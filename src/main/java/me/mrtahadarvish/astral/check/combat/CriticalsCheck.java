package me.mrtahadarvish.astral.check.combat;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.BlockUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.BoundingBox;

import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * Critical hits need the player to fall. Packet criticals fake tiny jumps while the player is
 * really standing on the ground.
 */
public final class CriticalsCheck extends Check {

    public CriticalsCheck(AstralAntiCheat plugin) {
        super(plugin, "criticals", "Criticals", CheckCategory.COMBAT, "Dealing critical hits while standing on the ground.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !event.isCritical()) {
            return;
        }
        PlayerData data = data(player);
        if (!shouldCheck(data) || data.inLiquid || data.climbing || data.inVehicle || data.inWeb) {
            return;
        }
        BoundingBox box = player.getBoundingBox();
        if (!BlockUtil.isLoaded(player.getWorld(), box)) {
            return;
        }
        boolean ground = BlockUtil.collides(player.getWorld(), box.getMinX(), box.getMinY() - 0.001, box.getMinZ(),
                box.getMaxX(), box.getMinY(), box.getMaxZ());
        if (ground && !BlockUtil.nearCollidable(player)) {
            if (flag(data, "Ground", "fall=" + format(player.getFallDistance(), 3) + " air=" + data.airTicks)) {
                event.setCancelled(true);
            }
        }
    }
}
