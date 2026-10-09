package me.mrtahadarvish.astral.check.combat;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.AttackRange;
import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** Shared math for combat checks (reach, hitbox, walls). */
public final class CombatUtil {

    private CombatUtil() {
    }

    public static boolean isCombatTarget(Entity entity) {
        return entity instanceof LivingEntity || entity instanceof EnderCrystal;
    }

    /** Vanilla attack range: the entity_interaction_range attribute or the item's attack_range (spears). */
    public static double attackRange(Player player, Entity target) {
        AttributeInstance attribute = player.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        double range = attribute == null ? (player.getGameMode() == GameMode.CREATIVE ? 5.0 : 3.0) : attribute.getValue();
        AttackRange attackRange = attackRange(player);
        if (attackRange != null) {
            double max = player.getGameMode() == GameMode.CREATIVE ? attackRange.maxCreativeReach() : attackRange.maxReach();
            if (!(target instanceof Player)) {
                max *= Math.max(1.0, attackRange.mobFactor());
            }
            range = Math.max(range, max);
        }
        return range;
    }

    public static double hitboxMargin(Player player) {
        AttackRange attackRange = attackRange(player);
        return attackRange == null ? 0.0 : attackRange.hitboxMargin();
    }

    private static AttackRange attackRange(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            return null;
        }
        try {
            return hand.getData(DataComponentTypes.ATTACK_RANGE);
        } catch (Throwable throwable) {
            return null;
        }
    }

    /**
     * Possible eye positions of the attacker: the client attacks from the camera of the last
     * rendered frame, which is between the last two client ticks.
     */
    public static List<Vector> eyes(Player player, PlayerData data) {
        List<Vector> eyes = new ArrayList<>(3);
        double eye = player.getEyeHeight();
        eyes.add(player.getEyeLocation().toVector());
        if (data.hasPosition) {
            eyes.add(new Vector(data.x, data.y + eye, data.z));
            eyes.add(new Vector(data.lastX, data.lastY + eye, data.lastZ));
        }
        return eyes;
    }

    /**
     * Lag compensated boxes of the target: where the attacker could have seen the target,
     * based on the attacker's ping.
     */
    public static List<BoundingBox> targetBoxes(AstralAntiCheat plugin, Entity target, PlayerData attacker) {
        int ping = Math.min(Math.max(0, attacker.ping()), plugin.settings().maxPingCompensation);
        int compensation = (int) Math.ceil(ping / 50.0) + 4;
        int now = Bukkit.getCurrentTick();
        List<BoundingBox> boxes = new ArrayList<>();
        if (target instanceof Player targetPlayer) {
            PlayerData targetData = plugin.data().get(targetPlayer);
            if (targetData != null) {
                boxes.addAll(targetData.history.between(now - compensation - 2, now));
            }
            boxes.add(targetPlayer.getBoundingBox());
            return boxes;
        }
        BoundingBox box = target.getBoundingBox().clone();
        double speed = target.getVelocity().length();
        box.expand(Math.min(1.2, 0.1 + speed * compensation));
        boxes.add(box);
        return boxes;
    }

    public static boolean rayHits(Vector eye, Vector direction, double range, BoundingBox box) {
        if (box.contains(eye)) {
            return true;
        }
        RayTraceResult result = box.rayTrace(eye, direction, range);
        return result != null;
    }

    /** True when every line from the eye to the target passes through a full solid block. */
    public static boolean allBlocked(World world, Vector eye, List<BoundingBox> boxes) {
        for (BoundingBox box : boxes) {
            Vector center = box.getCenter();
            double y = center.getY();
            Vector[] points = {
                    center,
                    new Vector(center.getX(), box.getMaxY() - 0.15, center.getZ()),
                    new Vector(center.getX(), box.getMinY() + 0.15, center.getZ()),
                    new Vector(box.getMinX() + 0.05, y, box.getMinZ() + 0.05),
                    new Vector(box.getMaxX() - 0.05, y, box.getMaxZ() - 0.05),
                    new Vector(box.getMinX() + 0.05, y, box.getMaxZ() - 0.05),
                    new Vector(box.getMaxX() - 0.05, y, box.getMinZ() + 0.05)
            };
            for (Vector point : points) {
                if (!blocked(world, eye, point)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean blocked(World world, Vector from, Vector to) {
        Vector direction = to.clone().subtract(from);
        double distance = direction.length();
        if (distance < 0.01) {
            return false;
        }
        RayTraceResult result = world.rayTraceBlocks(new Location(world, from.getX(), from.getY(), from.getZ()),
                direction.normalize(), distance, FluidCollisionMode.NEVER, true);
        if (result == null || result.getHitBlock() == null) {
            return false;
        }
        Block block = result.getHitBlock();
        return block.getType().isOccluding() && block.getType().isSolid();
    }
}
