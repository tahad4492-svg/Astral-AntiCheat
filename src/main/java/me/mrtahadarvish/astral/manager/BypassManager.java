package me.mrtahadarvish.astral.manager;

import me.mrtahadarvish.astral.AstralAntiCheat;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Players added with /astral bypass. They are treated exactly like players with the
 * astral.bypass permission. Saved in bypass.yml so it survives restarts.
 */
public final class BypassManager {

    private final AstralAntiCheat plugin;
    private final File file;
    private final Set<UUID> bypassing = ConcurrentHashMap.newKeySet();

    public BypassManager(AstralAntiCheat plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bypass.yml");
    }

    public void load() {
        bypassing.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String raw : config.getStringList("players")) {
            try {
                bypassing.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                // invalid entry
            }
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        List<String> list = new ArrayList<>();
        for (UUID uuid : bypassing) {
            list.add(uuid.toString());
        }
        config.set("players", list);
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save bypass.yml", e);
        }
    }

    public boolean contains(UUID uuid) {
        return bypassing.contains(uuid);
    }

    /** Toggles the bypass. Returns the new state. */
    public boolean toggle(UUID uuid) {
        boolean now;
        if (bypassing.remove(uuid)) {
            now = false;
        } else {
            bypassing.add(uuid);
            now = true;
        }
        save();
        return now;
    }

    public Set<UUID> all() {
        return bypassing;
    }
}
