package org.jestron.portal.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.jestron.portal.PortalPlugin;
import org.jestron.portal.model.Portal;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Portallari bellekte tutar ve plugins/Portal/portals.yml dosyasina kalici olarak kaydeder.
 */
public final class PortalManager {

    public static final String PERMISSION_PREFIX = "portal.";

    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

    /** Bu isimler eklentinin kendi yetkileriyle (portal.<isim>) cakisacagi icin kullanilamaz. */
    private static final Set<String> RESERVED = new HashSet<>(Arrays.asList(
            "admin", "command", "create", "tphere", "edit", "delete", "info", "reload",
            "use", "bypass", "list", "all"));

    private final PortalPlugin plugin;
    private final File file;

    /** kucuk harfli isim -> portal (ekleme sirasi korunur) */
    private final Map<String, Portal> portals = new LinkedHashMap<>();
    /** dunya adi -> o dunyadaki portallar (hizli konum aramasi icin) */
    private final Map<String, List<Portal>> byWorld = new HashMap<>();
    /** Bu eklentinin Bukkit'e kaydettigi portal.<isim> yetkileri */
    private final Set<String> registeredPermissions = new HashSet<>();

    public PortalManager(PortalPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "portals.yml");
    }

    // ------------------------------------------------------------------ dogrulama

    public static boolean isValidName(String name) {
        return name != null && VALID_NAME.matcher(name).matches();
    }

    public static boolean isReservedName(String name) {
        return name != null && RESERVED.contains(name.toLowerCase(Locale.ROOT));
    }

    public static String permissionOf(String portalName) {
        return PERMISSION_PREFIX + portalName.toLowerCase(Locale.ROOT);
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ yukleme / kaydetme

    public void load() {
        unregisterAllPermissions();
        portals.clear();
        byWorld.clear();

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().severe("Eklenti klasoru olusturulamadi: " + plugin.getDataFolder());
        }
        if (!file.exists()) {
            return; // ilk kurulum: kaydedilecek portal yok
        }

        YamlConfiguration yml = new YamlConfiguration();
        try {
            yml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            File backup = new File(plugin.getDataFolder(),
                    "portals.yml.broken-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()));
            try {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().severe("portals.yml okunamadi! Yedek alindi: " + backup.getName()
                        + " - Hata: " + e.getMessage());
            } catch (IOException copyError) {
                plugin.getLogger().severe("portals.yml okunamadi ve yedeklenemedi: " + copyError.getMessage());
            }
            return;
        }

        ConfigurationSection root = yml.getConfigurationSection("portals");
        if (root == null) return;

        for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name);
            if (s == null) continue;
            if (!isValidName(name) || isReservedName(name)) {
                plugin.getLogger().warning("Gecersiz portal adi atlandi: " + name);
                continue;
            }
            if (portals.containsKey(key(name))) {
                plugin.getLogger().warning("Ayni isimde ikinci portal atlandi: " + name);
                continue;
            }
            String world = s.getString("world");
            if (world == null || world.isEmpty()
                    || !s.isConfigurationSection("pos1") || !s.isConfigurationSection("pos2")) {
                plugin.getLogger().warning("Eksik veriye sahip portal atlandi: " + name);
                continue;
            }

            Portal p = new Portal(name, world,
                    s.getInt("pos1.x"), s.getInt("pos1.y"), s.getInt("pos1.z"),
                    s.getInt("pos2.x"), s.getInt("pos2.y"), s.getInt("pos2.z"));

            ConfigurationSection d = s.getConfigurationSection("destination");
            if (d != null && d.getString("world") != null) {
                p.setDestination(new Portal.Destination(
                        d.getString("world"),
                        d.getDouble("x"), d.getDouble("y"), d.getDouble("z"),
                        (float) d.getDouble("yaw"), (float) d.getDouble("pitch")));
            }

            portals.put(key(name), p);
            registerPermission(p.getName());
        }
        rebuildIndex();
    }

    /** Tum portallari diske yazar (once gecici dosyaya, sonra atomik olarak tasir). */
    public boolean save() {
        YamlConfiguration yml = new YamlConfiguration();

        // Bos olsa bile 'portals' bolumu dosyada kalsin
        ConfigurationSection root = yml.createSection("portals");
        for (Portal p : portals.values()) {
            ConfigurationSection s = root.createSection(p.getName());
            s.set("world", p.getWorld());
            s.set("pos1.x", p.getX1());
            s.set("pos1.y", p.getY1());
            s.set("pos1.z", p.getZ1());
            s.set("pos2.x", p.getX2());
            s.set("pos2.y", p.getY2());
            s.set("pos2.z", p.getZ2());
            Portal.Destination d = p.getDestination();
            if (d != null) {
                s.set("destination.world", d.getWorld());
                s.set("destination.x", d.getX());
                s.set("destination.y", d.getY());
                s.set("destination.z", d.getZ());
                s.set("destination.yaw", (double) d.getYaw());
                s.set("destination.pitch", (double) d.getPitch());
            }
        }

        File tmp = new File(plugin.getDataFolder(), "portals.yml.tmp");
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Klasor olusturulamadi: " + plugin.getDataFolder());
            }
            yml.save(tmp);
            try {
                Files.move(tmp.toPath(), file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "portals.yml kaydedilemedi!", e);
            return false;
        }
    }

    // ------------------------------------------------------------------ islemler

    public void add(Portal portal) {
        portals.put(key(portal.getName()), portal);
        registerPermission(portal.getName());
        rebuildIndex();
        save();
    }

    public boolean remove(String name) {
        Portal removed = portals.remove(key(name));
        if (removed == null) return false;
        unregisterPermission(removed.getName());
        rebuildIndex();
        save();
        return true;
    }

    /** pos1/pos2 gibi bolge degisikliklerinden sonra cagrilir. */
    public void updated() {
        rebuildIndex();
        save();
    }

    public Portal get(String name) {
        return name == null ? null : portals.get(key(name));
    }

    public boolean exists(String name) {
        return name != null && portals.containsKey(key(name));
    }

    public Collection<Portal> all() {
        return Collections.unmodifiableCollection(portals.values());
    }

    public int size() {
        return portals.size();
    }

    /** Konumun icinde bulundugu portal; yoksa null. */
    public Portal getAt(Location loc) {
        if (loc == null) return null;
        World w = loc.getWorld();
        if (w == null) return null;
        List<Portal> list = byWorld.get(w.getName());
        if (list == null) return null;
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        for (Portal p : list) {
            if (p.containsBlock(w.getName(), bx, by, bz)) return p;
        }
        return null;
    }

    public Portal getAtBlock(String worldName, int bx, int by, int bz) {
        List<Portal> list = byWorld.get(worldName);
        if (list == null) return null;
        for (Portal p : list) {
            if (p.containsBlock(worldName, bx, by, bz)) return p;
        }
        return null;
    }

    private void rebuildIndex() {
        byWorld.clear();
        for (Portal p : portals.values()) {
            List<Portal> list = byWorld.get(p.getWorld());
            if (list == null) {
                list = new ArrayList<>();
                byWorld.put(p.getWorld(), list);
            }
            list.add(p);
        }
    }

    // ------------------------------------------------------------------ yetkiler

    /**
     * portal.<isim> yetkisini Bukkit'e kaydeder (varsayilan: OP). Boylece LuckPerms gibi eklentiler
     * yetkiyi otomatik tamamlar. Kayitli olmasa da kontrol calisir; bu sadece kolaylik icindir.
     */
    private void registerPermission(String portalName) {
        String node = permissionOf(portalName);
        PluginManager pm = Bukkit.getPluginManager();
        try {
            if (pm.getPermission(node) == null) {
                pm.addPermission(new Permission(node, "'" + portalName + "' portalini kullanma yetkisi",
                        PermissionDefault.OP));
                registeredPermissions.add(node);
            }
        } catch (Throwable t) {
            // Baska bir eklenti ayni yetkiyi kaydetmis olabilir; sorun degil.
        }
    }

    private void unregisterPermission(String portalName) {
        String node = permissionOf(portalName);
        if (registeredPermissions.remove(node)) {
            try {
                Bukkit.getPluginManager().removePermission(node);
            } catch (Throwable ignored) {
            }
        }
    }

    public void unregisterAllPermissions() {
        for (String node : new ArrayList<>(registeredPermissions)) {
            try {
                Bukkit.getPluginManager().removePermission(node);
            } catch (Throwable ignored) {
            }
        }
        registeredPermissions.clear();
    }
}
