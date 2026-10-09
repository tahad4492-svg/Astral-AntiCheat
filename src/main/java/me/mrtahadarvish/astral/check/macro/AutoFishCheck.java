package me.mrtahadarvish.astral.check.macro;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.MathUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.ArrayDeque;
import java.util.Deque;

import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * AFK fishing macros react to every bite with (almost) the same delay. Humans do not.
 */
public final class AutoFishCheck extends Check {

    public AutoFishCheck(AstralAntiCheat plugin) {
        super(plugin, "autofish", "AutoFish", CheckCategory.MACRO, "Fishing macros with robotic reaction times.");
    }

    private static final class State {
        int biteTick = -1;
        final Deque<Integer> reactions = new ArrayDeque<>();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        PlayerData data = data(event.getPlayer());
        if (data == null) {
            return;
        }
        State state = data.state(State.class, State::new);
        switch (event.getState()) {
            case BITE -> state.biteTick = data.clientTicks;
            case CAUGHT_FISH -> {
                if (state.biteTick < 0) {
                    return;
                }
                int reaction = data.clientTicks - state.biteTick;
                state.biteTick = -1;
                state.reactions.addLast(reaction);
                while (state.reactions.size() > 10) {
                    state.reactions.removeFirst();
                }
                if (state.reactions.size() >= 8 && shouldCheck(data)) {
                    double deviation = MathUtil.deviation(state.reactions);
                    double mean = MathUtil.mean(state.reactions);
                    if (deviation < 0.75 && mean < 15.0) {
                        flag(data, "Reaction", "avg=" + format(mean, 1) + " ticks dev=" + format(deviation, 2));
                        state.reactions.clear();
                    }
                }
            }
            case FAILED_ATTEMPT, REEL_IN, IN_GROUND, CAUGHT_ENTITY -> state.biteTick = -1;
            default -> {
            }
        }
    }
}
