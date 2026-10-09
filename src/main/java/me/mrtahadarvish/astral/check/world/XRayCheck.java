package me.mrtahadarvish.astral.check.world;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.BlockUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.ArrayDeque;
import java.util.Deque;

import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * X-Ray heuristics: legit miners find a few ore veins per hundred blocks of stone. X-Ray users
 * dig straight to diamonds / ancient debris. Alert only by default (staff should spectate).
 */
public final class XRayCheck extends Check {

    private int minStone;
    private double maxRatio;

    public XRayCheck(AstralAntiCheat plugin) {
        super(plugin, "xray", "XRay", CheckCategory.WORLD, "Finding far too many rare ores (x-ray).");
    }

    @Override
    protected void onLoad() {
        minStone = option("min-stone", 120);
        maxRatio = option("max-ratio", 4.5);
    }

    private static final class State {
        int stone;
        int veins;
        final Deque<int[]> recentOres = new ArrayDeque<>();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getClass() != BlockBreakEvent.class) {
            return;
        }
        PlayerData data = data(event.getPlayer());
        if (!shouldCheck(data)) {
            return;
        }
        Block block = event.getBlock();
        Material type = block.getType();
        State state = data.state(State.class, State::new);
        if (BlockUtil.isStoneLike(type)) {
            state.stone++;
        } else if (BlockUtil.isOre(type)) {
            boolean sameVein = false;
            for (int[] ore : state.recentOres) {
                if (Math.abs(ore[0] - block.getX()) <= 3 && Math.abs(ore[1] - block.getY()) <= 3 && Math.abs(ore[2] - block.getZ()) <= 3) {
                    sameVein = true;
                    break;
                }
            }
            state.recentOres.addFirst(new int[]{block.getX(), block.getY(), block.getZ()});
            while (state.recentOres.size() > 16) {
                state.recentOres.removeLast();
            }
            if (!sameVein) {
                state.veins++;
                if (state.stone >= minStone) {
                    double ratio = state.veins * 100.0 / state.stone;
                    if (ratio > maxRatio) {
                        flag(data, "Ratio", "veins=" + state.veins + " stone=" + state.stone + " ratio=" + format(ratio, 2) + "/100");
                    }
                }
            }
        }
        // keep a rolling window
        if (state.stone > 2000) {
            state.stone /= 2;
            state.veins /= 2;
        }
    }
}
