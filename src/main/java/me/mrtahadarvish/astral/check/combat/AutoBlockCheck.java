package me.mrtahadarvish.astral.check.combat;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.listener.MovementProcessor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.ItemStack;

/**
 * A vanilla client throws away every attack click while an item is in use (shield up, eating,
 * drawing a bow). Attacking at the same time is AutoBlock / multi action.
 */
public final class AutoBlockCheck extends Check {

    public AutoBlockCheck(AstralAntiCheat plugin) {
        super(plugin, "autoblock", "AutoBlock", CheckCategory.COMBAT, "Attacking while blocking, eating or using an item.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        Player player = event.getPlayer();
        PlayerData data = data(player);
        if (!shouldCheck(data) || !CombatUtil.isCombatTarget(event.getAttacked())) {
            return;
        }
        String type = null;
        if (player.isBlocking()) {
            type = "Shield";
        } else if (player.hasActiveItem() && player.getActiveItemUsedTime() > 3 && player.getActiveItemRemainingTime() > 3) {
            ItemStack item = player.getActiveItem();
            if (!MovementProcessor.isSpear(item)) {
                type = "Use";
            }
        }
        if (type == null) {
            return;
        }
        if (flag(data, type, "item=" + player.getActiveItem().getType().name().toLowerCase()
                + " used=" + player.getActiveItemUsedTime())) {
            event.setCancelled(true);
        }
    }
}
