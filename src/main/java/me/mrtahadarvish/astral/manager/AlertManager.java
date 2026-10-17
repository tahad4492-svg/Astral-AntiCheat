package me.mrtahadarvish.astral.manager;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.config.Settings;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.ClientUtil;
import me.mrtahadarvish.astral.util.MathUtil;
import me.mrtahadarvish.astral.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sends flag alerts to staff (chat with hover + click) and to the console.
 * <p>
 * Compact mode (default): flags are grouped per player into one short line that says what the
 * player is most likely using ("Steve may be using KillAura (silent rotations) x6, Silent swap x2"),
 * with every detail in the hover. See {@link AlertDigest} for when lines are sent.
 * Staff with /astral verbose (or /astral debug) still see every single flag.
 */
public final class AlertManager {

    private final AstralAntiCheat plugin;
    private final CheatNames names = new CheatNames();

    public AlertManager(AstralAntiCheat plugin) {
        this.plugin = plugin;
    }

    /** (Re)loads the cheat names from messages.yml. */
    public void load() {
        names.load(plugin.messages().section("cheat-names"));
    }

    public CheatNames names() {
        return names;
    }

    public void alert(Check check, PlayerData data, String type, double vl, String debug) {
        Player player = data.player();
        String[] placeholders = flagPlaceholders(check, data, type, vl, debug);
        sendVerbose(check, placeholders, player);

        if (vl < check.alertVl()) {
            return;
        }
        Settings settings = plugin.settings();
        if (!settings.alertCompact) {
            legacyAlert(check, data, placeholders, player, debug);
            return;
        }
        long now = System.currentTimeMillis();
        String name = names.name(check.id(), check.name(), type);
        data.alertDigest.add(name, check.name() + " " + type, vl, debug, now, settings.alertQuietMs);
        flush(data, now);
    }

    /** Sends the alert lines that became due (runs every 10 ticks). */
    public void tick() {
        long now = System.currentTimeMillis();
        for (PlayerData data : plugin.data().all()) {
            if (!data.alertDigest.isEmpty()) {
                flush(data, now);
            }
        }
    }

    private void flush(PlayerData data, long now) {
        Settings settings = plugin.settings();
        AlertDigest digest = data.alertDigest;
        if (!digest.due(now, settings.alertGatherMs, settings.alertIntervalMs, settings.alertRepeatMs)) {
            return;
        }
        List<AlertDigest.Entry> entries = digest.drain(now);
        if (entries.isEmpty()) {
            return;
        }
        Player player = data.player();

        StringBuilder cheats = new StringBuilder();
        StringBuilder plainCheats = new StringBuilder();
        String separator = plugin.messages().raw("compact-alert-separator");
        int flags = 0;
        for (AlertDigest.Entry entry : entries) {
            if (!cheats.isEmpty()) {
                cheats.append(separator);
                plainCheats.append(", ");
            }
            cheats.append(plugin.messages().raw("compact-alert-cheat",
                    "cheat", entry.name, "count", String.valueOf(entry.count)));
            plainCheats.append(entry.name).append(" x").append(entry.count);
            flags += entry.count;
        }
        String[] placeholders = {
                "player", player.getName(),
                "cheats", cheats.toString(),
                "flags", String.valueOf(flags),
                "vl", MathUtil.format(data.totalViolations(), 1),
                "ping", String.valueOf(data.ping()),
                "tps", MathUtil.format(plugin.violations().currentTps(), 1),
                "client", ClientUtil.describe(player),
                "mode", plugin.messages().raw(settings.mitigation ? "mode-blocking" : "mode-watching")
        };

        List<String> hover = new ArrayList<>();
        for (String line : plugin.messages().rawList("compact-alert-hover", placeholders)) {
            if (!line.contains("%details%")) {
                hover.add(line);
                continue;
            }
            for (AlertDigest.Entry entry : entries) {
                hover.add(plugin.messages().raw("compact-alert-detail",
                        "cheat", entry.name, "count", String.valueOf(entry.count),
                        "checks", types(entry.types), "vl", MathUtil.format(entry.vl, 1)));
                if (entry.lastDebug != null && !entry.lastDebug.isEmpty()) {
                    hover.add(plugin.messages().raw("compact-alert-detail-info", "debug", entry.lastDebug));
                }
            }
        }

        Component line = plugin.messages().get("compact-alert", placeholders)
                .hoverEvent(HoverEvent.showText(Text.lines(hover)))
                .clickEvent(ClickEvent.runCommand(clickCommand(player)));
        for (PlayerData staffData : plugin.data().all()) {
            Player staff = staffData.player();
            if (staffData.alerts && !staffData.verbose && staff.hasPermission("astral.alerts")) {
                staff.sendMessage(line);
            }
        }
        if (settings.alertConsole) {
            StringBuilder details = new StringBuilder();
            for (AlertDigest.Entry entry : entries) {
                if (!details.isEmpty()) {
                    details.append("; ");
                }
                details.append(types(entry.types));
                if (entry.lastDebug != null && !entry.lastDebug.isEmpty()) {
                    details.append(" - ").append(entry.lastDebug);
                }
            }
            Bukkit.getConsoleSender().sendMessage(Text.color(plugin.messages().raw("compact-alert", placeholders)
                    + " &8(" + details + ")"));
        }
        plugin.logs().log("ALERT " + player.getName() + " may be using " + plainCheats
                + " vl=" + MathUtil.format(data.totalViolations(), 1));
    }

    private static String types(Map<String, Integer> types) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> type : types.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(type.getKey()).append(" x").append(type.getValue());
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ every flag (verbose / debug)

    private String[] flagPlaceholders(Check check, PlayerData data, String type, double vl, String debug) {
        Player player = data.player();
        return new String[]{
                "player", player.getName(),
                "check", check.name(),
                "type", type,
                "cheat", names.name(check.id(), check.name(), type),
                "vl", MathUtil.format(vl, 1),
                "ping", String.valueOf(data.ping()),
                "tps", MathUtil.format(plugin.violations().currentTps(), 1),
                "debug", debug,
                "category", check.category().display(),
                "description", check.description(),
                "client", ClientUtil.describe(player)
        };
    }

    private void sendVerbose(Check check, String[] placeholders, Player player) {
        Component verbose = null;
        for (PlayerData staffData : plugin.data().all()) {
            boolean wantsVerbose = staffData.verbose || staffData.debugChecks.contains(check.id());
            if (!wantsVerbose) {
                continue;
            }
            Player staff = staffData.player();
            if (!staff.hasPermission("astral.verbose")) {
                continue;
            }
            if (verbose == null) {
                verbose = plugin.messages().get("verbose", placeholders)
                        .hoverEvent(HoverEvent.showText(plugin.messages().lines("alert-hover", placeholders)))
                        .clickEvent(ClickEvent.runCommand(clickCommand(player)));
            }
            staff.sendMessage(verbose);
        }
    }

    /** alerts.compact: false - one line per flag (with a cooldown per check). */
    private void legacyAlert(Check check, PlayerData data, String[] placeholders, Player player, String debug) {
        long now = System.currentTimeMillis();
        Long last = data.lastAlert.get(check.id());
        if (last != null && now - last < plugin.settings().alertCooldownMs) {
            return;
        }
        data.lastAlert.put(check.id(), now);
        Component alert = null;
        for (PlayerData staffData : plugin.data().all()) {
            Player staff = staffData.player();
            if (staffData.alerts && !staffData.verbose && staff.hasPermission("astral.alerts")) {
                if (alert == null) {
                    alert = plugin.messages().get("alert", placeholders)
                            .hoverEvent(HoverEvent.showText(plugin.messages().lines("alert-hover", placeholders)))
                            .clickEvent(ClickEvent.runCommand(clickCommand(player)));
                }
                staff.sendMessage(alert);
            }
        }
        if (plugin.settings().alertConsole) {
            Bukkit.getConsoleSender().sendMessage(Text.color(
                    plugin.messages().raw("alert", placeholders) + " &8(" + debug + ")"));
        }
    }

    private String clickCommand(Player player) {
        return plugin.settings().alertClickCommand.replace("%player%", player.getName());
    }
}
