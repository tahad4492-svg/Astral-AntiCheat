package me.mrtahadarvish.astral.manager;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.ClientUtil;
import me.mrtahadarvish.astral.util.MathUtil;
import me.mrtahadarvish.astral.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Sends flag alerts to staff (chat with hover + click) and to the console. */
public final class AlertManager {

    private final AstralAntiCheat plugin;

    public AlertManager(AstralAntiCheat plugin) {
        this.plugin = plugin;
    }

    public void alert(Check check, PlayerData data, String type, double vl, String debug) {
        Player player = data.player();
        long now = System.currentTimeMillis();
        boolean reachedAlertVl = vl >= check.alertVl();
        Long last = data.lastAlert.get(check.id());
        boolean cooldownDone = last == null || now - last >= plugin.settings().alertCooldownMs;
        boolean normalAlert = reachedAlertVl && cooldownDone;
        if (normalAlert) {
            data.lastAlert.put(check.id(), now);
        }

        String[] placeholders = {
                "player", player.getName(),
                "check", check.name(),
                "type", type,
                "vl", MathUtil.format(vl, 1),
                "ping", String.valueOf(data.ping()),
                "tps", MathUtil.format(plugin.violations().currentTps(), 1),
                "debug", debug,
                "category", check.category().display(),
                "description", check.description(),
                "client", ClientUtil.describe(player)
        };

        Component alert = null;
        Component verbose = null;
        for (PlayerData staffData : plugin.data().all()) {
            Player staff = staffData.player();
            boolean debugging = staffData.debugChecks.contains(check.id());
            boolean wantsVerbose = staffData.verbose || debugging;
            if (wantsVerbose && staff.hasPermission("astral.verbose")) {
                if (verbose == null) {
                    verbose = plugin.messages().get("verbose", placeholders)
                            .hoverEvent(HoverEvent.showText(plugin.messages().lines("alert-hover", placeholders)))
                            .clickEvent(ClickEvent.runCommand(clickCommand(player)));
                }
                staff.sendMessage(verbose);
                continue;
            }
            if (normalAlert && staffData.alerts && staff.hasPermission("astral.alerts")) {
                if (alert == null) {
                    alert = build(placeholders, player);
                }
                staff.sendMessage(alert);
            }
        }
        if (normalAlert && plugin.settings().alertConsole) {
            Bukkit.getConsoleSender().sendMessage(Text.color(
                    plugin.messages().raw("alert", placeholders) + " &8(" + debug + ")"));
        }
    }

    private Component build(String[] placeholders, Player player) {
        return plugin.messages().get("alert", placeholders)
                .hoverEvent(HoverEvent.showText(plugin.messages().lines("alert-hover", placeholders)))
                .clickEvent(ClickEvent.runCommand(clickCommand(player)));
    }

    private String clickCommand(Player player) {
        return plugin.settings().alertClickCommand.replace("%player%", player.getName());
    }
}
