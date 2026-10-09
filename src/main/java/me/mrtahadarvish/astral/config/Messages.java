package me.mrtahadarvish.astral.config;

import me.mrtahadarvish.astral.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** messages.yml */
public final class Messages {

    private final JavaPlugin plugin;
    private YamlConfiguration config;
    private YamlConfiguration defaults;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        InputStream stream = plugin.getResource("messages.yml");
        if (stream != null) {
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
        }
    }

    public String prefix() {
        return config.getString("prefix", "&8[&bAstral&8] &r");
    }

    /** Raw message with %prefix% and the given placeholders replaced (key, value, key, value ...). */
    public String raw(String key, String... placeholders) {
        String message = config.getString(key);
        if (message == null) {
            message = key;
        }
        return apply(message, placeholders);
    }

    public List<String> rawList(String key, String... placeholders) {
        List<String> lines = config.getStringList(key);
        if (lines.isEmpty() && config.isString(key)) {
            lines = List.of(config.getString(key, ""));
        }
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(apply(line, placeholders));
        }
        return out;
    }

    public Component get(String key, String... placeholders) {
        return Text.color(raw(key, placeholders));
    }

    public Component lines(String key, String... placeholders) {
        return Text.lines(rawList(key, placeholders));
    }

    public void send(CommandSender sender, String key, String... placeholders) {
        String message = raw(key, placeholders);
        if (!message.isEmpty()) {
            sender.sendMessage(Text.color(message));
        }
    }

    private String apply(String message, String... placeholders) {
        String out = message.replace("%prefix%", prefix());
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            out = out.replace("%" + placeholders[i] + "%", placeholders[i + 1] == null ? "" : placeholders[i + 1]);
        }
        return out;
    }
}
