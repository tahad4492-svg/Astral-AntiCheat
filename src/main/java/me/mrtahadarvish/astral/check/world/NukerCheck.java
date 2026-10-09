package me.mrtahadarvish.astral.check.world;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.data.Buffer;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.BlockUtil;
import me.mrtahadarvish.astral.util.MathUtil;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;

import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * Nuker: vanilla can break at most one block per client tick, only the block the crosshair is
 * on, and only a limited amount per second.
 */
public final class NukerCheck extends Check {

    private int maxPerSecond;
    private double maxAngle;

    public NukerCheck(AstralAntiCheat plugin) {
        super(plugin, "nuker", "Nuker", CheckCategory.WORLD, "Breaking many blocks at once or blocks the player is not looking at.");
    }

    @Override
    protected void onLoad() {
        maxPerSecond = option("max-blocks-per-second", 22);
        maxAngle = option("max-angle", 75.0);
    }

    private static final class State {
        final Deque<Integer> breaks = new ArrayDeque<>();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getClass() != BlockBreakEvent.class) {
            return;
        }
        Player player = event.getPlayer();
        PlayerData data = data(player);
        if (!shouldCheck(data)) {
            return;
        }
        Block block = event.getBlock();
        data.brokenThisTick++;
        if (data.brokenThisTick > 1) {
            if (flag(data, "Multi", "blocks=" + data.brokenThisTick + " in one tick")) {
                event.setCancelled(true);
                return;
            }
        }

        State state = data.state(State.class, State::new);
        state.breaks.addLast(data.clientTicks);
        while (!state.breaks.isEmpty() && data.clientTicks - state.breaks.peekFirst() >= 20) {
            state.breaks.removeFirst();
        }
        if (state.breaks.size() > maxPerSecond) {
            if (flag(data, "Speed", "blocks/s=" + state.breaks.size())) {
                event.setCancelled(true);
                return;
            }
        }

        Vector eye = player.getEyeLocation().toVector();
        Vector toBlock = BlockUtil.center(block).subtract(eye);
        if (toBlock.lengthSquared() > 2.25) {
            double angle = MathUtil.angle(player.getEyeLocation().getDirection(), toBlock);
            Buffer buffer = data.buffer("nuker.angle");
            if (angle > maxAngle) {
                if (buffer.increase() > 2.0) {
                    if (flag(data, "Angle", "angle=" + format(angle, 1))) {
                        event.setCancelled(true);
                    }
                }
            } else {
                buffer.decrease(0.5);
            }
        }
    }
}
