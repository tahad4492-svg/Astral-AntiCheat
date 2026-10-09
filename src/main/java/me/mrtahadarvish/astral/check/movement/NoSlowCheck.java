package me.mrtahadarvish.astral.check.movement;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.UseEffects;
import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.MovementCheck;
import me.mrtahadarvish.astral.data.Buffer;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.listener.MovementProcessor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static me.mrtahadarvish.astral.data.PlayerData.since;
import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * Using an item (eating, blocking, drawing a bow) slows vanilla players to 20% and sneaking to
 * 30%. NoSlow cheats keep full speed.
 */
public final class NoSlowCheck extends MovementCheck {

    public NoSlowCheck(AstralAntiCheat plugin) {
        super(plugin, "noslow", "NoSlow", "Moving at full speed while using items or sneaking.");
    }

    private static final class State {
        int sneakTicks;
    }

    @Override
    protected void handle(PlayerData data) {
        State state = data.state(State.class, State::new);
        Player player = data.player();
        state.sneakTicks = data.sneaking && player.getCurrentInput().isSneak() ? state.sneakTicks + 1 : 0;

        if (exempt(data) || !data.onGround || !data.lastOnGround || !data.lastLastOnGround || data.inLiquid
                || since(data.lastLiquid) < 5 || velocityPending(data) || since(data.lastSlime) < 10
                || data.climbing || data.swimming) {
            return;
        }

        double multiplier;
        String type;
        if (player.hasActiveItem() && player.getActiveItemUsedTime() >= 5 && player.getActiveItemRemainingTime() > 3) {
            ItemStack item = player.getActiveItem();
            if (MovementProcessor.isSpear(item)) {
                return;
            }
            UseEffects effects = item.getData(DataComponentTypes.USE_EFFECTS);
            multiplier = effects != null ? effects.speedMultiplier() : 0.2;
            type = "Item";
        } else if (state.sneakTicks >= 5) {
            AttributeInstance sneak = player.getAttribute(Attribute.SNEAKING_SPEED);
            multiplier = sneak == null ? 0.3 : sneak.getValue();
            type = "Sneak";
        } else {
            return;
        }
        if (multiplier >= 0.99) {
            return;
        }

        double base = data.sprinting ? data.speedAttribute : data.speedAttribute * 1.3;
        double f = data.lastFriction;
        double momentum = data.lastDeltaXZ * data.lastLastFriction * 0.91;
        double acceleration = base * multiplier * (0.21600002 / (f * f * f));
        double limit = momentum + acceleration + 0.02;

        Buffer buffer = data.buffer("noslow");
        if (data.deltaXZ > limit && data.deltaXZ > 0.1) {
            if (buffer.increase() > 5.0) {
                if (flag(data, type, "speed=" + format(data.deltaXZ) + " limit=" + format(limit) + " mult=" + format(multiplier, 2))) {
                    setback(data);
                }
            }
        } else {
            buffer.decrease(0.25);
        }
    }
}
