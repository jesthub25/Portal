package org.jestron.portal.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * WorldEdit / FastAsyncWorldEdit secimini yansima (reflection) ile okur.
 * Derleme bagimliligi yoktur; WorldEdit 7.x'in tum surumleri ve FAWE ile calisir,
 * WorldEdit yuklu degilse hicbir sinif yuklenmez ve hata olusmaz.
 */
public final class WorldEditHook {

    public enum Status { OK, MISSING, NO_SELECTION, NOT_CUBOID, ERROR }

    /** Secim sonucu. Status OK ise koordinatlar ve dunya adi doludur. */
    public static final class Result {
        public final Status status;
        public final String world;
        public final int minX, minY, minZ, maxX, maxY, maxZ;

        private Result(Status status, String world, int[] c) {
            this.status = status;
            this.world = world;
            this.minX = c[0]; this.minY = c[1]; this.minZ = c[2];
            this.maxX = c[3]; this.maxY = c[4]; this.maxZ = c[5];
        }

        static Result of(Status s) {
            return new Result(s, null, new int[6]);
        }
    }

    private WorldEditHook() {
    }

    public static Plugin findPlugin() {
        Plugin p = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (p == null || !p.isEnabled()) {
            p = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
        }
        return (p != null && p.isEnabled()) ? p : null;
    }

    public static boolean isAvailable() {
        return findPlugin() != null;
    }

    public static Result getSelection(Player player) {
        Plugin we = findPlugin();
        if (we == null) return Result.of(Status.MISSING);

        try {
            // LocalSession session = worldEditPlugin.getSession(player)
            Object session = invoke(we, "getSession", new Class<?>[]{Player.class}, player);
            if (session == null) return Result.of(Status.NO_SELECTION);

            // World selWorld = session.getSelectionWorld()
            Object selWorld = invoke(session, "getSelectionWorld", new Class<?>[0]);
            if (selWorld == null) return Result.of(Status.NO_SELECTION);

            // Region region = session.getSelection(selWorld)  (eksikse IncompleteRegionException)
            Object region;
            try {
                Method m = findOneArgMethod(session.getClass(), "getSelection", selWorld.getClass());
                if (m == null) return Result.of(Status.ERROR);
                region = m.invoke(session, selWorld);
            } catch (InvocationTargetException ite) {
                Throwable cause = ite.getCause();
                if (cause != null && cause.getClass().getSimpleName().equals("IncompleteRegionException")) {
                    return Result.of(Status.NO_SELECTION);
                }
                throw ite;
            }
            if (region == null) return Result.of(Status.NO_SELECTION);

            if (!isCuboid(region, we.getClass().getClassLoader())) {
                return Result.of(Status.NOT_CUBOID);
            }

            Object min = invoke(region, "getMinimumPoint", new Class<?>[0]);
            Object max = invoke(region, "getMaximumPoint", new Class<?>[0]);
            String worldName = String.valueOf(invoke(selWorld, "getName", new Class<?>[0]));

            int[] c = new int[]{
                    coord(min, "X"), coord(min, "Y"), coord(min, "Z"),
                    coord(max, "X"), coord(max, "Y"), coord(max, "Z")
            };
            return new Result(Status.OK, worldName, c);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[Portal] WorldEdit secimi okunamadi: " + t);
            return Result.of(Status.ERROR);
        }
    }

    private static boolean isCuboid(Object region, ClassLoader loader) {
        try {
            Class<?> cuboid = Class.forName("com.sk89q.worldedit.regions.CuboidRegion", false, loader);
            return cuboid.isInstance(region);
        } catch (Throwable t) {
            return region.getClass().getSimpleName().contains("Cuboid");
        }
    }

    /** BlockVector3 koordinati: getBlockX() (7.0-7.3) / x() (7.3+) / getX() — hangisi varsa. */
    private static int coord(Object vec, String axis) throws Exception {
        String[] names = {"getBlock" + axis, axis.toLowerCase(), "get" + axis};
        for (String n : names) {
            Method m = findMethod(vec.getClass(), n, new Class<?>[0]);
            if (m != null) {
                Object v = m.invoke(vec);
                if (v instanceof Number) return (int) Math.floor(((Number) v).doubleValue());
            }
        }
        throw new NoSuchMethodException("Koordinat metodu bulunamadi: " + vec.getClass().getName() + "#" + axis);
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = findMethod(target.getClass(), name, types);
        if (m == null) throw new NoSuchMethodException(target.getClass().getName() + "#" + name);
        return m.invoke(target, args);
    }

    /** Public metodu arar; public olmayan sinif icinde ise erisilebilir yapar. */
    private static Method findMethod(Class<?> type, String name, Class<?>[] types) {
        try {
            Method m = type.getMethod(name, types);
            if (!m.isAccessible()) {
                try {
                    m.setAccessible(true);
                } catch (Throwable ignored) {
                }
            }
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Method findOneArgMethod(Class<?> type, String name, Class<?> argType) {
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterTypes().length == 1
                    && m.getParameterTypes()[0].isAssignableFrom(argType)) {
                try {
                    m.setAccessible(true);
                } catch (Throwable ignored) {
                }
                return m;
            }
        }
        return null;
    }
}
