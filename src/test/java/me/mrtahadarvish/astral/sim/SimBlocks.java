package me.mrtahadarvish.astral.sim;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Set;

/** Block shapes / properties used by the simulated world. */
public final class SimBlocks {

    public enum Shape {
        NONE, FULL, SLAB
    }

    private static final Set<String> AIR = Set.of("air", "cave_air", "void_air");
    private static final Set<String> SLABS = Set.of("stone_slab", "oak_slab", "smooth_stone_slab");
    private static final Set<String> NON_SOLID = Set.of("water", "lava", "ladder", "vine", "cobweb", "short_grass",
            "bubble_column", "kelp", "seagrass", "scaffolding");
    private static final Set<String> TRANSPARENT = Set.of("glass", "ice", "slime_block", "honey_block");

    private SimBlocks() {
    }

    private static String path(Material material) {
        return material.name().toLowerCase(Locale.ROOT);
    }

    public static boolean isAir(String path) {
        return AIR.contains(path);
    }

    public static Shape shape(String path) {
        if (AIR.contains(path) || NON_SOLID.contains(path)) {
            return Shape.NONE;
        }
        if (SLABS.contains(path)) {
            return Shape.SLAB;
        }
        return Shape.FULL;
    }

    public static Shape shape(Material material) {
        return shape(path(material));
    }

    public static boolean isSolid(String path) {
        return shape(path) != Shape.NONE;
    }

    public static boolean isOccluding(String path) {
        return shape(path) == Shape.FULL && !TRANSPARENT.contains(path);
    }

    /** Collision height of the block (0 = no collision). */
    public static double height(Material material) {
        return switch (shape(material)) {
            case FULL -> 1.0;
            case SLAB -> 0.5;
            case NONE -> 0.0;
        };
    }
}
