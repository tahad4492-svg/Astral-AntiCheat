package me.mrtahadarvish.astral.manager;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a check + type into what the player is most likely using ("KillAura (silent rotations)",
 * "Auto attribute swap" ...). Shown in the compact staff alerts. Every name can be changed in
 * messages.yml under "cheat-names".
 */
public final class CheatNames {

    private static final Map<String, String> DEFAULTS = new HashMap<>();

    static {
        put("reach", "Reach");
        put("hitbox", "KillAura / Hitbox");
        put("killaura", "KillAura");
        put("killaura.post", "KillAura (silent rotations)");
        put("killaura.multi", "Multi aura");
        put("killaura.switch", "Switch aura");
        put("killaura.noswing", "KillAura (no swing)");
        put("killaura.walls", "KillAura (through walls)");
        put("aim", "Aimbot");
        put("aim.silentaim", "Silent aim");
        put("aim.gcd", "Aimbot / aim assist");
        put("aim.robotic", "Aimbot (robotic rotations)");
        put("crystalaura", "Crystal aura");
        put("autoblock", "AutoBlock");
        put("criticals", "Criticals");
        put("velocity", "Anti knockback");
        put("mace", "Mace exploit");
        put("mace.fakefall", "Mace kill (fake fall)");

        put("flight", "Fly");
        put("flight.hover", "Fly (hover)");
        put("flight.glide", "Glide / slow fall");
        put("flight.spider", "Spider");
        put("flight.highjump", "High jump");
        put("flight.minijump", "Criticals / low hop");
        put("flight.fastfall", "Fast fall");
        put("speed", "Speed");
        put("speed.strafe", "Air strafe");
        put("nofall", "NoFall");
        put("timer", "Timer");
        put("step", "Step");
        put("jesus", "Jesus");
        put("fastclimb", "Fast ladder");
        put("elytrafly", "Elytra fly");
        put("noslow", "NoSlow");
        put("sprint", "Omni sprint");
        put("sprint.hunger", "Sprint while starving");
        put("phase", "Phase / NoClip");
        put("boatfly", "Boat fly");

        put("inventorymove", "Inventory move");
        put("inventorymove.action", "Inventory macro (auto armor / auto totem)");
        put("inventorymove.post", "Inventory macro (auto armor / auto totem)");
        put("inventorymove.combo", "Inventory macro (auto armor / auto totem)");
        put("badpackets", "Bad packets");
        put("badpackets.postuse", "Auto pot / silent rotations");
        put("badpackets.postdig", "Silent rotations");
        put("badpackets.postinteract", "Silent rotations");
        put("badpackets.multi", "Blink / fake rotations");
        put("fastbow", "Fast bow");
        put("clientbrand", "Hacked client");

        put("fastbreak", "Fast break");
        put("nuker", "Nuker");
        put("blockreach", "Block reach");
        put("interact", "Ghost hand");
        put("fastplace", "Fast place");
        put("scaffold", "Scaffold");
        put("xray", "X-Ray (suspected)");

        put("autoclicker", "Auto clicker");
        put("autototem", "Auto totem");
        put("inventorymacro", "Chest stealer");
        put("hotbarmacro", "Silent swap");
        put("hotbarmacro.spam", "Hotbar macro");
        put("hotbarmacro.attributeswap", "Auto attribute swap");
        put("hotbarmacro.stunslam", "Auto stun slam");
        put("autofish", "Auto fish");
        put("chat", "Chat spam / macro");
    }

    private static void put(String key, String name) {
        DEFAULTS.put(key, name);
    }

    private final Map<String, String> overrides = new HashMap<>();

    public void load(ConfigurationSection section) {
        overrides.clear();
        if (section == null) {
            return;
        }
        // reach: "Reach"   or   killaura: {default: "KillAura", post: "KillAura (silent rotations)"}
        for (String check : section.getKeys(false)) {
            String id = check.toLowerCase(Locale.ROOT);
            ConfigurationSection types = section.getConfigurationSection(check);
            if (types == null) {
                String value = section.getString(check);
                if (value != null) {
                    overrides.put(id, value);
                }
                continue;
            }
            for (String type : types.getKeys(false)) {
                String value = types.getString(type);
                if (value == null) {
                    continue;
                }
                String key = type.equalsIgnoreCase("default") ? id : id + "." + type.toLowerCase(Locale.ROOT);
                overrides.put(key, value);
            }
        }
    }

    /** What the player is most likely using, e.g. name("killaura", "KillAura", "Post"). */
    public String name(String checkId, String checkName, String type) {
        String id = checkId.toLowerCase(Locale.ROOT);
        String full = id + "." + type.toLowerCase(Locale.ROOT);
        String name = overrides.get(full);
        if (name == null) {
            name = DEFAULTS.get(full);
        }
        if (name == null) {
            name = overrides.get(id);
        }
        if (name == null) {
            name = DEFAULTS.get(id);
        }
        return name == null ? checkName : name;
    }
}
