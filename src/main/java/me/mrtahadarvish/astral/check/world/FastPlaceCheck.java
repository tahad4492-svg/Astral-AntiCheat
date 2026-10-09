package me.mrtahadarvish.astral.check.world;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Placing blocks too fast (FastPlace, auto bridge). Holding right click places one block every
 * 4 ticks; clicking is limited by the human hand. Perfectly timed 1-tick placing is a macro.
 */
public final class FastPlaceCheck extends Check {

    private int maxPerSecond;

    public FastPlaceCheck(AstralAntiCheat plugin) {
        super(plugin, "fastplace", "FastPlace", CheckCategory.WORLD, "Placing blocks faster than humanly possible.");
    }

    @Override
    protected void onLoad() {
        maxPerSecond = option("max-blocks-per-second", 25);
    }

    private static final class State {
        final Deque<Integer> places = new ArrayDeque<>();
        int lastPlaceTick = -1000;
        int sameDelay;
        int lastDelay = -1;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getClass() != BlockPlaceEvent.class) {
            return;
        }
        Player player = event.getPlayer();
        PlayerData data = data(player);
        if (!shouldCheck(data)) {
            return;
        }
        if (PlayerData.now() < data.placeLockUntil) {
            event.setCancelled(true);
            return;
        }
        State state = data.state(State.class, State::new);
        data.placedThisTick++;
        if (data.placedThisTick > 4) {
            if (flag(data, "Multi", "blocks=" + data.placedThisTick + " in one tick")) {
                event.setCancelled(true);
                return;
            }
        }

        state.places.addLast(data.clientTicks);
        while (!state.places.isEmpty() && data.clientTicks - state.places.peekFirst() >= 20) {
            state.places.removeFirst();
        }
        if (state.places.size() > maxPerSecond) {
            if (flag(data, "Speed", "blocks/s=" + state.places.size())) {
                event.setCancelled(true);
                return;
            }
        }

        int delay = data.clientTicks - state.lastPlaceTick;
        state.lastPlaceTick = data.clientTicks;
        if (delay >= 1 && delay <= 2 && delay == state.lastDelay) {
            if (++state.sameDelay >= 20) {
                state.sameDelay = 10;
                if (flag(data, "Robotic", "20 blocks with exactly " + delay + " tick delay")) {
                    event.setCancelled(true);
                }
            }
        } else {
            state.sameDelay = 0;
        }
        state.lastDelay = delay;
    }
}
