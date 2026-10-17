package me.mrtahadarvish.astral;

import me.mrtahadarvish.astral.config.ConfigUpdater;
import me.mrtahadarvish.astral.config.Settings;
import me.mrtahadarvish.astral.sim.Sim;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A config.yml written by Astral 1.0 (watch only) is updated to blocking + compact alerts. */
class ConfigUpdaterTest {

    private static YamlConfiguration load(String resource) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                ConfigUpdaterTest.class.getResourceAsStream(resource), StandardCharsets.UTF_8));
    }

    @Test
    void oldConfigIsUpdated() {
        new Sim(); // Bukkit server for the YAML / registry classes
        YamlConfiguration config = load("/old/config-1.0.yml");
        config.set("checks.reach.tolerance", 0.25); // a value the owner changed
        config.setDefaults(load("/config.yml"));
        JavaPlugin plugin = Mockito.mock(JavaPlugin.class, Mockito.withSettings().stubOnly());
        Mockito.when(plugin.getConfig()).thenReturn(config);
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("ConfigUpdaterTest"));

        assertFalse(config.getBoolean("settings.mitigation"), "1.0 wrote watch mode");
        assertTrue(ConfigUpdater.update(plugin));

        Settings settings = new Settings();
        settings.load(config);
        assertTrue(settings.mitigation, "blocking is on after the update");
        assertTrue(config.getBoolean("checks.hotbarmacro.mitigate"));
        assertTrue(config.getBoolean("checks.aim.mitigate"));
        assertTrue(settings.alertCompact, "compact alerts");
        assertEquals(2, config.getInt("config-version"));
        assertEquals(0.25, config.getDouble("checks.reach.tolerance"), 1e-9, "owner values are kept");
        assertFalse(config.getBoolean("punishments.enabled"), "still no automatic punishments");
        assertFalse(ConfigUpdater.update(plugin), "only once");
        Report.add("CONFIG", "config.yml from Astral 1.0 updated to blocking + compact alerts, own values kept",
                "mitigation=" + settings.mitigation + " compact=" + settings.alertCompact, true);
    }
}
