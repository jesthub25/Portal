package org.jestron.portal;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jestron.portal.command.PortalCommand;
import org.jestron.portal.listener.PortalListener;
import org.jestron.portal.manager.CooldownManager;
import org.jestron.portal.manager.PortalManager;
import org.jestron.portal.util.Msg;
import org.jestron.portal.util.Settings;
import org.jestron.portal.util.WorldEditHook;

public final class PortalPlugin extends JavaPlugin {

    private PortalManager portalManager;
    private CooldownManager cooldownManager;
    private PortalListener listener;
    private Settings settings;
    private Msg msg;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        this.settings = new Settings(getConfig());
        this.msg = new Msg(getConfig());

        this.cooldownManager = new CooldownManager();
        this.portalManager = new PortalManager(this);
        this.portalManager.load();

        PluginCommand cmd = getCommand("portal");
        if (cmd == null) {
            getLogger().severe("plugin.yml icinde 'portal' komutu bulunamadi! Eklenti devre disi birakiliyor.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        PortalCommand executor = new PortalCommand(this);
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);

        this.listener = new PortalListener(this);
        getServer().getPluginManager().registerEvents(listener, this);

        if (!WorldEditHook.isAvailable()) {
            getLogger().warning("WorldEdit/FastAsyncWorldEdit bulunamadi. /portal oluştur calismaz; "
                    + "diger tum ozellikler calisir.");
        }
        getLogger().info(portalManager.size() + " portal yuklendi. Portal v" + getDescription().getVersion() + " aktif.");
    }

    @Override
    public void onDisable() {
        // Her degisiklik aninda kaydedildigi icin burada tekrar yazilmaz
        // (sunucu acikken elle yapilan duzenlemelerin ezilmesini onler).
        if (portalManager != null) {
            portalManager.unregisterAllPermissions();
        }
        if (cooldownManager != null) cooldownManager.clearAll();
        if (listener != null) listener.clear();
    }

    /** config.yml ve portals.yml dosyalarini yeniden yukler. */
    public void reloadAll() {
        reloadConfig();
        this.settings = new Settings(getConfig());
        this.msg = new Msg(getConfig());
        portalManager.load();
    }

    public PortalManager getPortalManager() {
        return portalManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public Settings getSettings() {
        return settings;
    }

    public Msg getMsg() {
        return msg;
    }
}
