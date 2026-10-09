package me.mrtahadarvish.astral.check.movement;

import io.papermc.paper.event.player.PlayerFailMoveEvent;
import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.MovementCheck;
import me.mrtahadarvish.astral.data.Buffer;
import me.mrtahadarvish.astral.data.PlayerData;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import static me.mrtahadarvish.astral.data.PlayerData.since;
import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * Moving through solid blocks (Phase / NoClip / VClip). Type A ray traces the movement of the
 * feet and of the eyes - both passing through a full solid block is impossible. Type B uses
 * Paper's own movement validation (moved wrongly / clipped into block).
 */
public final class PhaseCheck extends MovementCheck {

    public PhaseCheck(AstralAntiCheat plugin) {
        super(plugin, "phase", "Phase", "Moving through solid blocks (phase / noclip / vclip).");
    }

    @Override
    protected void handle(PlayerData data) {
        if (exempt(data) || since(data.lastPiston) < 40) {
            return;
        }
        double distance = Math.sqrt(data.deltaXZ * data.deltaXZ + data.deltaY * data.deltaY);
        if (distance < 0.1 || (data.deltaXZ > 0.8 && Math.abs(data.deltaY) < 0.6)) {
            return;
        }
        World world = data.player().getWorld();
        Vector feetFrom = new Vector(data.lastX, data.lastY + 0.1, data.lastZ);
        Vector feetTo = new Vector(data.x, data.y + 0.1, data.z);
        Vector eyeFrom = new Vector(data.lastX, data.lastY + data.eyeHeight, data.lastZ);
        Vector eyeTo = new Vector(data.x, data.y + data.eyeHeight, data.z);
        Block feet = blocked(world, feetFrom, feetTo);
        if (feet == null) {
            return;
        }
        Block eye = blocked(world, eyeFrom, eyeTo);
        if (eye == null) {
            return;
        }
        if (flag(data, "Clip", "dist=" + format(distance) + " through=" + feet.getType().name().toLowerCase())) {
            setback(data);
        }
    }

    /** Returns the full solid block between the two points, or null when the path is clear. */
    private Block blocked(World world, Vector from, Vector to) {
        if (isSolid(world.getBlockAt(from.getBlockX(), from.getBlockY(), from.getBlockZ()))
                || isSolid(world.getBlockAt(to.getBlockX(), to.getBlockY(), to.getBlockZ()))) {
            // already inside a block (suffocating, sand, ...) - not a phase
            return null;
        }
        Vector direction = to.clone().subtract(from);
        double length = direction.length();
        if (length < 1.0E-4) {
            return null;
        }
        RayTraceResult result = world.rayTraceBlocks(new Location(world, from.getX(), from.getY(), from.getZ()),
                direction.normalize(), length, FluidCollisionMode.NEVER, true);
        if (result == null || result.getHitBlock() == null) {
            return null;
        }
        Block hit = result.getHitBlock();
        return isSolid(hit) ? hit : null;
    }

    private static boolean isSolid(Block block) {
        return block.getType().isOccluding() && block.getType().isSolid();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFailMove(PlayerFailMoveEvent event) {
        if (event.isAllowed()) {
            return;
        }
        PlayerData data = data(event.getPlayer());
        if (!shouldCheck(data) || since(data.lastTeleport) < 40 || since(data.lastPiston) < 40
                || since(data.lastGhostBlock) < 40 || data.inVehicle) {
            return;
        }
        PlayerFailMoveEvent.FailReason reason = event.getFailReason();
        if (reason != PlayerFailMoveEvent.FailReason.CLIPPED_INTO_BLOCK && reason != PlayerFailMoveEvent.FailReason.MOVED_WRONGLY) {
            return;
        }
        Buffer buffer = data.buffer("phase.vanilla");
        if (buffer.increase() > 6.0) {
            buffer.set(3.0);
            flag(data, "Vanilla", "reason=" + reason.name().toLowerCase());
        }
    }
}
