package me.mrtahadarvish.astral.check.movement;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.MovementCheck;
import me.mrtahadarvish.astral.data.PlayerData;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.entity.Player;

import static me.mrtahadarvish.astral.data.PlayerData.since;
import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * Uses the key inputs the client sends since 1.21.2: vanilla stops sprinting without forward
 * input (OmniSprint) and when the food level is 6 or lower.
 */
public final class SprintCheck extends MovementCheck {

    public SprintCheck(AstralAntiCheat plugin) {
        super(plugin, "sprint", "OmniSprint", "Sprinting backwards / sideways or while starving.");
    }

    private static final class State {
        int omniTicks;
        int hungerTicks;
    }

    @Override
    protected void handle(PlayerData data) {
        State state = data.state(State.class, State::new);
        Player player = data.player();
        if (exempt(data) || data.inLiquid || data.swimming || since(data.lastLiquid) < 10 || !data.sprinting) {
            state.omniTicks = 0;
            state.hungerTicks = 0;
            return;
        }
        Input input = player.getCurrentInput();
        int forward = (input.isForward() ? 1 : 0) - (input.isBackward() ? 1 : 0);
        boolean moving = input.isForward() || input.isBackward() || input.isLeft() || input.isRight();

        if (forward <= 0 && moving && data.deltaXZ > 0.15 && velocityNotPending(data)) {
            if (++state.omniTicks >= 4) {
                state.omniTicks = 0;
                if (flag(data, "Omni", "forward=" + forward + " speed=" + format(data.deltaXZ))) {
                    player.setSprinting(false);
                    setback(data);
                }
            }
        } else {
            state.omniTicks = 0;
        }

        GameMode mode = player.getGameMode();
        if (player.getFoodLevel() <= 6 && !player.getAllowFlight() && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
                && data.deltaXZ > 0.2) {
            if (++state.hungerTicks >= data.pingTicks() + 20) {
                state.hungerTicks = 0;
                if (flag(data, "Hunger", "food=" + player.getFoodLevel() + " speed=" + format(data.deltaXZ))) {
                    player.setSprinting(false);
                }
            }
        } else {
            state.hungerTicks = 0;
        }
    }

    private boolean velocityNotPending(PlayerData data) {
        return !velocityPending(data);
    }
}
