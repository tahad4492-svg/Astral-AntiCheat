package me.mrtahadarvish.astral.check.movement;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.BlockUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.util.BoundingBox;

import java.util.List;

import static me.mrtahadarvish.astral.data.PlayerData.since;
import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * Boats can not rise in the air (BoatFly / vehicle fly).
 */
public final class BoatFlyCheck extends Check {

    public BoatFlyCheck(AstralAntiCheat plugin) {
        super(plugin, "boatfly", "BoatFly", CheckCategory.MOVEMENT, "Flying with a boat.");
    }

    private static final class State {
        int riseTicks;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleMove(VehicleMoveEvent event) {
        Vehicle vehicle = event.getVehicle();
        if (!(vehicle instanceof Boat boat)) {
            return;
        }
        List<Entity> passengers = boat.getPassengers();
        if (passengers.isEmpty() || !(passengers.getFirst() instanceof Player player)) {
            return;
        }
        PlayerData data = data(player);
        if (!shouldCheck(data)) {
            return;
        }
        State state = data.state(State.class, State::new);
        Location from = event.getFrom();
        Location to = event.getTo();
        double deltaY = to.getY() - from.getY();
        World world = boat.getWorld();
        BoundingBox box = boat.getBoundingBox();
        if (!BlockUtil.isLoaded(world, box) || since(data.lastTeleport) < 20 || since(data.lastExplosion) < 40
                || since(data.lastPiston) < 40) {
            state.riseTicks = 0;
            return;
        }
        // box right under the boat (must not overlap the boat itself, boats are collidable)
        BoundingBox below = new BoundingBox(box.getMinX() - 0.1, box.getMinY() - 0.4, box.getMinZ() - 0.1,
                box.getMaxX() + 0.1, box.getMinY() - 0.001, box.getMaxZ() + 0.1);
        boolean supported = world.hasCollisionsIn(below) || touchesFluid(world, box.clone().expand(0.1, 0.6, 0.1));
        if (deltaY > 0.0 && !supported) {
            if (++state.riseTicks >= 4) {
                state.riseTicks = 0;
                if (flag(data, "Rise", "dy=" + format(deltaY))) {
                    boat.eject();
                    boat.teleport(from);
                }
            }
        } else {
            state.riseTicks = 0;
        }
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
