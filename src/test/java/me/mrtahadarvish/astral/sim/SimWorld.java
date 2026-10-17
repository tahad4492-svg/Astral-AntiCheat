package me.mrtahadarvish.astral.sim;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A voxel world with real block collisions (full blocks and slabs), vanilla style axis
 * collision + step up, and block ray tracing.
 */
public final class SimWorld {

    private final Map<Long, Material> blocks = new HashMap<>();
    private final Map<Long, Block> blockMocks = new HashMap<>();
    private final Map<Long, Double> breakSpeeds = new HashMap<>();
    public final List<SimPlayer> players = new ArrayList<>();
    public final List<Entity> entities = new ArrayList<>();
    public final World world;
    private final UUID uid = UUID.randomUUID();

    public SimWorld() {
        world = Sim.mock(World.class, invocation -> {
            Object[] a = invocation.getArguments();
            return switch (invocation.getMethod().getName()) {
                case "getUID" -> uid;
                case "getName" -> "world";
                case "hasCollisionsIn" -> hasCollisions((BoundingBox) a[0]);
                case "isChunkLoaded" -> true;
                case "getMinHeight" -> -64;
                case "getMaxHeight" -> 320;
                case "getBlockAt" -> a.length == 3
                        ? block((Integer) a[0], (Integer) a[1], (Integer) a[2])
                        : block(((Location) a[0]).getBlockX(), ((Location) a[0]).getBlockY(), ((Location) a[0]).getBlockZ());
                case "rayTraceBlocks" -> a.length >= 3 ? rayTrace(((Location) a[0]).toVector(), (Vector) a[1], (Double) a[2]) : null;
                case "getPlayers" -> {
                    List<Player> list = new ArrayList<>();
                    for (SimPlayer p : players) {
                        list.add(p.player);
                    }
                    yield list;
                }
                case "getNearbyEntities" -> {
                    if (a.length == 4 && a[0] instanceof Location center) {
                        yield nearby(center.toVector(), (Double) a[1], (Double) a[2], (Double) a[3], null);
                    }
                    if (a.length >= 1 && a[0] instanceof BoundingBox area) {
                        @SuppressWarnings("unchecked")
                        java.util.function.Predicate<Entity> filter = a.length > 1 && a[1] != null
                                ? (java.util.function.Predicate<Entity>) a[1] : e -> true;
                        List<Entity> out = new ArrayList<>();
                        for (SimPlayer p : players) {
                            if (p.player.getBoundingBox().overlaps(area) && filter.test(p.player)) {
                                out.add(p.player);
                            }
                        }
                        for (Entity entity : entities) {
                            if (entity.getBoundingBox().overlaps(area) && filter.test(entity)) {
                                out.add(entity);
                            }
                        }
                        yield out;
                    }
                    yield new ArrayList<Entity>();
                }
                case "hashCode" -> System.identityHashCode(invocation.getMock());
                case "toString" -> "SimWorld";
                default -> Mockito.RETURNS_DEFAULTS.answer(invocation);
            };
        });
    }

    /** Drops everything this world references (called when its simulation is closed). */
    void clear() {
        blocks.clear();
        blockMocks.clear();
        breakSpeeds.clear();
        players.clear();
        entities.clear();
    }

    private static long key(int x, int y, int z) {
        return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | ((long) (y + 2048) & 0xFFFL);
    }

    public void set(int x, int y, int z, Material material) {
        if (y < -64 || y > 319) {
            return;
        }
        if (material.isAir()) {
            blocks.remove(key(x, y, z));
        } else {
            blocks.put(key(x, y, z), material);
        }
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, material);
                }
            }
        }
    }

    public Material type(int x, int y, int z) {
        if (y < -64 || y > 319) {
            return Material.AIR;
        }
        return blocks.getOrDefault(key(x, y, z), Material.AIR);
    }

    public void breakSpeed(int x, int y, int z, double speed) {
        breakSpeeds.put(key(x, y, z), speed);
    }

    public Block block(int x, int y, int z) {
        return blockMocks.computeIfAbsent(key(x, y, z), k -> createBlock(x, y, z));
    }

    private Block createBlock(int x, int y, int z) {
        return Sim.mock(Block.class, invocation -> {
            Object[] a = invocation.getArguments();
            return switch (invocation.getMethod().getName()) {
                case "getType" -> type(x, y, z);
                case "getX" -> x;
                case "getY" -> y;
                case "getZ" -> z;
                case "getWorld" -> world;
                case "getLocation" -> a.length == 0 ? new Location(world, x, y, z) : null;
                case "getRelative" -> {
                    if (a.length == 3) {
                        yield block(x + (Integer) a[0], y + (Integer) a[1], z + (Integer) a[2]);
                    }
                    BlockFace face = (BlockFace) a[0];
                    int distance = a.length > 1 ? (Integer) a[1] : 1;
                    yield block(x + face.getModX() * distance, y + face.getModY() * distance, z + face.getModZ() * distance);
                }
                case "getFace" -> {
                    Block other = (Block) a[0];
                    int dx = other.getX() - x;
                    int dy = other.getY() - y;
                    int dz = other.getZ() - z;
                    for (BlockFace face : BlockFace.values()) {
                        if (face.getModX() == dx && face.getModY() == dy && face.getModZ() == dz) {
                            yield face;
                        }
                    }
                    yield null;
                }
                case "getBlockData" -> blockData(type(x, y, z));
                case "getBoundingBox" -> {
                    double height = SimBlocks.height(type(x, y, z));
                    yield height > 0.0 ? new BoundingBox(x, y, z, x + 1, y + height, z + 1) : new BoundingBox(x, y, z, x, y, z);
                }
                case "getBreakSpeed" -> (float) (double) breakSpeeds.getOrDefault(key(x, y, z), 0.05);
                case "isEmpty" -> type(x, y, z).isAir();
                case "hashCode" -> System.identityHashCode(invocation.getMock());
                case "toString" -> "Block[" + x + "," + y + "," + z + " " + type(x, y, z) + "]";
                default -> Mockito.RETURNS_DEFAULTS.answer(invocation);
            };
        });
    }

    private static final BlockData SOLID_DATA = Mockito.mock(BlockData.class, Mockito.withSettings().stubOnly());
    private static final Levelled LIQUID_DATA = Mockito.mock(Levelled.class, Mockito.withSettings().stubOnly()
            .defaultAnswer(invocation -> "getLevel".equals(invocation.getMethod().getName()) ? 0
                    : Mockito.RETURNS_DEFAULTS.answer(invocation)));

    private static BlockData blockData(Material type) {
        return type == Material.WATER || type == Material.LAVA ? LIQUID_DATA : SOLID_DATA;
    }

    // ------------------------------------------------------------------ collisions

    public List<BoundingBox> collisionBoxes(BoundingBox region) {
        List<BoundingBox> out = new ArrayList<>();
        for (int x = (int) Math.floor(region.getMinX()) - 1; x <= (int) Math.floor(region.getMaxX()); x++) {
            for (int y = (int) Math.floor(region.getMinY()) - 1; y <= (int) Math.floor(region.getMaxY()); y++) {
                for (int z = (int) Math.floor(region.getMinZ()) - 1; z <= (int) Math.floor(region.getMaxZ()); z++) {
                    double height = SimBlocks.height(type(x, y, z));
                    if (height > 0.0) {
                        BoundingBox box = new BoundingBox(x, y, z, x + 1, y + height, z + 1);
                        if (box.overlaps(region)) {
                            out.add(box);
                        }
                    }
                }
            }
        }
        return out;
    }

    public boolean hasCollisions(BoundingBox box) {
        return !collisionBoxes(box).isEmpty();
    }

    /** Vanilla style movement: collide Y, then X / Z, then try to step up. */
    public Vector collide(BoundingBox box, Vector movement, boolean onGround, double stepHeight) {
        Vector result = collideBox(box, movement);
        boolean collX = result.getX() != movement.getX();
        boolean collZ = result.getZ() != movement.getZ();
        boolean landing = result.getY() != movement.getY() && movement.getY() < 0.0;
        if (stepHeight > 0.0 && (onGround || landing) && (collX || collZ)) {
            Vector step = collideBox(box, new Vector(movement.getX(), stepHeight, movement.getZ()));
            if (horizontal(step) > horizontal(result)) {
                Vector down = collideBox(box.clone().shift(step), new Vector(0.0, -step.getY() + movement.getY(), 0.0));
                return step.add(down);
            }
        }
        return result;
    }

    private static double horizontal(Vector v) {
        return v.getX() * v.getX() + v.getZ() * v.getZ();
    }

    private Vector collideBox(BoundingBox box, Vector movement) {
        double dx = movement.getX();
        double dy = movement.getY();
        double dz = movement.getZ();
        BoundingBox region = box.clone().expand(Math.max(-dx, 0), Math.max(-dy, 0), Math.max(-dz, 0),
                Math.max(dx, 0), Math.max(dy, 0), Math.max(dz, 0)).expand(0.01);
        List<BoundingBox> boxes = collisionBoxes(region);
        BoundingBox moving = box.clone();
        dy = clip(boxes, moving, dy, 1);
        moving.shift(0, dy, 0);
        if (Math.abs(dx) < Math.abs(dz)) {
            dz = clip(boxes, moving, dz, 2);
            moving.shift(0, 0, dz);
            dx = clip(boxes, moving, dx, 0);
        } else {
            dx = clip(boxes, moving, dx, 0);
            moving.shift(dx, 0, 0);
            dz = clip(boxes, moving, dz, 2);
        }
        return new Vector(dx, dy, dz);
    }

    private static double clip(List<BoundingBox> boxes, BoundingBox moving, double delta, int axis) {
        final double eps = 1.0E-7;
        for (BoundingBox b : boxes) {
            boolean overlapX = b.getMaxX() > moving.getMinX() + eps && b.getMinX() < moving.getMaxX() - eps;
            boolean overlapY = b.getMaxY() > moving.getMinY() + eps && b.getMinY() < moving.getMaxY() - eps;
            boolean overlapZ = b.getMaxZ() > moving.getMinZ() + eps && b.getMinZ() < moving.getMaxZ() - eps;
            switch (axis) {
                case 0 -> {
                    if (overlapY && overlapZ) {
                        if (delta > 0 && b.getMinX() >= moving.getMaxX() - eps) {
                            delta = Math.min(delta, b.getMinX() - moving.getMaxX());
                        } else if (delta < 0 && b.getMaxX() <= moving.getMinX() + eps) {
                            delta = Math.max(delta, b.getMaxX() - moving.getMinX());
                        }
                    }
                }
                case 1 -> {
                    if (overlapX && overlapZ) {
                        if (delta > 0 && b.getMinY() >= moving.getMaxY() - eps) {
                            delta = Math.min(delta, b.getMinY() - moving.getMaxY());
                        } else if (delta < 0 && b.getMaxY() <= moving.getMinY() + eps) {
                            delta = Math.max(delta, b.getMaxY() - moving.getMinY());
                        }
                    }
                }
                default -> {
                    if (overlapX && overlapY) {
                        if (delta > 0 && b.getMinZ() >= moving.getMaxZ() - eps) {
                            delta = Math.min(delta, b.getMinZ() - moving.getMaxZ());
                        } else if (delta < 0 && b.getMaxZ() <= moving.getMinZ() + eps) {
                            delta = Math.max(delta, b.getMaxZ() - moving.getMinZ());
                        }
                    }
                }
            }
        }
        return delta;
    }

    // ------------------------------------------------------------------ ray tracing / entities

    public RayTraceResult rayTrace(Vector start, Vector direction, double maxDistance) {
        Vector dir = direction.clone().normalize();
        Set<Long> visited = new LinkedHashSet<>();
        List<int[]> cells = new ArrayList<>();
        for (double t = 0.0; t <= maxDistance + 0.05; t += 0.02) {
            Vector p = start.clone().add(dir.clone().multiply(t));
            int x = p.getBlockX();
            int y = p.getBlockY();
            int z = p.getBlockZ();
            if (visited.add(key(x, y, z))) {
                cells.add(new int[]{x, y, z});
            }
        }
        for (int[] c : cells) {
            double height = SimBlocks.height(type(c[0], c[1], c[2]));
            if (height <= 0.0) {
                continue;
            }
            BoundingBox box = new BoundingBox(c[0], c[1], c[2], c[0] + 1, c[1] + height, c[2] + 1);
            RayTraceResult hit = box.rayTrace(start, dir, maxDistance);
            if (hit != null) {
                return new RayTraceResult(hit.getHitPosition(), block(c[0], c[1], c[2]), hit.getHitBlockFace());
            }
        }
        return null;
    }

    public List<Entity> nearby(Vector center, double rx, double ry, double rz, Entity exclude) {
        BoundingBox area = new BoundingBox(center.getX() - rx, center.getY() - ry, center.getZ() - rz,
                center.getX() + rx, center.getY() + ry, center.getZ() + rz);
        List<Entity> out = new ArrayList<>();
        for (SimPlayer p : players) {
            if (p.player != exclude && p.player.getBoundingBox().overlaps(area)) {
                out.add(p.player);
            }
        }
        for (Entity entity : entities) {
            if (entity != exclude && entity.getBoundingBox().overlaps(area)) {
                out.add(entity);
            }
        }
        return out;
    }
}
