package me.mrtahadarvish.astral.check.world;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.check.combat.CombatUtil;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.BlockUtil;
import me.mrtahadarvish.astral.util.MathUtil;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * Breaking / placing blocks further away than the block_interaction_range attribute.
 */
public final class BlockReachCheck extends Check {

    private double tolerance;

    public BlockReachCheck(AstralAntiCheat plugin) {
        super(plugin, "blockreach", "BlockReach", CheckCategory.WORLD, "Breaking or placing blocks too far away.");
    }

    @Override
    protected void onLoad() {
        tolerance = option("tolerance", 0.3);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getClass() != BlockBreakEvent.class) {
            return;
        }
        if (check(event.getPlayer(), event.getBlock(), "Break")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getClass() != BlockPlaceEvent.class) {
            return;
        }
        if (check(event.getPlayer(), event.getBlockAgainst(), "Place")) {
            event.setCancelled(true);
        }
    }

    private boolean check(Player player, Block block, String type) {
        PlayerData data = data(player);
        if (!shouldCheck(data) || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        AttributeInstance attribute = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        double range = attribute == null ? (player.getGameMode() == GameMode.CREATIVE ? 5.0 : 4.5) : attribute.getValue();
        BoundingBox box = BlockUtil.box(block);
        double best = Double.MAX_VALUE;
        for (Vector eye : CombatUtil.eyes(player, data)) {
            best = Math.min(best, MathUtil.distanceToBox(eye, box));
        }
        if (best > range + tolerance) {
            return flag(data, type, "distance=" + format(best) + " max=" + format(range));
        }
        return false;
    }
}
