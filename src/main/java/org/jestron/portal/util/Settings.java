package org.jestron.portal.util;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * config.yml 'settings' bolumunun onbellege alinmis hali.
 * Hareket olayinda her seferinde config okumamak icin (performans) kullanilir.
 */
public final class Settings {

    public final double teleportCooldown;
    public final double warningCooldown;
    public final int pushbackDistance;
    public final double pushbackStrength;
    public final double pushbackVertical;
    public final boolean adminBypass;
    public final boolean blockEntryOnCooldown;
    public final String teleportSound;
    public final String denySound;
    public final float soundVolume;
    public final float soundPitch;

    public Settings(FileConfiguration c) {
        this.teleportCooldown = Math.max(0D, c.getDouble("settings.teleport-cooldown", 3D));
        this.warningCooldown = Math.max(0D, c.getDouble("settings.warning-cooldown", 3D));
        this.pushbackDistance = clamp(c.getInt("settings.pushback-distance", 3), 0, 16);
        this.pushbackStrength = clamp(c.getDouble("settings.pushback-strength", 0.8D), 0D, 3D);
        this.pushbackVertical = clamp(c.getDouble("settings.pushback-vertical", 0.35D), 0D, 2D);
        this.adminBypass = c.getBoolean("settings.admin-bypass", true);
        this.blockEntryOnCooldown = c.getBoolean("settings.block-entry-on-cooldown", true);
        this.teleportSound = c.getString("settings.teleport-sound", "");
        this.denySound = c.getString("settings.deny-sound", "");
        this.soundVolume = (float) clamp(c.getDouble("settings.sound-volume", 1D), 0D, 10D);
        this.soundPitch = (float) clamp(c.getDouble("settings.sound-pitch", 1D), 0.5D, 2D);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double clamp(double v, double min, double max) {
        if (Double.isNaN(v)) return min;
        return Math.max(min, Math.min(max, v));
    }
}
