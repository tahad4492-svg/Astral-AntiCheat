package me.mrtahadarvish.astral.check.world;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.data.Buffer;
import me.mrtahadarvish.astral.data.PlayerData;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * GhostHand / interacting through walls: the block has to be visible from the player's eyes.
 * Rays are cast to the center, the 6 face centers and the 8 corners of the block; if a full
 * solid block hides every one of them, the player can not see it.
 */
public final class InteractCheck extends Check {

    public InteractCheck(AstralAntiCheat plugin) {
        super(plugin, "interact", "GhostHand", CheckCategory.WORLD, "Breaking or using blocks through walls.");
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
        if (event.getClass() != BlockPlaceEvent.class || event.getBlockAgainst().equals(event.getBlockPlaced())) {
            return;
        }
        if (check(event.getPlayer(), event.getBlockAgainst(), "Place")) {
            event.setCancelled(true);
        }
    }

    private boolean check(Player player, Block block, String type) {
        PlayerData data = data(player);
        if (!shouldCheck(data) || player.getGameMode() == GameMode.SPECTATOR || data.inVehicle) {
            return false;
        }
        Vector eye = player.getEyeLocation().toVector();
        if (visible(player.getWorld(), eye, block)) {
            data.buffer("interact").decrease(0.5);
            return false;
        }
        Buffer buffer = data.buffer("interact");
        if (buffer.increase() > 1.0) {
            return flag(data, type, "block=" + block.getType().name().toLowerCase() + " hidden behind solid blocks");
        }
        return false;
    }

    private boolean visible(World world, Vector eye, Block block) {
        double x = block.getX();
        double y = block.getY();
        double z = block.getZ();
        // inside or touching the block
        if (eye.getX() >= x - 0.5 && eye.getX() <= x + 1.5 && eye.getY() >= y - 0.5 && eye.getY() <= y + 1.5
                && eye.getZ() >= z - 0.5 && eye.getZ() <= z + 1.5) {
            return true;
        }
        // the eyes are stuck inside a block (suffocating) - the client can still mine out
        if (world.getBlockAt(eye.getBlockX(), eye.getBlockY(), eye.getBlockZ()).getType().isOccluding()) {
            return true;
        }
        double[][] points = {
                {0.5, 0.5, 0.5},
                {0.5, 0.999, 0.5}, {0.5, 0.001, 0.5}, {0.001, 0.5, 0.5}, {0.999, 0.5, 0.5}, {0.5, 0.5, 0.001}, {0.5, 0.5, 0.999},
                {0.05, 0.05, 0.05}, {0.95, 0.05, 0.05}, {0.05, 0.95, 0.05}, {0.05, 0.05, 0.95},
                {0.95, 0.95, 0.05}, {0.95, 0.05, 0.95}, {0.05, 0.95, 0.95}, {0.95, 0.95, 0.95}
        };
        for (double[] point : points) {
            Vector target = new Vector(x + point[0], y + point[1], z + point[2]);
            Vector direction = target.clone().subtract(eye);
            double distance = direction.length();
            if (distance < 0.01) {
                return true;
            }
            RayTraceResult result = world.rayTraceBlocks(new Location(world, eye.getX(), eye.getY(), eye.getZ()),
                    direction.normalize(), distance + 0.05, FluidCollisionMode.NEVER, true);
            if (result == null || result.getHitBlock() == null) {
                return true;
            }
            Block hit = result.getHitBlock();
            if (hit.equals(block) || !hit.getType().isOccluding()) {
                return true;
            }
        }
        return false;
    }
}
