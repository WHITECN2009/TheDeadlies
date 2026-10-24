package org.whitecn.net.theDeadlies;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {
    private final Plugin plugin;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String getConfigValue(ConfigKeys key){
        return plugin.getConfig().getString(key.getPath());
    }
}
