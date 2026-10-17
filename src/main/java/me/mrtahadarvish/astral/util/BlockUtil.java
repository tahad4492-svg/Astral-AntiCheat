package me.mrtahadarvish.astral.util;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.EnumSet;
import java.util.Set;

public final class BlockUtil {

    private static final Set<Material> WATERLOGGABLE = EnumSet.noneOf(Material.class);
    private static final Set<Material> ALWAYS_WATER = EnumSet.of(
            Material.KELP, Material.KELP_PLANT, Material.SEAGRASS, Material.TALL_SEAGRASS, Material.BUBBLE_COLUMN);

    static {
        for (Material material : Material.values()) {
            if (material.isLegacy() || !material.isBlock()) {
                continue;
            }
            try {
                if (material.createBlockData() instanceof Waterlogged) {
                    WATERLOGGABLE.add(material);
                }
            } catch (Throwable ignored) {
                // some technical blocks cannot create block data
            }
        }
    }

    private BlockUtil() {
    }

    /** Vanilla block friction ("slipperiness"). */
    public static double friction(Material material) {
        return switch (material) {
            case ICE, PACKED_ICE, FROSTED_ICE -> 0.98;
            case BLUE_ICE -> 0.989;
            case SLIME_BLOCK -> 0.8;
            default -> 0.6;
        };
    }

    public static boolean isClimbable(Material material) {
        return Tag.CLIMBABLE.isTagged(material);
    }

    /**
     * Height of the fluid inside the block (0 - 1), or -1 if the block has no fluid.
     */
    public static double fluidHeight(Block block) {
        Material type = block.getType();
        boolean fluid;
        double height = 8.0 / 9.0;
        if (type == Material.WATER || type == Material.LAVA) {
            fluid = true;
            BlockData data = block.getBlockData();
            if (data instanceof Levelled levelled) {
                int level = levelled.getLevel();
                if (level > 0 && level < 8) {
                    height = (8 - level) / 9.0;
                }
            }
        } else if (ALWAYS_WATER.contains(type)) {
            fluid = true;
            if (type == Material.BUBBLE_COLUMN) {
                height = 1.0;
            }
        } else if (WATERLOGGABLE.contains(type)) {
            fluid = block.getBlockData() instanceof Waterlogged w && w.isWaterlogged();
        } else {
            fluid = false;
        }
        if (!fluid) {
            return -1.0;
        }
        Block above = block.getRelative(0, 1, 0);
        Material aboveType = above.getType();
        if (aboveType == Material.WATER || aboveType == Material.LAVA || ALWAYS_WATER.contains(aboveType)) {
            return 1.0;
        }
        if (WATERLOGGABLE.contains(aboveType) && above.getBlockData() instanceof Waterlogged w && w.isWaterlogged()) {
            return 1.0;
        }
        return height;
    }

    public static boolean isLiquid(Block block) {
        return fluidHeight(block) > 0.0;
    }

    /** True when every chunk touched by the box (plus a margin) is loaded. */
    public static boolean isLoaded(World world, BoundingBox box) {
        int minCX = ((int) Math.floor(box.getMinX() - 2)) >> 4;
        int maxCX = ((int) Math.floor(box.getMaxX() + 2)) >> 4;
        int minCZ = ((int) Math.floor(box.getMinZ() - 2)) >> 4;
        int maxCZ = ((int) Math.floor(box.getMaxZ() + 2)) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean collides(World world, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return world.hasCollisionsIn(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
    }

    /** Center of a block. */
    public static Vector center(Block block) {
        return new Vector(block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
    }

    /** Box of a block (falls back to a full cube for blocks without a shape). */
    public static BoundingBox box(Block block) {
        BoundingBox box = block.getBoundingBox();
        if (box.getVolume() <= 0.0) {
            return BoundingBox.of(block);
        }
        return box;
    }

    public static boolean isFullCube(Block block) {
        BoundingBox box = block.getBoundingBox();
        return box.getWidthX() >= 0.999 && box.getHeight() >= 0.999 && box.getWidthZ() >= 0.999;
    }

    public static boolean isOre(Material material) {
        return switch (material) {
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE, ANCIENT_DEBRIS, EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> true;
            default -> false;
        };
    }

    public static boolean isStoneLike(Material material) {
        return switch (material) {
            case STONE, DEEPSLATE, TUFF, NETHERRACK, BASALT, BLACKSTONE, ANDESITE, DIORITE, GRANITE,
                 COBBLED_DEEPSLATE, CALCITE, SMOOTH_BASALT -> true;
            default -> false;
        };
    }

    /**
     * Boats, shulkers, minecarts and happy ghasts have collision boxes players can stand on,
     * which makes their movement look strange.
     */
    public static boolean nearCollidable(Player player) {
        for (Entity entity : player.getNearbyEntities(1.5, 2.5, 1.5)) {
            if (isPlatform(entity)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Entities players can stand on or get pushed by like a block: boats / rafts, minecarts,
     * shulkers and happy ghasts. Minigames often build moving platforms (battle bus, airships,
     * elevators) out of invisible shulkers or boats.
     */
    public static boolean isPlatform(Entity entity) {
        String type = entity.getType().name();
        return type.contains("BOAT") || type.contains("RAFT") || type.contains("MINECART")
                || type.equals("SHULKER") || type.equals("HAPPY_GHAST");
    }

    /** True when a platform entity touches the player's box (below, around or above). */
    public static boolean touchesPlatform(Player player, BoundingBox box) {
        BoundingBox area = new BoundingBox(box.getMinX() - 0.6, box.getMinY() - 1.2, box.getMinZ() - 0.6,
                box.getMaxX() + 0.6, box.getMaxY() + 0.6, box.getMaxZ() + 0.6);
        return !player.getWorld().getNearbyEntities(area, entity -> entity != player && isPlatform(entity)).isEmpty();
    }
}
