package org.whitecn.net.theDeadlies;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import static org.whitecn.net.theDeadlies.Vars.PREFIX;

public final class TheDeadlies extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        this.getLogger().info("插件已启用");
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("thedeadlies").setExecutor(new TheDeadliesCommand(this));
        getCommand("thedeadlies").setTabCompleter(new TheDeadliesCommand(this));
    }

    @Override
    public void onDisable() {
        this.getLogger().info("插件已禁用");
    }
}
