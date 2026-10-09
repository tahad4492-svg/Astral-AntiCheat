package me.mrtahadarvish.astral.check.world;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;

import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * Compares the real mining time (in client ticks, so timer can not hide it) with the vanilla
 * mining time of the block for the tool, enchantments, haste, water and ground state.
 */
public final class FastBreakCheck extends Check {

    private double minRatio;

    public FastBreakCheck(AstralAntiCheat plugin) {
        super(plugin, "fastbreak", "FastBreak", CheckCategory.WORLD, "Mining blocks faster than vanilla.");
    }

    @Override
    protected void onLoad() {
        minRatio = option("min-ratio", 0.75);
    }

    private static final class State {
        int x;
        int y;
        int z;
        boolean active;
        int startTick;
        float startSpeed;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        PlayerData data = data(event.getPlayer());
        if (data == null) {
            return;
        }
        State state = data.state(State.class, State::new);
        if (event.getInstaBreak()) {
            state.active = false;
            return;
        }
        Block block = event.getBlock();
        state.x = block.getX();
        state.y = block.getY();
        state.z = block.getZ();
        state.startTick = data.clientTicks;
        state.startSpeed = block.getBreakSpeed(event.getPlayer());
        state.active = true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAbort(BlockDamageAbortEvent event) {
        PlayerData data = data(event.getPlayer());
        if (data != null) {
            data.state(State.class, State::new).active = false;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getClass() != BlockBreakEvent.class) {
            return; // fired by another plugin (tree feller, vein miner ...)
        }
        Player player = event.getPlayer();
        PlayerData data = data(player);
        if (!shouldCheck(data) || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        State state = data.state(State.class, State::new);
        Block block = event.getBlock();
        if (!state.active || state.x != block.getX() || state.y != block.getY() || state.z != block.getZ()) {
            return;
        }
        state.active = false;
        float speed = Math.max(state.startSpeed, block.getBreakSpeed(player));
        if (speed <= 0.0F || speed >= 1.0F) {
            return;
        }
        int expected = (int) Math.ceil(1.0 / speed);
        if (expected < 4) {
            return;
        }
        int elapsed = data.clientTicks - state.startTick + 1;
        double ratio = elapsed / (double) expected;
        if (ratio < minRatio) {
            if (flag(data, "Time", "ticks=" + elapsed + " vanilla=" + expected + " ratio=" + format(ratio, 2))) {
                event.setCancelled(true);
            }
        }
    }
}
