package me.mrtahadarvish.astral.check;

import org.bukkit.Material;

public enum CheckCategory {
    COMBAT("Combat", "&c", Material.DIAMOND_SWORD),
    MOVEMENT("Movement", "&b", Material.FEATHER),
    PLAYER("Player", "&e", Material.PLAYER_HEAD),
    WORLD("World", "&a", Material.GRASS_BLOCK),
    MACRO("Macro", "&d", Material.REPEATER);

    private final String display;
    private final String color;
    private final Material icon;

    CheckCategory(String display, String color, Material icon) {
        this.display = display;
        this.color = color;
        this.icon = icon;
    }

    public String display() {
        return display;
    }

    public String color() {
        return color;
    }

    public Material icon() {
        return icon;
    }
}
