package me.mrtahadarvish.astral.check.macro;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * AutoTotem: after a totem pops, a human needs to open the inventory, find a totem and move it.
 * Putting a new totem in the off-hand through the inventory a few ticks after the pop is a macro.
 */
public final class AutoTotemCheck extends Check {

    private int minTicks;

    public AutoTotemCheck(AstralAntiCheat plugin) {
        super(plugin, "autototem", "AutoTotem", CheckCategory.MACRO, "Re-equipping totems faster than humanly possible.");
    }

    @Override
    protected void onLoad() {
        minTicks = option("min-ticks", 4);
    }

    private static final class State {
        int lastPop = -10_000;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player) {
            PlayerData data = data(player);
            if (data != null) {
                data.state(State.class, State::new).lastPop = data.clientTicks;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        PlayerData data = data(player);
        if (!shouldCheck(data)) {
            return;
        }
        boolean totemToOffhand = false;
        if (event.getClick() == ClickType.SWAP_OFFHAND && isTotem(event.getCurrentItem())) {
            totemToOffhand = true;
        } else if (event.getClickedInventory() instanceof PlayerInventory && event.getSlot() == 40 && isTotem(event.getCursor())) {
            totemToOffhand = true;
        }
        if (!totemToOffhand) {
            return;
        }
        int ticks = data.clientTicks - data.state(State.class, State::new).lastPop;
        if (ticks <= minTicks) {
            if (flag(data, "Speed", "totem re-equipped " + ticks + " ticks after pop")) {
                event.setCancelled(true);
            }
        }
    }

    private static boolean isTotem(ItemStack item) {
        return item != null && item.getType() == Material.TOTEM_OF_UNDYING;
    }
}
