package me.mrtahadarvish.astral.listener;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.data.PlayerData;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Tracks attacks, arm swings (clicks) and digging, shared by many combat and macro checks.
 */
public final class InteractionListener implements Listener {

    private final AstralAntiCheat plugin;

    public InteractionListener(AstralAntiCheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.data().get(player);
        if (data == null) {
            return;
        }
        data.attacksThisTick++;
        data.actionsThisTick++;
        data.targetsThisTick.add(event.getAttacked().getEntityId());
        data.lastAttackTick = PlayerData.now();
        data.lastAttackClientTick = data.clientTicks;
        if (MovementProcessor.isSpear(player.getInventory().getItemInMainHand())) {
            data.lastSpear = PlayerData.now();
        }
        // a combat check flagged the player recently -> block every hit for a while
        if (PlayerData.now() < data.combatLockUntil && !data.isBypassing("killaura")) {
            event.setCancelled(true);
        }
    }

    /** Item use, block clicks and dig starts - every one of them is an "action" packet. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL || event.getAction() == Action.LEFT_CLICK_AIR) {
            return; // physical = pressure plates, left click air = fired by the arm swing itself
        }
        PlayerData data = plugin.data().get(event.getPlayer());
        if (data != null) {
            data.actionsThisTick++;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        PlayerData data = plugin.data().get(event.getPlayer());
        if (data != null) {
            data.actionsThisTick++;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerArmSwingEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        PlayerData data = plugin.data().get(player);
        if (data == null) {
            return;
        }
        data.swingsThisTick++;
        if (data.attacksThisTick > 0) {
            data.clicksThisTick++;
            return;
        }
        if (data.digging) {
            return;
        }
        // swinging at a block (mining / block hitting) is not a combat click
        double range = player.getGameMode() == org.bukkit.GameMode.CREATIVE ? 5.0 : 4.5;
        Block target = player.getTargetBlockExact((int) Math.ceil(range), FluidCollisionMode.NEVER);
        if (target != null && !target.getType().isAir()
                && target.getLocation().add(0.5, 0.5, 0.5).distanceSquared(player.getEyeLocation()) <= (range + 0.5) * (range + 0.5)) {
            return;
        }
        data.clicksThisTick++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDigStart(BlockDamageEvent event) {
        PlayerData data = plugin.data().get(event.getPlayer());
        if (data != null) {
            data.digging = !event.getInstaBreak();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDigAbort(BlockDamageAbortEvent event) {
        PlayerData data = plugin.data().get(event.getPlayer());
        if (data != null) {
            data.digging = false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        PlayerData data = plugin.data().get(event.getPlayer());
        if (data != null) {
            data.digging = false;
        }
    }
}
