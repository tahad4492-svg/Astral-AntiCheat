package me.mrtahadarvish.astral.check.player;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.check.TickCheck;
import me.mrtahadarvish.astral.data.Buffer;
import me.mrtahadarvish.astral.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;

import static me.mrtahadarvish.astral.data.PlayerData.since;
import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * When a screen opens, vanilla releases every key and tells the server (player input packet,
 * 1.21.2+). Clicking in an inventory while still holding movement keys, or while the camera
 * keeps turning, is InventoryMove / InvWalk (also used by AutoArmor and ChestStealer).
 */
public final class InventoryMoveCheck extends Check implements TickCheck {

    public InventoryMoveCheck(AstralAntiCheat plugin) {
        super(plugin, "inventorymove", "InventoryMove", CheckCategory.PLAYER, "Moving or turning while using an inventory.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event instanceof InventoryCreativeEvent || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        PlayerData data = data(player);
        if (data == null) {
            return;
        }
        data.inventoryClicksThisTick++;
        if (!shouldCheck(data) || player.getGameMode() == GameMode.CREATIVE || data.inVehicle || data.gliding
                || since(data.lastTeleport) < 20 || player.isDead()) {
            return;
        }
        Input input = player.getCurrentInput();
        boolean moving = input.isForward() || input.isBackward() || input.isLeft() || input.isRight() || input.isJump();
        Buffer buffer = data.buffer("inventorymove.input");
        if (moving) {
            if (buffer.increase() > 1.0) {
                if (flag(data, "Input", keys(input))) {
                    event.setCancelled(true);
                }
            }
        } else {
            buffer.decrease(0.25);
        }
    }

    @Override
    public void onTick(PlayerData data) {
        if (data.inventoryClicksThisTick == 0 || !shouldCheck(data) || data.skipMovement || data.inVehicle
                || since(data.lastTeleport) < 20) {
            return;
        }
        Buffer buffer = data.buffer("inventorymove.rotation");
        if (Math.abs(data.deltaYaw) > 5.0F && Math.abs(data.lastDeltaYaw) > 5.0F) {
            if (buffer.increase() > 3.0) {
                buffer.set(1.5);
                flag(data, "Rotation", "turned " + format(Math.abs(data.deltaYaw), 1) + " deg while clicking");
            }
        } else {
            buffer.decrease(0.1);
        }
    }

    private static String keys(Input input) {
        StringBuilder sb = new StringBuilder("keys=");
        if (input.isForward()) sb.append('W');
        if (input.isLeft()) sb.append('A');
        if (input.isBackward()) sb.append('S');
        if (input.isRight()) sb.append('D');
        if (input.isJump()) sb.append(" jump");
        return sb.toString();
    }
}
