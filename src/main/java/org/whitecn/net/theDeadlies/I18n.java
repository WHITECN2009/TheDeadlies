package org.whitecn.net.theDeadlies;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public final class I18n {
    private static ConfigurationSection messages;

    private I18n() {
    }

    public static void load(JavaPlugin plugin) {
        String configured = plugin.getConfig().getString("language", "ZH_CN");
        if (configured == null) {
            configured = "ZH_CN";
        }
        String language = configured.trim().toUpperCase(Locale.ROOT);
        if (!language.equals("EN") && !language.equals("ZH_CN")) {
            plugin.getLogger().warning(
                    "Unsupported language '" + configured + "'; using ZH_CN."
            );
            language = "ZH_CN";
        }

        plugin.saveResource("language.yml", false);
        File languageFile = new File(plugin.getDataFolder(), "language.yml");
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(languageFile);
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Failed to load language.yml", exception);
        }

        messages = configuration.getConfigurationSection(language);
        if (messages == null) {
            throw new IllegalStateException(
                    "Missing language section '" + language + "' in language.yml"
            );
        }
    }

    public static String get(String key) {
        if (messages == null) {
            throw new IllegalStateException("Language messages have not been loaded");
        }
        String message = messages.getString(key);
        if (message == null) {
            throw new IllegalArgumentException(
                    "Missing language key '" + key + "' in language.yml"
            );
        }
        return message;
    }
}
