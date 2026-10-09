package me.mrtahadarvish.astral.command;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.data.FlagRecord;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.ClientUtil;
import me.mrtahadarvish.astral.util.DurationUtil;
import me.mrtahadarvish.astral.util.MathUtil;
import me.mrtahadarvish.astral.util.Text;
import io.papermc.paper.ban.BanListType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /astral - every staff command of Astral.
 */
public final class AstralCommand implements TabExecutor {

    /** name, usage, description */
    private static final String[][] COMMANDS = {
            {"help", "", "Show this help"},
            {"version", "[player]", "Plugin, server and client versions"},
            {"reload", "", "Reload config.yml and messages.yml"},
            {"alerts", "[on|off]", "Toggle receiving cheat alerts (flags)"},
            {"verbose", "", "See EVERY flag (no cooldown / minimum VL)"},
            {"checks", "[category]", "List every check and its state"},
            {"toggle", "<check> [on|off]", "Enable / disable a check"},
            {"info", "<player>", "Full anticheat profile of a player"},
            {"violations", "<player>", "Violation levels of a player"},
            {"logs", "<player> [page]", "Recent flags of a player"},
            {"reset", "<player|*> [check]", "Reset violations"},
            {"bypass", "<player|list>", "Toggle anticheat bypass for a player"},
            {"kick", "<player> [reason]", "Kick a player with the Astral screen"},
            {"ban", "<player> [time] [reason]", "Ban a player (7d, 12h, 30m, perm)"},
            {"unban", "<player>", "Unban a player"},
            {"freeze", "<player>", "Freeze / unfreeze a player"},
            {"tp", "<player>", "Teleport to a player"},
            {"cps", "<player>", "Watch the CPS of a player"},
            {"ping", "<player>", "Show the ping of a player"},
            {"client", "<player>", "Client version, brand and platform"},
            {"gui", "", "Open the staff menu"},
            {"stats", "", "Anticheat statistics"},
            {"debug", "<check>", "Toggle debug output of one check"}
    };

    private final AstralAntiCheat plugin;

    public AstralCommand(AstralAntiCheat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        sub = alias(sub);
        if (!isKnown(sub)) {
            plugin.messages().send(sender, "unknown-subcommand");
            return true;
        }
        if (!sender.hasPermission("astral.command." + sub)) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        String[] rest = args.length == 0 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
        switch (sub) {
            case "help" -> help(sender, label);
            case "version" -> version(sender, rest);
            case "reload" -> reload(sender);
            case "alerts" -> alerts(sender, rest);
            case "verbose" -> verbose(sender);
            case "checks" -> checks(sender, rest);
            case "toggle" -> toggle(sender, rest);
            case "info" -> info(sender, rest);
            case "violations" -> violations(sender, rest);
            case "logs" -> logs(sender, rest);
            case "reset" -> reset(sender, rest);
            case "bypass" -> bypass(sender, rest);
            case "kick" -> kick(sender, rest);
            case "ban" -> ban(sender, rest);
            case "unban" -> unban(sender, rest);
            case "freeze" -> freeze(sender, rest);
            case "tp" -> teleport(sender, rest);
            case "cps" -> cps(sender, rest);
            case "ping" -> ping(sender, rest);
            case "client" -> client(sender, rest);
            case "gui" -> gui(sender);
            case "stats" -> stats(sender);
            case "debug" -> debug(sender, rest);
            default -> plugin.messages().send(sender, "unknown-subcommand");
        }
        return true;
    }

    private static String alias(String sub) {
        return switch (sub) {
            case "ver", "v", "about" -> "version";
            case "vl", "violation" -> "violations";
            case "log", "history" -> "logs";
            case "alert", "notify" -> "alerts";
            case "check", "list" -> "checks";
            case "profile", "player", "lookup" -> "info";
            case "exempt" -> "bypass";
            case "ss", "unfreeze" -> "freeze";
            case "teleport" -> "tp";
            case "menu", "panel" -> "gui";
            case "statistics" -> "stats";
            case "brand" -> "client";
            case "?" -> "help";
            default -> sub;
        };
    }

    private static boolean isKnown(String sub) {
        for (String[] entry : COMMANDS) {
            if (entry[0].equals(sub)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ helpers

    private void send(CommandSender sender, String message) {
        sender.sendMessage(Text.color(message));
    }

    private String prefix() {
        return plugin.messages().prefix();
    }

    private void usage(CommandSender sender, String sub) {
        for (String[] entry : COMMANDS) {
            if (entry[0].equals(sub)) {
                send(sender, prefix() + "&cUsage: &f/astral " + entry[0] + (entry[1].isEmpty() ? "" : " " + entry[1]));
                return;
            }
        }
    }

    private Player online(CommandSender sender, String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            player = Bukkit.getPlayer(name);
        }
        if (player == null) {
            plugin.messages().send(sender, "player-not-found", "player", name);
        }
        return player;
    }

    private static String line() {
        return "&8&m                                                    ";
    }

    // ------------------------------------------------------------------ sub commands

    private void help(CommandSender sender, String label) {
        send(sender, line());
        send(sender, " &#00E5FF&l" + AstralAntiCheat.DISPLAY_NAME + " &7v" + plugin.getPluginMeta().getVersion());
        send(sender, "");
        for (String[] entry : COMMANDS) {
            if (!sender.hasPermission("astral.command." + entry[0])) {
                continue;
            }
            String usage = "/" + label + " " + entry[0] + (entry[1].isEmpty() ? "" : " " + entry[1]);
            sender.sendMessage(Text.color(" &#00E5FF" + usage + " &8- &7" + entry[2])
                    .hoverEvent(HoverEvent.showText(Text.color("&7Click to use")))
                    .clickEvent(ClickEvent.suggestCommand("/" + label + " " + entry[0] + " ")));
        }
        send(sender, line());
    }

    private void version(CommandSender sender, String[] args) {
        if (args.length >= 1) {
            if (!sender.hasPermission("astral.command.version.others")) {
                plugin.messages().send(sender, "no-permission");
                return;
            }
            Player target = online(sender, args[0]);
            if (target != null) {
                clientInfo(sender, target);
            }
            return;
        }
        send(sender, line());
        send(sender, " &#00E5FF&l" + AstralAntiCheat.DISPLAY_NAME + " &7AntiCheat &8by &f" + AstralAntiCheat.AUTHOR);
        send(sender, " &7Plugin version: &f" + plugin.getPluginMeta().getVersion());
        send(sender, " &7Author: &f" + AstralAntiCheat.AUTHOR);
        send(sender, " &7Mode: " + (plugin.settings().mitigation ? "&cBLOCK" : "&aWATCH &7(alerts only)"));
        send(sender, " &7Server: &f" + Bukkit.getName() + " " + Bukkit.getMinecraftVersion() + " &8(" + Bukkit.getVersion() + ")");
        send(sender, " &7Server protocol: &f" + ClientUtil.serverProtocol());
        send(sender, " &7Checks: &f" + plugin.checks().all().size() + " loaded &8(&a" + plugin.checks().enabledCount() + " enabled&8)");
        if (sender instanceof Player player) {
            send(sender, " &7Your client: &f" + ClientUtil.version(player) + " &8| &7brand: &f" + ClientUtil.brand(player));
        }
        send(sender, line());
    }

    private void clientInfo(CommandSender sender, Player target) {
        send(sender, line());
        send(sender, " &#00E5FF&l" + target.getName() + "&7's client");
        send(sender, " &7Version: &f" + ClientUtil.versionName(ClientUtil.protocol(target)));
        send(sender, " &7Protocol: &f" + ClientUtil.protocol(target));
        send(sender, " &7Brand: &f" + ClientUtil.brand(target));
        send(sender, " &7Platform: &f" + (ClientUtil.isBedrock(target.getUniqueId()) ? "Bedrock (Geyser)" : "Java Edition"));
        send(sender, " &7Ping: &f" + target.getPing() + "ms");
        send(sender, line());
    }

    private void reload(CommandSender sender) {
        long start = System.currentTimeMillis();
        plugin.reload();
        plugin.messages().send(sender, "reloaded", "time", String.valueOf(System.currentTimeMillis() - start));
    }

    private void alerts(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        PlayerData data = plugin.data().get(player);
        if (data == null) {
            return;
        }
        if (args.length >= 1) {
            data.alerts = args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("true");
        } else {
            data.alerts = !data.alerts;
        }
        plugin.messages().send(sender, data.alerts ? "alerts-enabled" : "alerts-disabled");
    }

    private void verbose(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        PlayerData data = plugin.data().get(player);
        if (data == null) {
            return;
        }
        data.verbose = !data.verbose;
        plugin.messages().send(sender, data.verbose ? "verbose-enabled" : "verbose-disabled");
    }

    private void checks(CommandSender sender, String[] args) {
        CheckCategory filter = null;
        if (args.length >= 1) {
            for (CheckCategory category : CheckCategory.values()) {
                if (category.name().equalsIgnoreCase(args[0]) || category.display().equalsIgnoreCase(args[0])) {
                    filter = category;
                }
            }
        }
        send(sender, line());
        send(sender, " &#00E5FF&lChecks &8(&a" + plugin.checks().enabledCount() + "&7/&f" + plugin.checks().all().size() + "&8)");
        boolean canToggle = sender.hasPermission("astral.command.toggle");
        for (CheckCategory category : CheckCategory.values()) {
            if (filter != null && filter != category) {
                continue;
            }
            Component row = Text.color(" " + category.color() + category.display() + "&8: ");
            boolean first = true;
            for (Check check : plugin.checks().all()) {
                if (check.category() != category) {
                    continue;
                }
                if (!first) {
                    row = row.append(Text.color("&8, "));
                }
                first = false;
                Component entry = Text.color((check.enabled() ? "&a" : "&c&m") + check.name())
                        .hoverEvent(HoverEvent.showText(Text.color("&f" + check.name() + "\n&7" + check.description()
                                + "\n&7Id: &f" + check.id() + "\n&7Flags: &f" + check.totalFlags()
                                + (canToggle ? "\n\n&eClick to " + (check.enabled() ? "disable" : "enable") : ""))));
                if (canToggle) {
                    entry = entry.clickEvent(ClickEvent.runCommand("/astral toggle " + check.id()));
                }
                row = row.append(entry);
            }
            sender.sendMessage(row);
        }
        send(sender, line());
    }

    private void toggle(CommandSender sender, String[] args) {
        if (args.length < 1) {
            usage(sender, "toggle");
            return;
        }
        Check check = plugin.checks().get(args[0]);
        if (check == null) {
            plugin.messages().send(sender, "unknown-check", "check", args[0]);
            return;
        }
        boolean enable = args.length >= 2 ? args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true") : !check.enabled();
        plugin.checks().setEnabled(check, enable);
        plugin.messages().send(sender, enable ? "check-enabled" : "check-disabled", "check", check.name());
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 1) {
            usage(sender, "info");
            return;
        }
        Player target = online(sender, args[0]);
        if (target == null) {
            return;
        }
        PlayerData data = plugin.data().get(target);
        if (data == null) {
            return;
        }
        boolean permission = target.hasPermission("astral.bypass");
        boolean manual = plugin.bypass().contains(target.getUniqueId());
        long online = (System.currentTimeMillis() - data.joinTime) / 1000L;
        send(sender, line());
        send(sender, " &#00E5FF&lAstral &7» &f" + target.getName());
        send(sender, " &7Client: &f" + ClientUtil.describe(target) + " &8(protocol " + ClientUtil.protocol(target) + ")");
        send(sender, " &7Ping: &f" + data.ping() + "ms &8| &7CPS: &f" + data.cps() + " &8| &7Online: &f" + DurationUtil.format(Duration.ofSeconds(online)));
        send(sender, " &7Bypass: " + (data.bypassPermission ? "&aYES" : "&cno") + " &8(&7permission: &f" + (permission ? "yes" : "no")
                + "&8, &7manual: &f" + (manual ? "yes" : "no") + "&8)");
        if (!data.bypassChecks.isEmpty()) {
            send(sender, " &7Bypassed checks: &f" + String.join(", ", data.bypassChecks));
        }
        send(sender, " &7Frozen: " + (plugin.freeze().isFrozen(target) ? "&bYES" : "&fno") + " &8| &7Gamemode: &f"
                + target.getGameMode().name().toLowerCase(Locale.ROOT));
        send(sender, " &7Total flags: &f" + data.totalFlags + " &8| &7Total VL: &c" + MathUtil.format(data.totalViolations(), 1));
        send(sender, " &7Top checks: " + plugin.violations().topChecks(data, 5));
        send(sender, line());
    }

    private void violations(CommandSender sender, String[] args) {
        if (args.length < 1) {
            usage(sender, "violations");
            return;
        }
        Player target = online(sender, args[0]);
        if (target == null) {
            return;
        }
        PlayerData data = plugin.data().get(target);
        if (data == null) {
            return;
        }
        send(sender, line());
        send(sender, " &#00E5FF&lViolations &7» &f" + target.getName());
        if (data.violations().isEmpty()) {
            send(sender, " &aNo violations.");
        }
        List<Map.Entry<String, Double>> entries = new ArrayList<>(data.violations().entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        for (Map.Entry<String, Double> entry : entries) {
            Check check = plugin.checks().get(entry.getKey());
            String name = check == null ? entry.getKey() : check.name();
            String extra = check == null ? "" : " &8(&7alert " + MathUtil.format(check.alertVl(), 1) + " &8| &7punish "
                    + MathUtil.format(check.punishVl(), 1) + "&8)";
            sender.sendMessage(Text.color(" &8- &f" + name + "&7: &c" + MathUtil.format(entry.getValue(), 2) + extra)
                    .hoverEvent(HoverEvent.showText(Text.color(check == null ? "" : "&7" + check.description()))));
        }
        send(sender, line());
    }

    private void logs(CommandSender sender, String[] args) {
        if (args.length < 1) {
            usage(sender, "logs");
            return;
        }
        Player target = online(sender, args[0]);
        if (target == null) {
            return;
        }
        PlayerData data = plugin.data().get(target);
        if (data == null) {
            return;
        }
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }
        List<FlagRecord> records = new ArrayList<>(data.flags);
        int pages = Math.max(1, (int) Math.ceil(records.size() / 10.0));
        page = Math.min(page, pages);
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
        send(sender, line());
        send(sender, " &#00E5FF&lLogs &7» &f" + target.getName() + " &8(&7page " + page + "/" + pages + "&8)");
        if (records.isEmpty()) {
            send(sender, " &aNo flags this session.");
        }
        for (int i = (page - 1) * 10; i < Math.min(records.size(), page * 10); i++) {
            FlagRecord record = records.get(i);
            sender.sendMessage(Text.color(" &8[" + format.format(new Date(record.time())) + "] &f" + record.check()
                            + " &8(&7" + record.type() + "&8) &7vl &c" + MathUtil.format(record.vl(), 1))
                    .hoverEvent(HoverEvent.showText(Text.color("&7" + record.debug()))));
        }
        if (page < pages) {
            sender.sendMessage(Text.color(" &e» Next page").clickEvent(ClickEvent.runCommand("/astral logs " + target.getName() + " " + (page + 1))));
        }
        send(sender, line());
    }

    private void reset(CommandSender sender, String[] args) {
        if (args.length < 1) {
            usage(sender, "reset");
            return;
        }
        if (args[0].equals("*") || args[0].equalsIgnoreCase("all")) {
            for (PlayerData data : plugin.data().all()) {
                data.resetViolations();
            }
            plugin.messages().send(sender, "reset-all");
            return;
        }
        Player target = online(sender, args[0]);
        if (target == null) {
            return;
        }
        PlayerData data = plugin.data().get(target);
        if (data == null) {
            return;
        }
        if (args.length >= 2) {
            Check check = plugin.checks().get(args[1]);
            if (check == null) {
                plugin.messages().send(sender, "unknown-check", "check", args[1]);
                return;
            }
            data.resetViolations(check.id());
            plugin.messages().send(sender, "reset-check", "check", check.name(), "player", target.getName());
            return;
        }
        data.resetViolations();
        plugin.messages().send(sender, "reset-player", "player", target.getName());
    }

    private void bypass(CommandSender sender, String[] args) {
        if (args.length < 1) {
            usage(sender, "bypass");
            return;
        }
        if (args[0].equalsIgnoreCase("list")) {
            List<String> names = new ArrayList<>();
            for (UUID uuid : plugin.bypass().all()) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                names.add(player.getName() == null ? uuid.toString() : player.getName());
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("astral.bypass") && !plugin.bypass().contains(player.getUniqueId())) {
                    names.add(player.getName() + " (permission)");
                }
            }
            plugin.messages().send(sender, "bypass-list", "list", names.isEmpty() ? "none" : String.join(", ", names));
            return;
        }
        UUID uuid;
        String name;
        Player online = Bukkit.getPlayerExact(args[0]);
        if (online != null) {
            uuid = online.getUniqueId();
            name = online.getName();
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(args[0]);
            if (offline == null) {
                plugin.messages().send(sender, "player-not-found", "player", args[0]);
                return;
            }
            uuid = offline.getUniqueId();
            name = offline.getName() == null ? args[0] : offline.getName();
        }
        boolean now = plugin.bypass().toggle(uuid);
        if (online != null) {
            PlayerData data = plugin.data().get(online);
            if (data != null) {
                plugin.data().refreshBypass(data);
                data.resetViolations();
            }
        }
        plugin.messages().send(sender, now ? "bypass-enabled" : "bypass-disabled", "player", name);
        if (!now && online != null && online.hasPermission("astral.bypass")) {
            plugin.messages().send(sender, "bypass-permission-note", "player", name);
        }
        plugin.logs().log("BYPASS " + name + " -> " + now + " by " + sender.getName());
    }

    private void kick(CommandSender sender, String[] args) {
        if (args.length < 1) {
            usage(sender, "kick");
            return;
        }
        Player target = online(sender, args[0]);
        if (target == null) {
            return;
        }
        String reason = args.length >= 2 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Unfair Advantage";
        target.kick(plugin.messages().lines("kick-screen", "reason", reason));
        plugin.messages().send(sender, "kicked", "player", target.getName(), "reason", reason);
        plugin.logs().log("KICK " + target.getName() + " by " + sender.getName() + ": " + reason);
    }

    private void ban(CommandSender sender, String[] args) {
        if (args.length < 1) {
            usage(sender, "ban");
            return;
        }
        Duration duration;
        int reasonStart = 1;
        try {
            if (args.length >= 2 && DurationUtil.isDuration(args[1])) {
                duration = DurationUtil.parse(args[1]);
                reasonStart = 2;
            } else if (args.length >= 2 && (args[1].equalsIgnoreCase("perm") || args[1].equalsIgnoreCase("permanent"))) {
                duration = null;
                reasonStart = 2;
            } else {
                duration = DurationUtil.parse(plugin.settings().defaultBanDuration);
            }
        } catch (IllegalArgumentException e) {
            duration = null;
        }
        String reason = args.length > reasonStart ? String.join(" ", Arrays.copyOfRange(args, reasonStart, args.length)) : "Unfair Advantage";
        String source = AstralAntiCheat.DISPLAY_NAME + " (" + sender.getName() + ")";
        String expires = duration == null ? "Never" : DurationUtil.format(duration);

        Player online = Bukkit.getPlayerExact(args[0]);
        String name;
        if (online != null) {
            name = online.getName();
            online.ban(reason, duration, source, false);
            online.kick(plugin.messages().lines("ban-screen", "reason", reason, "expires", expires));
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(args[0]);
            if (offline == null) {
                plugin.messages().send(sender, "player-not-found", "player", args[0]);
                return;
            }
            name = offline.getName() == null ? args[0] : offline.getName();
            offline.ban(reason, duration, source);
        }
        plugin.messages().send(sender, "banned", "player", name, "duration", expires, "reason", reason);
        plugin.logs().log("BAN " + name + " for " + expires + " by " + sender.getName() + ": " + reason);
    }

    private void unban(CommandSender sender, String[] args) {
        if (args.length < 1) {
            usage(sender, "unban");
            return;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(args[0]);
        if (offline == null) {
            plugin.messages().send(sender, "player-not-found", "player", args[0]);
            return;
        }
        String name = offline.getName() == null ? args[0] : offline.getName();
        if (!offline.isBanned()) {
            plugin.messages().send(sender, "not-banned", "player", name);
            return;
        }
        Bukkit.getBanList(BanListType.PROFILE).pardon(offline.getPlayerProfile());
        plugin.messages().send(sender, "unbanned", "player", name);
        plugin.logs().log("UNBAN " + name + " by " + sender.getName());
    }

    private void freeze(CommandSender sender, String[] args) {
        if (args.length < 1) {
            usage(sender, "freeze");
            return;
        }
        Player target = online(sender, args[0]);
        if (target == null) {
            return;
        }
        boolean frozen = plugin.freeze().toggle(target);
        plugin.messages().send(sender, frozen ? "frozen" : "unfrozen", "player", target.getName());
        if (!frozen) {
            plugin.messages().send(target, "unfrozen", "player", target.getName());
        }
        plugin.logs().log((frozen ? "FREEZE " : "UNFREEZE ") + target.getName() + " by " + sender.getName());
    }

    private void teleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (args.length < 1) {
            usage(sender, "tp");
            return;
        }
        Player target = online(sender, args[0]);
        if (target == null) {
            return;
        }
        player.teleport(target.getLocation(), PlayerTeleportEvent.TeleportCause.COMMAND);
        plugin.messages().send(sender, "teleported", "player", target.getName());
    }

    private void cps(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        PlayerData data = plugin.data().get(player);
        if (data == null) {
            return;
        }
        if (args.length < 1 || (data.cpsTarget != null && Bukkit.getPlayer(data.cpsTarget) != null
                && Bukkit.getPlayer(data.cpsTarget).getName().equalsIgnoreCase(args[0]))) {
            data.cpsTarget = null;
            plugin.messages().send(sender, "cps-stop");
            return;
        }
        Player target = online(sender, args[0]);
        if (target == null) {
            return;
        }
        data.cpsTarget = target.getUniqueId();
        plugin.messages().send(sender, "cps-start", "player", target.getName());
    }

    private void ping(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 1) {
            target = online(sender, args[0]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            usage(sender, "ping");
            return;
        }
        if (target != null) {
            send(sender, prefix() + "&f" + target.getName() + "&7's ping: &f" + target.getPing() + "ms");
        }
    }

    private void client(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 1) {
            target = online(sender, args[0]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            usage(sender, "client");
            return;
        }
        if (target != null) {
            clientInfo(sender, target);
        }
    }

    private void gui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        plugin.menus().openPlayers(player);
    }

    private void stats(CommandSender sender) {
        long uptime = (System.currentTimeMillis() - plugin.startTime()) / 1000L;
        long bypassing = plugin.data().all().stream().filter(data -> data.bypassPermission).count();
        send(sender, line());
        send(sender, " &#00E5FF&l" + AstralAntiCheat.DISPLAY_NAME + " &7statistics");
        send(sender, " &7Uptime: &f" + DurationUtil.format(Duration.ofSeconds(uptime)));
        send(sender, " &7Flags: &f" + plugin.violations().totalFlags() + " &8| &7Punishments: &f" + plugin.violations().totalPunishments());
        send(sender, " &7Players: &f" + Bukkit.getOnlinePlayers().size() + " &8| &7Bypassing: &f" + bypassing);
        send(sender, " &7Checks: &a" + plugin.checks().enabledCount() + "&7/&f" + plugin.checks().all().size() + " enabled");
        send(sender, " &7TPS: &f" + MathUtil.format(plugin.violations().currentTps(), 2)
                + (plugin.violations().isLagging() ? " &c(lag protection active)" : ""));
        send(sender, line());
    }

    private void debug(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (args.length < 1) {
            usage(sender, "debug");
            return;
        }
        Check check = plugin.checks().get(args[0]);
        if (check == null) {
            plugin.messages().send(sender, "unknown-check", "check", args[0]);
            return;
        }
        PlayerData data = plugin.data().get(player);
        if (data == null) {
            return;
        }
        boolean enabled;
        if (data.debugChecks.remove(check.id())) {
            enabled = false;
        } else {
            data.debugChecks.add(check.id());
            enabled = true;
        }
        plugin.messages().send(sender, enabled ? "debug-enabled" : "debug-disabled", "check", check.name());
    }

    // ------------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            for (String[] entry : COMMANDS) {
                if (sender.hasPermission("astral.command." + entry[0])) {
                    subs.add(entry[0]);
                }
            }
            return filter(subs, args[0]);
        }
        String sub = alias(args[0].toLowerCase(Locale.ROOT));
        if (!isKnown(sub) || !sender.hasPermission("astral.command." + sub)) {
            return List.of();
        }
        if (args.length == 2) {
            return switch (sub) {
                case "version", "info", "violations", "logs", "kick", "ban", "freeze", "tp", "cps", "ping", "client" -> filter(players(), args[1]);
                case "reset" -> filter(concat(players(), List.of("*")), args[1]);
                case "bypass" -> filter(concat(players(), List.of("list")), args[1]);
                case "toggle", "debug" -> filter(checkIds(), args[1]);
                case "alerts" -> filter(List.of("on", "off"), args[1]);
                case "checks" -> filter(Arrays.stream(CheckCategory.values()).map(c -> c.display().toLowerCase(Locale.ROOT)).toList(), args[1]);
                case "unban" -> filter(Bukkit.getBannedPlayers().stream().map(OfflinePlayer::getName).filter(n -> n != null).toList(), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (sub) {
                case "reset" -> filter(checkIds(), args[2]);
                case "toggle" -> filter(List.of("on", "off"), args[2]);
                case "ban" -> filter(List.of("1h", "12h", "1d", "7d", "30d", "perm"), args[2]);
                case "kick" -> filter(List.of("Unfair Advantage", "Cheating"), args[2]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private static List<String> players() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }

    private List<String> checkIds() {
        return plugin.checks().all().stream().map(Check::id).collect(Collectors.toList());
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private static List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().collect(Collectors.toList());
    }
}
