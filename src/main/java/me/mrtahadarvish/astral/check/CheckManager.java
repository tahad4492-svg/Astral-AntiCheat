package me.mrtahadarvish.astral.check;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.combat.AimCheck;
import me.mrtahadarvish.astral.check.combat.AutoBlockCheck;
import me.mrtahadarvish.astral.check.combat.CriticalsCheck;
import me.mrtahadarvish.astral.check.combat.CrystalAuraCheck;
import me.mrtahadarvish.astral.check.combat.HitboxCheck;
import me.mrtahadarvish.astral.check.combat.KillAuraCheck;
import me.mrtahadarvish.astral.check.combat.ReachCheck;
import me.mrtahadarvish.astral.check.combat.VelocityCheck;
import me.mrtahadarvish.astral.check.macro.AutoClickerCheck;
import me.mrtahadarvish.astral.check.macro.AutoFishCheck;
import me.mrtahadarvish.astral.check.macro.AutoTotemCheck;
import me.mrtahadarvish.astral.check.macro.ChatCheck;
import me.mrtahadarvish.astral.check.macro.HotbarMacroCheck;
import me.mrtahadarvish.astral.check.macro.InventoryMacroCheck;
import me.mrtahadarvish.astral.check.movement.BoatFlyCheck;
import me.mrtahadarvish.astral.check.movement.ElytraFlyCheck;
import me.mrtahadarvish.astral.check.movement.FastClimbCheck;
import me.mrtahadarvish.astral.check.movement.FlightCheck;
import me.mrtahadarvish.astral.check.movement.JesusCheck;
import me.mrtahadarvish.astral.check.movement.NoFallCheck;
import me.mrtahadarvish.astral.check.movement.NoSlowCheck;
import me.mrtahadarvish.astral.check.movement.PhaseCheck;
import me.mrtahadarvish.astral.check.movement.SpeedCheck;
import me.mrtahadarvish.astral.check.movement.SprintCheck;
import me.mrtahadarvish.astral.check.movement.StepCheck;
import me.mrtahadarvish.astral.check.movement.TimerCheck;
import me.mrtahadarvish.astral.check.player.BadPacketsCheck;
import me.mrtahadarvish.astral.check.player.ClientBrandCheck;
import me.mrtahadarvish.astral.check.player.FastBowCheck;
import me.mrtahadarvish.astral.check.player.InventoryMoveCheck;
import me.mrtahadarvish.astral.check.world.BlockReachCheck;
import me.mrtahadarvish.astral.check.world.FastBreakCheck;
import me.mrtahadarvish.astral.check.world.FastPlaceCheck;
import me.mrtahadarvish.astral.check.world.InteractCheck;
import me.mrtahadarvish.astral.check.world.NukerCheck;
import me.mrtahadarvish.astral.check.world.ScaffoldCheck;
import me.mrtahadarvish.astral.check.world.XRayCheck;
import me.mrtahadarvish.astral.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Creates, configures and registers every check. */
public final class CheckManager {

    private final AstralAntiCheat plugin;
    private final Map<String, Check> checks = new LinkedHashMap<>();
    private final List<TickCheck> tickChecks = new ArrayList<>();
    private KillAuraCheck killAura;

    public CheckManager(AstralAntiCheat plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        // movement
        add(new TimerCheck(plugin));
        add(new FlightCheck(plugin));
        add(new SpeedCheck(plugin));
        add(new NoFallCheck(plugin));
        add(new StepCheck(plugin));
        add(new JesusCheck(plugin));
        add(new FastClimbCheck(plugin));
        add(new ElytraFlyCheck(plugin));
        add(new NoSlowCheck(plugin));
        add(new SprintCheck(plugin));
        add(new PhaseCheck(plugin));
        add(new BoatFlyCheck(plugin));
        // combat
        add(new ReachCheck(plugin));
        add(new HitboxCheck(plugin));
        killAura = add(new KillAuraCheck(plugin));
        add(new AutoBlockCheck(plugin));
        add(new CriticalsCheck(plugin));
        add(new VelocityCheck(plugin));
        add(new AimCheck(plugin));
        add(new CrystalAuraCheck(plugin));
        // player
        add(new InventoryMoveCheck(plugin));
        add(new BadPacketsCheck(plugin));
        add(new FastBowCheck(plugin));
        add(new ClientBrandCheck(plugin));
        // world
        add(new FastBreakCheck(plugin));
        add(new NukerCheck(plugin));
        add(new BlockReachCheck(plugin));
        add(new InteractCheck(plugin));
        add(new FastPlaceCheck(plugin));
        add(new ScaffoldCheck(plugin));
        add(new XRayCheck(plugin));
        // macros
        add(new AutoClickerCheck(plugin));
        add(new AutoTotemCheck(plugin));
        add(new InventoryMacroCheck(plugin));
        add(new HotbarMacroCheck(plugin));
        add(new AutoFishCheck(plugin));
        add(new ChatCheck(plugin));

        reload();
        for (Check check : checks.values()) {
            Bukkit.getPluginManager().registerEvents(check, plugin);
        }
    }

    private <T extends Check> T add(T check) {
        checks.put(check.id(), check);
        if (check instanceof TickCheck tickCheck) {
            tickChecks.add(tickCheck);
        }
        return check;
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        for (Check check : checks.values()) {
            check.load(config);
        }
    }

    /** Enables / disables a check and saves it to config.yml. */
    public void setEnabled(Check check, boolean enabled) {
        check.setEnabled(enabled);
        plugin.getConfig().set("checks." + check.id() + ".enabled", enabled);
        plugin.saveConfig();
    }

    /** Blocks every attack of the player for a while (after a combat cheat was detected). */
    public void lockCombat(PlayerData data) {
        int ticks = killAura == null ? 60 : killAura.lockTicks();
        data.combatLockUntil = Math.max(data.combatLockUntil, PlayerData.now() + ticks);
    }

    public Check get(String id) {
        if (id == null) {
            return null;
        }
        String key = id.toLowerCase(Locale.ROOT);
        Check check = checks.get(key);
        if (check != null) {
            return check;
        }
        for (Check candidate : checks.values()) {
            if (candidate.name().equalsIgnoreCase(id)) {
                return candidate;
            }
        }
        return null;
    }

    public List<Check> all() {
        return Collections.unmodifiableList(new ArrayList<>(checks.values()));
    }

    public List<TickCheck> tickChecks() {
        return tickChecks;
    }

    public long enabledCount() {
        return checks.values().stream().filter(Check::enabled).count();
    }
}
