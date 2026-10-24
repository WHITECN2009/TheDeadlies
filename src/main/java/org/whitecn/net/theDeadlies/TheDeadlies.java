package org.whitecn.net.theDeadlies;

import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public final class TheDeadlies extends JavaPlugin {

    @Override
    public void onEnable() {
        int pluginId = 34456;
        Metrics metrics = new Metrics(this, pluginId);

        saveDefaultConfig();
        I18n.load(this);
        this.getLogger().info(I18n.get(Messages.THEDEADLIES_ENABLED));
        TheDeadliesCommand command = new TheDeadliesCommand(this);
        getCommand("thedeadlies").setExecutor(command);
        getCommand("thedeadlies").setTabCompleter(command);
    }

    @Override
    public void onDisable() {
        this.getLogger().info(I18n.get(Messages.THEDEADLIES_DISABLED));
    }
}
