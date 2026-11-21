package org.jestron.portal.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.Vector;
import org.jestron.portal.PortalPlugin;
import org.jestron.portal.manager.CooldownManager;
import org.jestron.portal.manager.PortalManager;
import org.jestron.portal.model.Portal;
import org.jestron.portal.util.Settings;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Portal girislerini algilar:
 *  - Yetkili oyuncu  : bekleme suresi doluysa hedefe isinlanir.
 *  - Yetkisiz oyuncu : uyari alir (uyari bekleme suresi ile), portaldan geriye atilir ve savrulur.
 *
 * Isinlanma/geri atma bir sonraki tick'te yapilir; PlayerMoveEvent icinde dogrudan teleport
 * bazi surumlerde "moved wrongly" / geri sekme sorunlarina yol actigi icin bu sekilde yapilir.
 */
public final class PortalListener implements Listener {

    private static final double MAX_VELOCITY = 3.9D; // Spigot'un "excessive velocity" siniri 4

    private final PortalPlugin plugin;
    private final Set<UUID> pendingTeleport = new HashSet<>();
    private final Set<UUID> pendingPush = new HashSet<>();

    public PortalListener(PortalPlugin plugin) {
        this.plugin = plugin;
    }

    // =================================================================== oyuncu hareketi

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        Location from = event.getFrom();

        // En ucuz kontrol once: ayni blok icindeki hareketler (bakis, kucuk adimlar) atlanir
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()
                && from.getWorld() == to.getWorld()) {
            return;
        }

        PortalManager pm = plugin.getPortalManager();
        if (pm.size() == 0) return;

        Portal portal = pm.getAt(to);
        if (portal == null) return;

        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        boolean alreadyInside = portal.contains(from);

        if (pendingTeleport.contains(id)) return;
        if (pendingPush.contains(id)) {
            if (!alreadyInside) event.setCancelled(true);
            return;
        }

        if (!canUse(player, portal)) {
            deny(player, portal, from, to, alreadyInside, event);
            return;
        }

        tryTeleport(player, portal, alreadyInside, event);
    }

    // =================================================================== araclar (tekne, maden arabasi...)

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleMove(VehicleMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        PortalManager pm = plugin.getPortalManager();
        if (pm.size() == 0) return;

        Portal portal = pm.getAt(to);
        if (portal == null) return;

        Vehicle vehicle = event.getVehicle();
        boolean alreadyInside = portal.contains(from);
        for (Entity passenger : vehicle.getPassengers()) {
            if (!(passenger instanceof Player)) continue;
            Player player = (Player) passenger;
            UUID id = player.getUniqueId();
            if (pendingTeleport.contains(id) || pendingPush.contains(id)) continue;

            if (!canUse(player, portal)) {
                warn(player, "portal-no-permission", "{name}", portal.getName());
                playSound(player, player.getLocation(), plugin.getSettings().denySound);
                Vector back = from.toVector().subtract(to.toVector());
                back.setY(0);
                if (back.lengthSquared() > 1.0E-4) {
                    vehicle.setVelocity(clampVelocity(back.normalize()
                            .multiply(Math.max(0.4D, plugin.getSettings().pushbackStrength))));
                }
                continue;
            }
            tryTeleport(player, portal, alreadyInside, null);
        }
    }

    // =================================================================== temizlik

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        plugin.getCooldownManager().clear(id);
        pendingTeleport.remove(id);
        pendingPush.remove(id);
    }

    public void clear() {
        pendingTeleport.clear();
        pendingPush.clear();
    }

    // =================================================================== mantik

    private boolean canUse(Player player, Portal portal) {
        if (player.hasPermission(PortalManager.permissionOf(portal.getName()))) return true;
        return plugin.getSettings().adminBypass && player.hasPermission("portal.admin");
    }

    private void tryTeleport(Player player, Portal portal, boolean alreadyInside, PlayerMoveEvent event) {
        Settings s = plugin.getSettings();
        CooldownManager cd = plugin.getCooldownManager();
        UUID id = player.getUniqueId();

        if (!cd.isTeleportReady(id)) {
            if (event != null && s.blockEntryOnCooldown && !alreadyInside) {
                event.setCancelled(true);
            }
            warn(player, "portal-cooldown",
                    "{name}", portal.getName(),
                    "{seconds}", String.valueOf(cd.teleportRemainingSeconds(id)));
            return;
        }

        Portal.Destination dest = portal.getDestination();
        if (dest == null) {
            warn(player, "portal-no-destination", "{name}", portal.getName());
            return;
        }
        if (dest.toLocation() == null) {
            warn(player, "portal-destination-world-missing",
                    "{name}", portal.getName(), "{world}", dest.getWorld());
            return;
        }

        cd.startTeleport(id, s.teleportCooldown);
        pendingTeleport.add(id);
        final String portalName = portal.getName();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            pendingTeleport.remove(id);
            if (!player.isOnline()) return;

            Location target = dest.toLocation();
            if (target == null) {
                warn(player, "portal-destination-world-missing",
                        "{name}", portalName, "{world}", dest.getWorld());
                return;
            }
            if (player.isInsideVehicle()) {
                player.leaveVehicle();
            }
            player.setFallDistance(0F);
            boolean ok = player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
            if (ok) {
                plugin.getMsg().send(player, "teleported", "{name}", portalName);
                playSound(player, target, plugin.getSettings().teleportSound);
            }
        });
    }

    private void deny(Player player, Portal portal, Location from, Location to,
                      boolean alreadyInside, PlayerMoveEvent event) {
        UUID id = player.getUniqueId();

        // Portalin disindan giriyorsa hareketi iptal et (oyuncu portala adim atamaz)
        if (!alreadyInside) event.setCancelled(true);

        warn(player, "portal-no-permission", "{name}", portal.getName());

        pendingPush.add(id);
        final Location fromCopy = from.clone();
        final Vector moveDir = to.toVector().subtract(from.toVector());

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            pendingPush.remove(id);
            if (!player.isOnline()) return;
            pushBack(player, portal, fromCopy, moveDir, alreadyInside);
        });
    }

    /** Oyuncuyu portaldan guvenli bir konuma geri atar ve hafifce savurur. */
    private void pushBack(Player player, Portal portal, Location from, Vector moveDir, boolean wasInside) {
        Settings s = plugin.getSettings();
        Location current = player.getLocation();
        if (current.getWorld() == null || from.getWorld() == null
                || !current.getWorld().getName().equals(from.getWorld().getName())) {
            return; // oyuncu baska dunyaya gecmis
        }

        Vector dir = pushDirection(portal, from, moveDir, current, wasInside);

        Location target = null;
        for (int d = s.pushbackDistance; d >= 1; d--) {
            Location candidate = findSafe(from, dir, d);
            if (candidate != null) {
                target = candidate;
                break;
            }
        }

        if (target != null) {
            target.setYaw(current.getYaw());
            target.setPitch(current.getPitch());
            player.setFallDistance(0F);
            player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }

        playSound(player, player.getLocation(), s.denySound);

        if (s.pushbackStrength > 0D || s.pushbackVertical > 0D) {
            final Vector velocity = clampVelocity(dir.clone().multiply(s.pushbackStrength).setY(s.pushbackVertical));
            // Teleport sonrasi hizin sifirlanmamasi icin bir tick sonra uygula
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !player.isDead()) {
                    player.setVelocity(velocity);
                }
            }, 1L);
        }
    }

    /** Geri atma yonu (yatay, birim vektor). */
    private Vector pushDirection(Portal portal, Location from, Vector moveDir, Location current, boolean wasInside) {
        Vector dir = null;

        // 1) Disaridan giriyorsa: geldigi yonun tersi
        if (!wasInside && moveDir != null) {
            Vector v = moveDir.clone().multiply(-1);
            v.setY(0);
            if (v.lengthSquared() > 1.0E-4) dir = v;
        }
        // 2) Zaten icerideyse: portal merkezinden disari dogru
        if (dir == null) {
            Location center = portal.getCenter();
            if (center != null) {
                Vector v = from.toVector().subtract(center.toVector());
                v.setY(0);
                if (v.lengthSquared() > 1.0E-4) dir = v;
            }
        }
        // 3) Son care: bakis yonunun tersi
        if (dir == null) {
            Vector v = current.getDirection().multiply(-1);
            v.setY(0);
            dir = v.lengthSquared() > 1.0E-4 ? v : new Vector(1, 0, 0);
        }
        return dir.normalize();
    }

    /** from + dir*distance civarinda (y, y+1, y-1) guvenli bir durma noktasi arar. */
    private Location findSafe(Location from, Vector dir, int distance) {
        World world = from.getWorld();
        if (world == null) return null;
        double x = from.getX() + dir.getX() * distance;
        double z = from.getZ() + dir.getZ() * distance;
        int[] yOffsets = {0, 1, -1};
        for (int dy : yOffsets) {
            double y = from.getY() + dy;
            int bx = floor(x), by = floor(y), bz = floor(z);
            if (by < minHeight(world) || by + 1 >= world.getMaxHeight()) continue;
            if (!world.isChunkLoaded(bx >> 4, bz >> 4)) continue;
            if (plugin.getPortalManager().getAtBlock(world.getName(), bx, by, bz) != null) continue;
            if (plugin.getPortalManager().getAtBlock(world.getName(), bx, by + 1, bz) != null) continue;

            Block feet = world.getBlockAt(bx, by, bz);
            Block head = world.getBlockAt(bx, by + 1, bz);
            if (!isFree(feet) || !isFree(head)) continue;
            if (isHazard(world.getBlockAt(bx, by - 1, bz).getType())) continue;

            double safeY = (dy == 0) ? from.getY() : by;
            return new Location(world, x, safeY, z);
        }
        return null;
    }

    private static boolean isFree(Block b) {
        return b.isPassable() && !isHazard(b.getType()) && !b.isLiquid();
    }

    private static boolean isHazard(Material m) {
        String n = m.name();
        return n.contains("LAVA") || n.contains("FIRE") || n.equals("CACTUS") || n.equals("MAGMA_BLOCK")
                || n.equals("SWEET_BERRY_BUSH") || n.equals("WITHER_ROSE") || n.equals("POWDER_SNOW")
                || n.equals("CAMPFIRE") || n.equals("SOUL_CAMPFIRE");
    }

    /** World#getMinHeight() 1.17+ icin vardir; 1.16.5'te 0 kullanilir (yansima ile, derleme 1.16.5 uyumlu). */
    private static final java.lang.reflect.Method GET_MIN_HEIGHT = lookupMinHeight();

    private static java.lang.reflect.Method lookupMinHeight() {
        try {
            return World.class.getMethod("getMinHeight");
        } catch (Throwable t) {
            return null;
        }
    }

    private static int minHeight(World w) {
        if (GET_MIN_HEIGHT == null) return 0;
        try {
            return ((Number) GET_MIN_HEIGHT.invoke(w)).intValue();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static Vector clampVelocity(Vector v) {
        double len = v.length();
        if (len > MAX_VELOCITY) v.multiply(MAX_VELOCITY / len);
        return v;
    }

    private void warn(Player player, String key, String... placeholders) {
        if (plugin.getCooldownManager().tryWarn(player.getUniqueId(), plugin.getSettings().warningCooldown)) {
            plugin.getMsg().send(player, key, placeholders);
        }
    }

    private void playSound(Player player, Location at, String sound) {
        if (sound == null || sound.isEmpty() || at == null) return;
        Settings s = plugin.getSettings();
        try {
            player.playSound(at, sound.toLowerCase(Locale.ROOT), s.soundVolume, s.soundPitch);
        } catch (Throwable ignored) {
            // gecersiz ses anahtari sunucuyu etkilemez
        }
    }
}
