package me.mrtahadarvish.astral.check.macro;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.check.PacketOrder;
import me.mrtahadarvish.astral.data.Buffer;
import me.mrtahadarvish.astral.data.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemHeldEvent;

/**
 * Silent swap / swap macros (auto weapon, mace swap, shield breaker, auto pot, auto crystal).
 * <p>
 * A vanilla client only sends a slot change at the start of a tick or right before an action,
 * because number keys are read before attacks and item use, and mouse-wheel scrolling happens
 * between ticks. So a slot change that comes:
 * <ul>
 *   <li>after an attack / item use / block click in the same tick ("SilentSwap": switch, hit,
 *       switch back), or</li>
 *   <li>after the movement packet of the tick ("Post")</li>
 * </ul>
 * can only come from a modified client. More than two slot changes in one tick is "Spam".
 */
public final class HotbarMacroCheck extends Check {

    public HotbarMacroCheck(AstralAntiCheat plugin) {
        super(plugin, "hotbarmacro", "SilentSwap", CheckCategory.MACRO, "Silent hotbar swapping (swap macros, auto weapon, mace swap).");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        PlayerData data = data(event.getPlayer());
        if (data == null) {
            return;
        }
        data.slotChangesThisTick++;
        if (!shouldCheck(data)) {
            return;
        }
        String slots = event.getPreviousSlot() + "->" + event.getNewSlot();
        if (data.actionsThisTick > 0) {
            Buffer buffer = data.buffer("hotbarmacro.swap");
            if (buffer.increase() > 1.0) {
                buffer.set(0.5);
                flag(data, "SilentSwap", "slot " + slots + " after " + data.actionsThisTick + " action(s) in the same tick");
            }
        } else if (PacketOrder.isPost(data)) {
            Buffer buffer = data.buffer("hotbarmacro.post");
            if (buffer.increase() > 1.0) {
                buffer.set(0.5);
                flag(data, "Post", "slot " + slots + " sent after the movement packet");
            }
        }
        if (data.slotChangesThisTick > 2) {
            Buffer buffer = data.buffer("hotbarmacro.spam");
            if (buffer.increase() > 1.0) {
                buffer.set(0.5);
                flag(data, "Spam", "slot changes=" + data.slotChangesThisTick + " in one tick");
            }
        }
    }
}
