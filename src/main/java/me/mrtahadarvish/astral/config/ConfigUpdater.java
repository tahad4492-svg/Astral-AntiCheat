package me.mrtahadarvish.astral.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Brings config.yml files written by older Astral versions up to date. New options are added
 * with their default value, existing values are kept unless a version note below says why
 * a value changes.
 */
public final class ConfigUpdater {

    public static final int CURRENT = 2;

    private ConfigUpdater() {
    }

    /** Returns true when the file was changed (and saved). */
    public static boolean update(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        int version = config.getInt("config-version", 1);
        if (version >= CURRENT) {
            return false;
        }
        if (version < 2) {
            // 1.0 only watched. Since 1.1 Astral blocks cheats by default (no automatic kicks / bans),
            // including silent swaps and aim cheats, and sends compact alerts.
            config.set("settings.mitigation", true);
            config.set("checks.aim.mitigate", true);
            config.set("checks.hotbarmacro.mitigate", true);
            plugin.getLogger().info("Updated config.yml to version 2: blocking is now ON (settings.mitigation: true),"
                    + " alerts are compact. Set settings.mitigation: false to only watch.");
        }
        config.set("config-version", CURRENT);
        config.options().copyDefaults(true);
        plugin.saveConfig();
        return true;
    }
}
