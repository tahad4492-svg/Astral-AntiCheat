package me.mrtahadarvish.astral.sim;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A non player entity the server moves: a battle bus seat (armor stand), a shulker or boat used
 * as a moving platform, a boat driven by a player ... Its box is width x height around x/y/z.
 */
public final class SimEntity {

    private static int nextId = 70_000;

    public final Entity entity;
    public final EntityType type;
    public final int id = nextId++;
    private final UUID uuid = UUID.randomUUID();
    private final SimWorld world;
    public double x, y, z;
    public final double width, height;
    public boolean gravity = true;
    public Vector velocity = new Vector();
    public final List<Entity> passengers = new ArrayList<>();
    public final List<Location> teleports = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public <T extends Entity> SimEntity(SimWorld world, Class<T> api, EntityType type, double x, double y, double z,
                                        double width, double height) {
        this.world = world;
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.width = width;
        this.height = height;
        this.entity = Sim.mock(api, invocation -> {
            Object[] a = invocation.getArguments();
            return switch (invocation.getMethod().getName()) {
                case "getType" -> type;
                case "getEntityId" -> id;
                case "getUniqueId" -> uuid;
                case "getWorld" -> world.world;
                case "getLocation" -> a.length == 0 ? new Location(world.world, this.x, this.y, this.z) : null;
                case "getBoundingBox" -> box();
                case "getVelocity" -> velocity.clone();
                case "setVelocity" -> {
                    velocity = ((Vector) a[0]).clone();
                    yield null;
                }
                case "hasGravity" -> gravity;
                case "getPassengers" -> new ArrayList<>(passengers);
                case "isValid", "isEmpty" -> invocation.getMethod().getName().equals("isValid") || passengers.isEmpty();
                case "teleport" -> {
                    Location to = (Location) a[0];
                    teleports.add(to.clone());
                    this.x = to.getX();
                    this.y = to.getY();
                    this.z = to.getZ();
                    yield true;
                }
                case "eject" -> {
                    passengers.clear();
                    yield true;
                }
                case "hashCode" -> System.identityHashCode(invocation.getMock());
                case "equals" -> invocation.getMock() == a[0];
                case "toString" -> "SimEntity[" + type + "]";
                default -> Mockito.RETURNS_DEFAULTS.answer(invocation);
            };
        });
        world.entities.add(entity);
    }

    public BoundingBox box() {
        double half = width / 2.0;
        return new BoundingBox(x - half, y, z - half, x + half, y + height, z + half);
    }

    public void move(double dx, double dy, double dz) {
        x += dx;
        y += dy;
        z += dz;
    }

    public void remove() {
        world.entities.remove(entity);
    }
}
