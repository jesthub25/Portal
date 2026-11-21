package org.jestron.portal.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Bir portalin bolgesi (pos1/pos2, blok koordinatlari, her iki uc dahil) ve isinlanma hedefi.
 */
public final class Portal {

    private final String name;
    private String world;

    private int x1, y1, z1;
    private int x2, y2, z2;

    // Onbellege alinmis sinirlar (pos1/pos2 her degistiginde yeniden hesaplanir)
    private int minX, minY, minZ, maxX, maxY, maxZ;

    private Destination destination;

    public Portal(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.name = name;
        this.world = world;
        this.x1 = x1; this.y1 = y1; this.z1 = z1;
        this.x2 = x2; this.y2 = y2; this.z2 = z2;
        recalc();
    }

    private void recalc() {
        minX = Math.min(x1, x2); maxX = Math.max(x1, x2);
        minY = Math.min(y1, y2); maxY = Math.max(y1, y2);
        minZ = Math.min(z1, z2); maxZ = Math.max(z1, z2);
    }

    /** Konumun (ayak blogu) bu portalin icinde olup olmadigini dondurur. */
    public boolean contains(Location loc) {
        if (loc == null) return false;
        World w = loc.getWorld();
        if (w == null || !w.getName().equals(world)) return false;
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        return bx >= minX && bx <= maxX && by >= minY && by <= maxY && bz >= minZ && bz <= maxZ;
    }

    public boolean containsBlock(String worldName, int bx, int by, int bz) {
        return world.equals(worldName)
                && bx >= minX && bx <= maxX && by >= minY && by <= maxY && bz >= minZ && bz <= maxZ;
    }

    /** Portal bolgesinin merkezi (dunya yuklu degilse null). */
    public Location getCenter() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, (minX + maxX + 1) / 2.0, (minY + maxY + 1) / 2.0, (minZ + maxZ + 1) / 2.0);
    }

    public void setPos1(int x, int y, int z) {
        this.x1 = x; this.y1 = y; this.z1 = z;
        recalc();
    }

    public void setPos2(int x, int y, int z) {
        this.x2 = x; this.y2 = y; this.z2 = z;
        recalc();
    }

    public String getName() { return name; }
    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public int getX1() { return x1; }
    public int getY1() { return y1; }
    public int getZ1() { return z1; }
    public int getX2() { return x2; }
    public int getY2() { return y2; }
    public int getZ2() { return z2; }

    public int getSizeX() { return maxX - minX + 1; }
    public int getSizeY() { return maxY - minY + 1; }
    public int getSizeZ() { return maxZ - minZ + 1; }

    public Destination getDestination() { return destination; }
    public void setDestination(Destination destination) { this.destination = destination; }
    public boolean hasDestination() { return destination != null; }

    /** Isinlanma hedefi: dunya adi + tam konum + bakis yonu. */
    public static final class Destination {
        private final String world;
        private final double x, y, z;
        private final float yaw, pitch;

        public Destination(String world, double x, double y, double z, float yaw, float pitch) {
            this.world = world;
            this.x = x; this.y = y; this.z = z;
            this.yaw = yaw; this.pitch = pitch;
        }

        public static Destination of(Location loc) {
            return new Destination(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                    loc.getYaw(), loc.getPitch());
        }

        /** Dunya yuklu degilse null doner. */
        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            if (w == null) return null;
            return new Location(w, x, y, z, yaw, pitch);
        }

        public String getWorld() { return world; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public float getYaw() { return yaw; }
        public float getPitch() { return pitch; }
    }
}
