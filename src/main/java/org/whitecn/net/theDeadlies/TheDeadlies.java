package org.whitecn.net.theDeadlies;

import org.bstats.bukkit.Metrics;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import static org.whitecn.net.theDeadlies.Vars.PREFIX;

public final class TheDeadlies extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        int pluginId = 34456;
        Metrics metrics = new Metrics(this, pluginId);

        saveDefaultConfig();
        I18n.load(this);
        this.getLogger().info(I18n.tr("插件已启用"));
        getServer().getPluginManager().registerEvents(this, this);
        TheDeadliesCommand command = new TheDeadliesCommand(this);
        getCommand("thedeadlies").setExecutor(command);
        getCommand("thedeadlies").setTabCompleter(command);
    }

    @Override
    public void onDisable() {
        this.getLogger().info(I18n.tr("插件已禁用"));
    }
}
