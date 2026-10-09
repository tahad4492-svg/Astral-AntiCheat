package me.mrtahadarvish.astral.check.movement;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.MovementCheck;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.Clock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * A vanilla client runs exactly 20 ticks per second and sends one "tick end" packet per tick.
 * Timer cheats speed the game up. Astral keeps a balance: +50ms per client tick, minus the real
 * time that passed. A client that runs too fast builds up a positive balance.
 */
public final class TimerCheck extends MovementCheck {

    private static final long TICK = 50_000_000L;

    private long maxBalance;
    private long maxLagBalance;

    public TimerCheck(AstralAntiCheat plugin) {
        super(plugin, "timer", "Timer", "Running the game faster than 20 ticks per second.");
    }

    @Override
    protected void onLoad() {
        maxBalance = (long) option("max-balance", 150.0) * 1_000_000L;
        maxLagBalance = (long) option("max-lag-balance", 3000.0) * 1_000_000L;
    }

    private static final class State {
        long lastTick;
        long balance;
        long windowStart;
        int windowTicks;
        double lastSpeed = 1.0;
        boolean suspect;
        long suspectBalance;
        long suspectSince;
    }

    @Override
    protected void handle(PlayerData data) {
        State state = data.state(State.class, State::new);
        long now = Clock.nanos();
        if (state.lastTick == 0L) {
            state.lastTick = now;
            state.windowStart = now;
            return;
        }
        long elapsed = now - state.lastTick;
        state.lastTick = now;

        state.windowTicks++;
        if (now - state.windowStart >= 1_000_000_000L) {
            state.lastSpeed = state.windowTicks / ((now - state.windowStart) / 1_000_000_000.0) / 20.0;
            state.windowTicks = 0;
            state.windowStart = now;
        }

        if (data.inVehicle || data.player().isDead()) {
            state.balance = 0L;
            return;
        }

        state.balance += TICK - elapsed;
        if (state.balance < -maxLagBalance) {
            state.balance = -maxLagBalance;
        }
        // stored lag slowly disappears so it cannot be saved for later
        if (state.balance < 0L) {
            state.balance = Math.min(0L, state.balance + 2_500_000L);
        }

        if (state.balance > maxBalance) {
            if (!state.suspect) {
                // could be a one time catch-up after the ping dropped - wait for more
                state.suspect = true;
                state.suspectBalance = state.balance;
                state.suspectSince = now;
            } else if (state.balance - state.suspectBalance > maxBalance / 2) {
                double ahead = state.balance / 1_000_000.0;
                state.balance = 0L;
                state.suspect = false;
                if (flag(data, "Balance", "ahead=" + format(ahead, 1) + "ms speed=" + format(state.lastSpeed * 100.0, 1) + "%")) {
                    setback(data);
                }
            } else if (now - state.suspectSince > 5_000_000_000L) {
                // the balance stopped growing: it was lag, not timer
                state.balance = 0L;
                state.suspect = false;
            }
        } else if (state.suspect && now - state.suspectSince > 5_000_000_000L) {
            state.suspect = false;
        }
    }

    private void reset(PlayerData data) {
        if (data == null) {
            return;
        }
        State state = data.state(State.class, State::new);
        state.lastTick = 0L;
        state.balance = 0L;
        state.suspect = false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        reset(data(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        reset(data(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        reset(data(event.getPlayer()));
    }
}
