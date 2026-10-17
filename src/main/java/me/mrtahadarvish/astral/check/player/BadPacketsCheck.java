package me.mrtahadarvish.astral.check.player;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.check.PacketOrder;
import me.mrtahadarvish.astral.data.Buffer;
import me.mrtahadarvish.astral.data.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Packets in an order a vanilla client never sends.
 * <ul>
 *   <li>Post - item use, block clicks, digging and entity interactions are all handled before the
 *   client moves, so receiving them after the movement packet of the same tick means silent
 *   rotations (auto pot looking down, auto crystal, aura interactions, packet macros).</li>
 *   <li>Multi - a vanilla client sends at most one movement packet per tick. More than one means
 *   extra rotation packets (rotate, act, rotate back) or held back movement released at once
 *   (blink / fake lag).</li>
 * </ul>
 */
public final class BadPacketsCheck extends Check {

    public BadPacketsCheck(AstralAntiCheat plugin) {
        super(plugin, "badpackets", "BadPackets", CheckCategory.PLAYER, "Packets sent in an order no vanilla client uses.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        PlayerData data = data(event.getPlayer());
        if (data == null) {
            return;
        }
        // Paper only fires the event for a real change (1/16 block or 10 degrees), so tiny
        // jitter packets are never counted
        int moves = ++data.movesThisTick;
        if (!shouldCheck(data) || PacketOrder.exempt(data)) {
            return;
        }
        Buffer buffer = data.buffer("badpackets.multi");
        if (moves == 1) {
            buffer.decrease(0.02);
            return;
        }
        if (buffer.increase() > 2.0) {
            buffer.set(1.0);
            flag(data, "Multi", moves + " movement packets in one tick (extra rotations / blink)");
        }
    }

    // not ignoreCancelled: right clicks on air (potions, pearls) are fired already "cancelled"
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action == Action.PHYSICAL || action == Action.LEFT_CLICK_AIR
                || (action == Action.RIGHT_CLICK_BLOCK && event.isBlockInHand())) {
            return; // block placing is handled by Scaffold (Post)
        }
        if (check(data(event.getPlayer()), action == Action.LEFT_CLICK_BLOCK ? "Dig" : "Use")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (check(data(event.getPlayer()), "Interact")) {
            event.setCancelled(true);
        }
    }

    /** Returns true when the action should be cancelled (block mode only). */
    private boolean check(PlayerData data, String what) {
        if (!shouldCheck(data)) {
            return false;
        }
        Buffer buffer = data.buffer("badpackets.post");
        if (PacketOrder.isPost(data)) {
            if (buffer.increase() > 1.0) {
                buffer.set(0.5);
                return flag(data, "Post" + what, what.toLowerCase() + " sent after the movement packet (silent rotations)");
            }
        } else {
            buffer.decrease(0.05);
        }
        return false;
    }
}
