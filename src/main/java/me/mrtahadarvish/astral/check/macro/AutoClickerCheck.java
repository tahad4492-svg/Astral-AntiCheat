package me.mrtahadarvish.astral.check.macro;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.check.TickCheck;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.MathUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * Click analysis on client ticks (immune to network jitter):
 * <ul>
 *   <li>CPS - clicking faster than a human for a whole second</li>
 *   <li>Pattern - an auto clicker / macro with a fixed delay repeats the same few 8-click
 *       rhythms. Thresholds come from simulating human click timing on 20 TPS ticks.</li>
 * </ul>
 */
public final class AutoClickerCheck extends Check implements TickCheck {

    private int maxCps;
    private int cancelCps;
    private int maxPatterns;

    public AutoClickerCheck(AstralAntiCheat plugin) {
        super(plugin, "autoclicker", "AutoClicker", CheckCategory.MACRO, "Auto clickers and click macros.");
    }

    @Override
    protected void onLoad() {
        maxCps = option("max-cps", 22);
        cancelCps = option("cancel-cps", 30);
        maxPatterns = option("max-patterns", 9);
    }

    private static final class State {
        final Deque<Integer> delays = new ArrayDeque<>();
        int ticksSinceClick = 100;
        int newSamples;
        int highCpsTicks;
        int patternWindows;
    }

    @Override
    public void onTick(PlayerData data) {
        State state = data.state(State.class, State::new);
        state.ticksSinceClick++;
        if (data.clicksThisTick > 0) {
            if (state.ticksSinceClick <= 10) {
                add(state, state.ticksSinceClick);
            }
            for (int i = 1; i < data.clicksThisTick; i++) {
                add(state, 0);
            }
            state.ticksSinceClick = 0;
        }
        if (!shouldCheck(data)) {
            return;
        }

        int cps = data.cps();
        if (cps > maxCps) {
            if (++state.highCpsTicks >= 20) {
                state.highCpsTicks = 0;
                flag(data, "CPS", "cps=" + cps + " max=" + maxCps);
            }
        } else {
            state.highCpsTicks = Math.max(0, state.highCpsTicks - 2);
        }

        if (state.newSamples >= 50 && state.delays.size() >= 100) {
            state.newSamples = 0;
            double mean = MathUtil.mean(state.delays);
            double averageCps = mean <= 0.0 ? 0.0 : 20.0 / mean;
            if (averageCps < 8.0) {
                state.patternWindows = 0;
                return;
            }
            int patterns = distinctPatterns(state.delays);
            // a steady human at ~20 CPS can look rhythmic on 20 TPS ticks, so ask for more proof there
            int needed = averageCps > 17.0 ? 4 : 2;
            if (patterns <= maxPatterns) {
                if (++state.patternWindows >= needed) {
                    state.patternWindows = 1;
                    flag(data, "Pattern", "patterns=" + patterns + " cps=" + format(averageCps, 1)
                            + " dev=" + format(MathUtil.deviation(state.delays), 2));
                }
            } else {
                state.patternWindows = 0;
            }
        }
    }

    /**
     * Number of different 8-click delay sequences. Any clicker with a fixed delay produces a
     * "Sturmian" sequence on 20 TPS ticks, which has at most 9 different 8-click sequences.
     * Human timing noise creates far more (simulated: 0% of human windows below 10 except
     * extremely steady 20 CPS clicking).
     */
    private static int distinctPatterns(Deque<Integer> delays) {
        Integer[] values = delays.toArray(new Integer[0]);
        Set<Long> patterns = new HashSet<>();
        for (int i = 0; i + 8 <= values.length; i++) {
            long key = 0L;
            for (int j = 0; j < 8; j++) {
                key = key * 16L + Math.min(15, values[i + j]);
            }
            patterns.add(key);
        }
        return patterns.size();
    }

    private void add(State state, int delay) {
        state.delays.addLast(delay);
        while (state.delays.size() > 100) {
            state.delays.removeFirst();
        }
        state.newSamples++;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        PlayerData data = data(event.getPlayer());
        if (shouldCheck(data) && mitigate() && data.cps() > cancelCps) {
            event.setCancelled(true);
        }
    }
}
