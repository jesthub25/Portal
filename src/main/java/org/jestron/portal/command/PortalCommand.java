package org.jestron.portal.command;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jestron.portal.PortalPlugin;
import org.jestron.portal.manager.PortalManager;
import org.jestron.portal.model.Portal;
import org.jestron.portal.util.Msg;
import org.jestron.portal.util.WorldEditHook;

import java.util.*;

/**
 * /portal <oluştur|tphere|düzenle|sil|bilgi|liste|yenile>
 * Tum alt komutlarda yetkiye gore tab tamamlama vardir; yetkisi olmayan alt komutlari goremez.
 */
public final class PortalCommand implements TabExecutor {

    private enum Sub {
        CREATE("portal.create", "help-create", "oluştur", "olustur", "create"),
        TPHERE("portal.tphere", "help-tphere", "tphere"),
        EDIT("portal.edit", "help-edit", "düzenle", "duzenle", "edit"),
        DELETE("portal.delete", "help-delete", "sil", "delete"),
        INFO("portal.info", "help-info", "bilgi", "info"),
        LIST("portal.info", "help-list", "liste", "list"),
        RELOAD("portal.reload", "help-reload", "yenile", "reload");

        final String permission;
        final String helpKey;
        final String[] names; // names[0] = tab tamamlamada gosterilen ana ad

        Sub(String permission, String helpKey, String... names) {
            this.permission = permission;
            this.helpKey = helpKey;
            this.names = names;
        }

        static Sub find(String input) {
            String in = input.toLowerCase(Locale.ROOT);
            for (Sub s : values()) {
                for (String n : s.names) {
                    if (n.equals(in)) return s;
                }
            }
            return null;
        }

        boolean matchesPrefix(String partial) {
            for (String n : names) {
                if (n.startsWith(partial)) return true;
            }
            return false;
        }
    }

    private final PortalPlugin plugin;

    public PortalCommand(PortalPlugin plugin) {
        this.plugin = plugin;
    }

    private Msg msg() {
        return plugin.getMsg();
    }

    private static boolean allowed(CommandSender sender, Sub sub) {
        return sender.hasPermission(sub.permission) || sender.hasPermission("portal.admin");
    }

    private boolean anyAllowed(CommandSender sender) {
        for (Sub s : Sub.values()) {
            if (allowed(sender, s)) return true;
        }
        return false;
    }

    // =================================================================== yurutme

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!anyAllowed(sender)) {
            msg().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        Sub sub = Sub.find(args[0]);
        if (sub == null) {
            msg().send(sender, "unknown-subcommand");
            return true;
        }
        if (!allowed(sender, sub)) {
            msg().send(sender, "no-permission");
            return true;
        }

        switch (sub) {
            case CREATE:
                create(sender, args);
                break;
            case TPHERE:
                tphere(sender, args);
                break;
            case EDIT:
                edit(sender, args);
                break;
            case DELETE:
                delete(sender, args);
                break;
            case INFO:
                info(sender, args);
                break;
            case LIST:
                list(sender);
                break;
            case RELOAD:
                plugin.reloadAll();
                msg().send(sender, "reloaded", "{count}", String.valueOf(plugin.getPortalManager().size()));
                break;
            default:
                sendHelp(sender);
        }
        return true;
    }

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            msg().send(sender, "only-players");
            return;
        }
        if (args.length < 2) {
            msg().send(sender, "usage-create");
            return;
        }
        Player player = (Player) sender;
        String name = args[1];

        if (!PortalManager.isValidName(name)) {
            msg().send(sender, "invalid-name", "{name}", name);
            return;
        }
        if (PortalManager.isReservedName(name)) {
            msg().send(sender, "reserved-name", "{name}", name);
            return;
        }
        PortalManager pm = plugin.getPortalManager();
        if (pm.exists(name)) {
            msg().send(sender, "portal-exists", "{name}", pm.get(name).getName());
            return;
        }

        WorldEditHook.Result sel = WorldEditHook.getSelection(player);
        switch (sel.status) {
            case MISSING:
                msg().send(sender, "worldedit-missing");
                return;
            case NO_SELECTION:
                msg().send(sender, "no-selection");
                return;
            case NOT_CUBOID:
                msg().send(sender, "selection-not-cuboid");
                return;
            case ERROR:
                msg().send(sender, "selection-error");
                return;
            default:
                break;
        }
        if (sel.world == null || plugin.getServer().getWorld(sel.world) == null) {
            msg().send(sender, "selection-world-missing");
            return;
        }

        Portal portal = new Portal(name, sel.world,
                sel.minX, sel.minY, sel.minZ, sel.maxX, sel.maxY, sel.maxZ);
        pm.add(portal);

        msg().send(sender, "portal-created",
                "{name}", name, "{world}", sel.world,
                "{x1}", String.valueOf(sel.minX), "{y1}", String.valueOf(sel.minY), "{z1}", String.valueOf(sel.minZ),
                "{x2}", String.valueOf(sel.maxX), "{y2}", String.valueOf(sel.maxY), "{z2}", String.valueOf(sel.maxZ));
    }

    private void tphere(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            msg().send(sender, "only-players");
            return;
        }
        if (args.length < 2) {
            msg().send(sender, "usage-tphere");
            return;
        }
        Portal portal = plugin.getPortalManager().get(args[1]);
        if (portal == null) {
            msg().send(sender, "portal-not-found", "{name}", args[1]);
            return;
        }
        Location loc = ((Player) sender).getLocation();
        if (loc.getWorld() == null) return;

        portal.setDestination(Portal.Destination.of(loc));
        plugin.getPortalManager().updated();

        msg().send(sender, "destination-set",
                "{name}", portal.getName(),
                "{world}", loc.getWorld().getName(),
                "{x}", fmt(loc.getX()), "{y}", fmt(loc.getY()), "{z}", fmt(loc.getZ()),
                "{yaw}", fmt1(loc.getYaw()), "{pitch}", fmt1(loc.getPitch()));
    }

    private void edit(CommandSender sender, String[] args) {
        // /portal düzenle <ad> <pos1|pos2> <x> <y> <z>
        if (args.length < 6) {
            msg().send(sender, "usage-edit");
            return;
        }
        Portal portal = plugin.getPortalManager().get(args[1]);
        if (portal == null) {
            msg().send(sender, "portal-not-found", "{name}", args[1]);
            return;
        }
        String pos = args[2].toLowerCase(Locale.ROOT);
        if (!pos.equals("pos1") && !pos.equals("pos2")) {
            msg().send(sender, "invalid-position");
            return;
        }

        Location base = (sender instanceof Player) ? ((Player) sender).getLocation() : null;
        Integer x = parseCoord(sender, args[3], base == null ? null : base.getBlockX());
        if (x == null) return;
        Integer y = parseCoord(sender, args[4], base == null ? null : base.getBlockY());
        if (y == null) return;
        Integer z = parseCoord(sender, args[5], base == null ? null : base.getBlockZ());
        if (z == null) return;

        if (pos.equals("pos1")) {
            portal.setPos1(x, y, z);
        } else {
            portal.setPos2(x, y, z);
        }
        plugin.getPortalManager().updated();

        msg().send(sender, "position-updated",
                "{name}", portal.getName(), "{pos}", pos.toUpperCase(Locale.ROOT),
                "{x}", String.valueOf(x), "{y}", String.valueOf(y), "{z}", String.valueOf(z));
    }

    /** Tam sayi veya ~ / ~n (oyuncunun konumuna gore) kabul eder. Hata durumunda mesaj gonderip null doner. */
    private Integer parseCoord(CommandSender sender, String input, Integer base) {
        try {
            if (input.startsWith("~")) {
                if (base == null) throw new NumberFormatException();
                String rest = input.substring(1);
                long v = (long) base + (rest.isEmpty() ? 0L : Long.parseLong(rest));
                if (v > Integer.MAX_VALUE || v < Integer.MIN_VALUE) throw new NumberFormatException();
                return (int) v;
            }
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            msg().send(sender, "invalid-coordinate", "{value}", input);
            return null;
        }
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg().send(sender, "usage-delete");
            return;
        }
        Portal portal = plugin.getPortalManager().get(args[1]);
        if (portal == null) {
            msg().send(sender, "portal-not-found", "{name}", args[1]);
            return;
        }
        plugin.getPortalManager().remove(portal.getName());
        msg().send(sender, "portal-deleted", "{name}", portal.getName());
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg().send(sender, "usage-info");
            return;
        }
        Portal p = plugin.getPortalManager().get(args[1]);
        if (p == null) {
            msg().send(sender, "portal-not-found", "{name}", args[1]);
            return;
        }

        String destination;
        Portal.Destination d = p.getDestination();
        if (d == null) {
            destination = msg().raw("info-no-destination", "&cAyarlanmamış");
        } else {
            destination = Msg.apply(msg().raw("info-destination", "{world} {x} {y} {z}"),
                    "{world}", d.getWorld(),
                    "{x}", fmt(d.getX()), "{y}", fmt(d.getY()), "{z}", fmt(d.getZ()),
                    "{yaw}", fmt1(d.getYaw()), "{pitch}", fmt1(d.getPitch()));
        }

        // Sadece komutu yazan kisiye gonderilir
        msg().sendLines(sender, "info",
                "{name}", p.getName(),
                "{world}", p.getWorld(),
                "{x1}", String.valueOf(p.getX1()), "{y1}", String.valueOf(p.getY1()), "{z1}", String.valueOf(p.getZ1()),
                "{x2}", String.valueOf(p.getX2()), "{y2}", String.valueOf(p.getY2()), "{z2}", String.valueOf(p.getZ2()),
                "{sizeX}", String.valueOf(p.getSizeX()), "{sizeY}", String.valueOf(p.getSizeY()),
                "{sizeZ}", String.valueOf(p.getSizeZ()),
                "{permission}", PortalManager.permissionOf(p.getName()),
                "{destination}", destination);
    }

    private void list(CommandSender sender) {
        Collection<Portal> all = plugin.getPortalManager().all();
        if (all.isEmpty()) {
            msg().send(sender, "list-empty");
            return;
        }
        String sep = msg().raw("list-separator", ", ");
        StringBuilder sb = new StringBuilder();
        for (Portal p : all) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(p.getName());
        }
        msg().send(sender, "list", "{count}", String.valueOf(all.size()), "{list}", sb.toString());
    }

    private void sendHelp(CommandSender sender) {
        msg().sendLines(sender, "help-header");
        for (Sub s : Sub.values()) {
            if (allowed(sender, s)) msg().sendRaw(sender, s.helpKey);
        }
        msg().sendLines(sender, "help-footer");
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    // =================================================================== tab tamamlama

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0 || !anyAllowed(sender)) return Collections.emptyList();

        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Sub s : Sub.values()) {
                if (allowed(sender, s) && s.matchesPrefix(partial)) out.add(s.names[0]);
            }
            return out;
        }

        Sub sub = Sub.find(args[0]);
        if (sub == null || !allowed(sender, sub)) return Collections.emptyList();

        if (args.length == 2) {
            switch (sub) {
                case TPHERE:
                case EDIT:
                case DELETE:
                case INFO:
                    return portalNames(args[1]);
                default:
                    return Collections.emptyList();
            }
        }

        if (sub != Sub.EDIT) return Collections.emptyList();

        if (args.length == 3) {
            String partial = args[2].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>(2);
            if ("pos1".startsWith(partial)) out.add("pos1");
            if ("pos2".startsWith(partial)) out.add("pos2");
            return out;
        }

        if (args.length >= 4 && args.length <= 6) {
            Portal p = plugin.getPortalManager().get(args[1]);
            if (p == null) return Collections.emptyList();
            boolean pos1 = !args[2].equalsIgnoreCase("pos2");
            int axis = args.length - 4; // 0=x 1=y 2=z
            int current;
            if (axis == 0) current = pos1 ? p.getX1() : p.getX2();
            else if (axis == 1) current = pos1 ? p.getY1() : p.getY2();
            else current = pos1 ? p.getZ1() : p.getZ2();

            // Portalin mevcut (aktif) koordinati her zaman ilk oneridir
            List<String> out = new ArrayList<>(2);
            out.add(String.valueOf(current));
            if (sender instanceof Player) out.add("~");
            return out;
        }
        return Collections.emptyList();
    }

    private List<String> portalNames(String partial) {
        String lower = partial.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Portal p : plugin.getPortalManager().all()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(lower)) out.add(p.getName());
        }
        return out;
    }
}
