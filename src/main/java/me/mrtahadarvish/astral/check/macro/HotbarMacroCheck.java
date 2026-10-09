package me.mrtahadarvish.astral.check.macro;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.data.Buffer;
import me.mrtahadarvish.astral.data.PlayerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemHeldEvent;

/**
 * Hotbar macros (auto weapon / swap-hit / auto pot macros). A vanilla client sends at most two
 * slot changes per tick and always before it attacks - never "attack, then switch back".
 */
public final class HotbarMacroCheck extends Check {

    public HotbarMacroCheck(AstralAntiCheat plugin) {
        super(plugin, "hotbarmacro", "HotbarMacro", CheckCategory.MACRO, "Hotbar swapping macros (swap-hit, auto weapon).");
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
        if (data.attacksThisTick > 0) {
            Buffer buffer = data.buffer("hotbarmacro.swap");
            if (buffer.increase() > 2.0) {
                buffer.set(1.0);
                flag(data, "SwapHit", "switched slot after attacking in the same tick");
            }
        }
        if (data.slotChangesThisTick > 2) {
            Buffer buffer = data.buffer("hotbarmacro.spam");
            if (buffer.increase() > 2.0) {
                buffer.set(1.0);
                flag(data, "Spam", "slot changes=" + data.slotChangesThisTick + " in one tick");
            }
        }
    }
}
