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
        State state = data.state(State.class, State::new);
        boolean inside = insideSolid(world, data.player().getBoundingBox());
        boolean wasInside = state.inside;
        state.inside = inside;
        if (inside && !wasInside && since(data.lastGhostBlock) > 20) {
            Buffer buffer = data.buffer("phase.inside");
            if (buffer.increase() > 1.0) {
                buffer.set(0.5);
                if (flag(data, "Inside", "moved into a solid block dist=" + format(distance))) {
                    setback(data);
                    return;
                }
            }
        }
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
        if (isSolid(world.getBlockAt(from.getBlockX(), from.getBlockY(), from.getBlockZ()))) {
            // started inside a block (suffocating, sand, ...) - the player is only getting out
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

    private static final class State {
        boolean inside;
    }

    /** True when the (slightly shrunk) player box overlaps a full solid block. */
    private static boolean insideSolid(World world, org.bukkit.util.BoundingBox box) {
        org.bukkit.util.BoundingBox shrunk = box.clone().expand(-0.05, -0.1, -0.05);
        for (int x = (int) Math.floor(shrunk.getMinX()); x <= (int) Math.floor(shrunk.getMaxX()); x++) {
            for (int y = (int) Math.floor(shrunk.getMinY()); y <= (int) Math.floor(shrunk.getMaxY()); y++) {
                for (int z = (int) Math.floor(shrunk.getMinZ()); z <= (int) Math.floor(shrunk.getMaxZ()); z++) {
                    if (isSolid(world.getBlockAt(x, y, z))) {
                        return true;
                    }
                }
            }
        }
        return false;
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
