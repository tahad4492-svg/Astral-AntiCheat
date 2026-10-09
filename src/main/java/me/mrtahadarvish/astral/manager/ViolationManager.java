package me.mrtahadarvish.astral.manager;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.data.FlagRecord;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.MathUtil;
import me.mrtahadarvish.astral.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles everything that happens after a check detected something: violation levels,
 * alerts, logging, mitigation (setbacks) and punishments.
 */
public final class ViolationManager {

    private final AstralAntiCheat plugin;
    private long totalFlags;
    private long totalPunishments;

    public ViolationManager(AstralAntiCheat plugin) {
        this.plugin = plugin;
    }

    /**
     * Adds violations for a check. Returns true when the check should block the cheat.
     */
    public boolean flag(Check check, PlayerData data, String type, double amount, String debug) {
        if (data == null || data.isBypassing(check.id())) {
            return false;
        }
        if (!Bukkit.isPrimaryThread()) {
            // async events (chat) - process the flag on the main thread
            Bukkit.getScheduler().runTask(plugin, () -> flag(check, data, type, amount, debug));
            return check.mitigate() && data.violation(check.id()) + amount >= check.mitigateVl();
        }
        if (isLagging()) {
            return false;
        }
        double vl = data.addViolation(check.id(), amount);
        if (check.category() == me.mrtahadarvish.astral.check.CheckCategory.MOVEMENT) {
            data.movementFlagged = true;
        }
        data.totalFlags++;
        check.incrementFlags();
        totalFlags++;

        data.flags.addFirst(new FlagRecord(System.currentTimeMillis(), check.name(), type, vl, debug));
        while (data.flags.size() > plugin.settings().logMemorySize) {
            data.flags.removeLast();
        }

        plugin.logs().log(data.name() + " failed " + check.name() + " (" + type + ") vl=" + MathUtil.format(vl, 2)
                + " ping=" + data.ping() + " | " + debug);
        plugin.alerts().alert(check, data, type, vl, debug);

        if (plugin.settings().punishEnabled && check.punish() && vl >= check.punishVl()
                && !data.punished.contains(check.id())) {
            data.punished.add(check.id());
            punish(check, data, type, vl);
        }
        return check.mitigate() && vl >= check.mitigateVl();
    }

    private void punish(Check check, PlayerData data, String type, double vl) {
        totalPunishments++;
        Player player = data.player();
        String[] placeholders = {
                "player", player.getName(),
                "uuid", player.getUniqueId().toString(),
                "check", check.name(),
                "type", type,
                "vl", MathUtil.format(vl, 1),
                "ping", String.valueOf(data.ping())
        };
        plugin.logs().log("PUNISH " + player.getName() + " for " + check.name() + " (" + type + ") vl=" + MathUtil.format(vl, 2));

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.settings().punishBroadcast) {
                Bukkit.getServer().sendMessage(plugin.messages().lines("punish-broadcast", placeholders));
            }
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("astral.notify.punish")) {
                    plugin.messages().send(staff, "punish-staff", placeholders);
                }
            }
            for (String command : check.punishCommands()) {
                String parsed = command;
                for (int i = 0; i + 1 < placeholders.length; i += 2) {
                    parsed = parsed.replace("%" + placeholders[i] + "%", placeholders[i + 1]);
                }
                if (parsed.startsWith("/")) {
                    parsed = parsed.substring(1);
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        });
    }

    /** Teleports a player back to the last legit position (movement mitigation). */
    public void setback(PlayerData data) {
        if (!plugin.settings().mitigation) {
            return;
        }
        Player player = data.player();
        Location safe = data.lastSafe;
        if (safe == null || safe.getWorld() == null || !safe.getWorld().equals(player.getWorld())) {
            return;
        }
        if (PlayerData.since(data.lastSetback) < 1) {
            return;
        }
        data.lastSetback = PlayerData.now();
        Location target = safe.clone();
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());
        player.setFallDistance(0.0F);
        player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    /** Lowers every violation level a bit. Runs every "violations.decay-interval-seconds". */
    public void decay() {
        for (PlayerData data : plugin.data().all()) {
            List<String> ids = new ArrayList<>(data.violations().keySet());
            for (String id : ids) {
                Check check = plugin.checks().get(id);
                double decay = check == null ? 1.0 : check.decay();
                if (decay <= 0.0) {
                    continue;
                }
                double value = data.violation(id) - decay;
                data.setViolation(id, value);
            }
        }
    }

    public boolean isLagging() {
        if (!plugin.settings().lagProtection) {
            return false;
        }
        return currentTps() < plugin.settings().minTps;
    }

    /** TPS of the last ~5 seconds. */
    public double currentTps() {
        double mspt = Bukkit.getAverageTickTime();
        if (mspt <= 0.0) {
            return 20.0;
        }
        return Math.min(20.0, 1000.0 / mspt);
    }

    public long totalFlags() {
        return totalFlags;
    }

    public long totalPunishments() {
        return totalPunishments;
    }

    public String topChecks(PlayerData data, int limit) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>(data.violations().entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(limit, entries.size()); i++) {
            Check check = plugin.checks().get(entries.get(i).getKey());
            if (!sb.isEmpty()) {
                sb.append("&7, ");
            }
            sb.append("&f").append(check == null ? entries.get(i).getKey() : check.name())
                    .append(" &c").append(MathUtil.format(entries.get(i).getValue(), 1));
        }
        return sb.isEmpty() ? "&anone" : sb.toString();
    }

    public String stripTop(PlayerData data, int limit) {
        return Text.strip(topChecks(data, limit));
    }
}
