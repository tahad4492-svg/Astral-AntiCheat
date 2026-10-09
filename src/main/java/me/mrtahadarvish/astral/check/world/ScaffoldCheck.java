package me.mrtahadarvish.astral.check.world;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.check.TickCheck;
import me.mrtahadarvish.astral.check.combat.CombatUtil;
import me.mrtahadarvish.astral.data.Buffer;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.BlockUtil;
import me.mrtahadarvish.astral.util.MathUtil;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Scaffold / auto bridge / tower:
 * <ul>
 *   <li>AirPlace - placing against air</li>
 *   <li>Face - clicking a block face that faces away from the player (impossible to see)</li>
 *   <li>Rotation - the crosshair never pointed at the clicked block (checked on the next tick
 *       with every rotation between the two ticks)</li>
 * </ul>
 */
public final class ScaffoldCheck extends Check implements TickCheck {

    public ScaffoldCheck(AstralAntiCheat plugin) {
        super(plugin, "scaffold", "Scaffold", CheckCategory.WORLD, "Automatic block placing (scaffold, tower, airplace).");
    }

    private record Pending(List<Vector> eyes, BoundingBox box, double range, float yaw, float pitch, float previousYaw, float previousPitch) {
    }

    private static final class State {
        final List<Pending> pending = new ArrayList<>();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getClass() != BlockPlaceEvent.class) {
            return;
        }
        Player player = event.getPlayer();
        PlayerData data = data(player);
        if (!shouldCheck(data) || player.getGameMode() == GameMode.SPECTATOR || data.inVehicle) {
            return;
        }
        Block against = event.getBlockAgainst();
        Block placed = event.getBlockPlaced();
        if (against.equals(placed)) {
            return;
        }

        if (against.getType().isAir()) {
            Buffer buffer = data.buffer("scaffold.air");
            if (buffer.increase() > 1.0 && fail(data, "AirPlace", "placed against " + against.getType().name().toLowerCase())) {
                event.setCancelled(true);
            }
            return;
        }

        BlockFace face = against.getFace(placed);
        List<Vector> eyes = CombatUtil.eyes(player, data);
        if (face != null && BlockUtil.isFullCube(against) && !faceVisible(eyes, BlockUtil.box(against), face)) {
            if (fail(data, "Face", "clicked the " + face.name().toLowerCase() + " face from behind")) {
                event.setCancelled(true);
                return;
            }
        }

        AttributeInstance attribute = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        double range = (attribute == null ? 4.5 : attribute.getValue()) + 1.0;
        State state = data.state(State.class, State::new);
        if (state.pending.size() < 10) {
            state.pending.add(new Pending(eyes, BlockUtil.box(against).expand(0.1), range,
                    player.getLocation().getYaw(), player.getLocation().getPitch(), data.lastYaw, data.lastPitch));
        }
    }

    @Override
    public void onTick(PlayerData data) {
        State state = data.state(State.class, State::new);
        if (state.pending.isEmpty()) {
            return;
        }
        List<Pending> pending = new ArrayList<>(state.pending);
        state.pending.clear();
        if (!shouldCheck(data) || PlayerData.since(data.lastTeleport) < 10) {
            return;
        }
        Buffer buffer = data.buffer("scaffold.rotation");
        for (Pending place : pending) {
            if (looked(place, data.yaw, data.pitch)) {
                buffer.decrease(0.5);
                continue;
            }
            if (buffer.increase() > 2.0) {
                buffer.set(1.0);
                fail(data, "Rotation", "yaw=" + MathUtil.format(data.yaw, 1) + " pitch=" + MathUtil.format(data.pitch, 1));
            }
        }
    }

    private boolean looked(Pending place, float endYaw, float endPitch) {
        float[][] rotations = new float[7][];
        rotations[0] = new float[]{place.previousYaw, place.previousPitch};
        rotations[1] = new float[]{place.yaw, place.pitch};
        rotations[2] = new float[]{endYaw, endPitch};
        for (int i = 1; i <= 4; i++) {
            float t = i / 5.0F;
            rotations[2 + i] = new float[]{MathUtil.lerpAngle(place.yaw, endYaw, t), place.pitch + (endPitch - place.pitch) * t};
        }
        for (float[] rotation : rotations) {
            Vector direction = MathUtil.direction(rotation[0], rotation[1]);
            for (Vector eye : place.eyes) {
                if (CombatUtil.rayHits(eye, direction, place.range, place.box)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The eye must be on the outer side of the clicked face. */
    private boolean faceVisible(List<Vector> eyes, BoundingBox box, BlockFace face) {
        double tolerance = 0.03;
        for (Vector eye : eyes) {
            boolean visible = switch (face) {
                case UP -> eye.getY() >= box.getMaxY() - tolerance;
                case DOWN -> eye.getY() <= box.getMinY() + tolerance;
                case NORTH -> eye.getZ() <= box.getMinZ() + tolerance;
                case SOUTH -> eye.getZ() >= box.getMaxZ() - tolerance;
                case WEST -> eye.getX() <= box.getMinX() + tolerance;
                case EAST -> eye.getX() >= box.getMaxX() - tolerance;
                default -> true;
            };
            if (visible) {
                return true;
            }
        }
        return false;
    }

    private boolean fail(PlayerData data, String type, String debug) {
        boolean mitigate = flag(data, type, debug);
        if (mitigate) {
            data.placeLockUntil = PlayerData.now() + 20;
        }
        return mitigate;
    }
}
