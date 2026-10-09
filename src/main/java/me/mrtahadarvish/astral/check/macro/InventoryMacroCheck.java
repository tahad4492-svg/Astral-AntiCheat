package me.mrtahadarvish.astral.check.macro;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

/**
 * ChestStealer / inventory macros: a long row of shift clicks out of a container with the exact
 * same delay every time.
 */
public final class InventoryMacroCheck extends Check {

    private int minSequence;

    public InventoryMacroCheck(AstralAntiCheat plugin) {
        super(plugin, "inventorymacro", "ChestStealer", CheckCategory.MACRO, "Looting containers with a macro (chest stealer).");
    }

    @Override
    protected void onLoad() {
        minSequence = option("min-sequence", 9);
    }

    private static final class State {
        int lastClick = -10_000;
        int lastDelay = -1;
        int same;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        InventoryType type = top.getType();
        if (type == InventoryType.CRAFTING || type == InventoryType.CREATIVE || type == InventoryType.PLAYER) {
            return;
        }
        if (event.getClick() != ClickType.SHIFT_LEFT && event.getClick() != ClickType.SHIFT_RIGHT) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }
        PlayerData data = data(player);
        if (!shouldCheck(data)) {
            return;
        }
        State state = data.state(State.class, State::new);
        int delay = data.clientTicks - state.lastClick;
        state.lastClick = data.clientTicks;
        if (delay >= 1 && delay <= 6 && delay == state.lastDelay) {
            state.same++;
        } else {
            state.same = 0;
        }
        state.lastDelay = delay;
        if (state.same >= minSequence - 1) {
            state.same = 0;
            if (flag(data, "Steal", minSequence + " clicks with exactly " + delay + " tick delay")) {
                event.setCancelled(true);
            }
        }
    }
}
