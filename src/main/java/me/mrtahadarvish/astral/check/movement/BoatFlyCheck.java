package me.mrtahadarvish.astral.check.movement;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.BlockUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.BoundingBox;

import java.util.List;

import static me.mrtahadarvish.astral.data.PlayerData.since;
import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * Boats can not rise in the air (BoatFly / vehicle fly).
 * <p>
 * Only movement the player's own client wrote is judged: Paper fires PlayerMoveEvent for a
 * passenger only from the client's vehicle move packet. Boats a plugin moves (battle bus, rides,
 * no gravity, teleports) are therefore never flagged. When blocking, the boat is put back where
 * it was last supported - nobody is thrown out of it.
 */
public final class BoatFlyCheck extends Check {

    public BoatFlyCheck(AstralAntiCheat plugin) {
        super(plugin, "boatfly", "BoatFly", CheckCategory.MOVEMENT, "Flying with a boat.");
    }

    private static final class State {
        int boatId = -1;
        double lastY;
        int riseTicks;
        int serverMoved = -100_000;
        Location supported;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!(player.getVehicle() instanceof Boat boat)) {
            return;
        }
        List<Entity> passengers = boat.getPassengers();
        if (passengers.isEmpty() || passengers.getFirst() != player) {
            return; // only the driver steers the boat
        }
        PlayerData data = data(player);
        if (!shouldCheck(data)) {
            return;
        }
        State state = data.state(State.class, State::new);
        Location to = event.getTo();
        if (state.boatId != boat.getEntityId()) {
            // first packet in this boat: "from" is still where the player stood before
            state.boatId = boat.getEntityId();
            state.lastY = to.getY();
            state.riseTicks = 0;
            state.supported = null;
            return;
        }
        double deltaY = to.getY() - state.lastY;
        state.lastY = to.getY();

        World world = boat.getWorld();
        BoundingBox box = boat.getBoundingBox();
        boolean pushedUp = boat.getVelocity().getY() > 0.05;
        if (!boat.hasGravity() || pushedUp || Math.abs(deltaY) > 3.0) {
            state.serverMoved = PlayerData.now(); // a plugin moves this boat
        }
        if (!BlockUtil.isLoaded(world, box) || since(state.serverMoved) < 40 || since(data.lastTeleport) < 20
                || since(data.lastExplosion) < 40 || since(data.lastPiston) < 40 || bouncy(world, box)) {
            state.riseTicks = 0;
            return;
        }
        // box right under the boat (must not overlap the boat itself, boats are collidable)
        BoundingBox below = new BoundingBox(box.getMinX() - 0.1, box.getMinY() - 0.4, box.getMinZ() - 0.1,
                box.getMaxX() + 0.1, box.getMinY() - 0.001, box.getMaxZ() + 0.1);
        boolean supported = world.hasCollisionsIn(below) || touchesFluid(world, box.clone().expand(0.1, 0.6, 0.1));
        if (supported) {
            state.supported = boat.getLocation();
        }
        if (deltaY > 0.0 && !supported) {
            if (++state.riseTicks >= 4) {
                state.riseTicks = 0;
                if (flag(data, "Rise", "dy=" + format(deltaY)) && state.supported != null
                        && state.supported.getWorld() == world && state.supported.distanceSquared(boat.getLocation()) < 400.0) {
                    boat.setVelocity(new org.bukkit.util.Vector());
                    boat.teleport(state.supported); // passengers stay in the boat (Paper 1.21.10+)
                }
            }
        } else {
            state.riseTicks = 0;
        }
    }

    /** Slime, honey and bubble columns push boats up for real. */
    private boolean bouncy(World world, BoundingBox box) {
        for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX()); x++) {
            for (int y = (int) Math.floor(box.getMinY()) - 2; y <= (int) Math.floor(box.getMaxY()); y++) {
                for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ()); z++) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (type == Material.SLIME_BLOCK || type == Material.HONEY_BLOCK || type == Material.BUBBLE_COLUMN) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean touchesFluid(World world, BoundingBox box) {
        for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX()); x++) {
            for (int y = (int) Math.floor(box.getMinY()); y <= (int) Math.floor(box.getMaxY()); y++) {
                for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ()); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (BlockUtil.isLiquid(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
