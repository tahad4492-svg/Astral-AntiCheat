package me.mrtahadarvish.astral.check.movement;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.MovementCheck;
import me.mrtahadarvish.astral.data.Buffer;
import me.mrtahadarvish.astral.data.PlayerData;

import static me.mrtahadarvish.astral.data.PlayerData.since;
import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * Ladders, vines and scaffolding have a fixed climb speed of ~0.1176 blocks per tick.
 */
public final class FastClimbCheck extends MovementCheck {

    private static final double CLIMB_SPEED = (0.2 - 0.08) * 0.98;

    public FastClimbCheck(AstralAntiCheat plugin) {
        super(plugin, "fastclimb", "FastClimb", "Climbing ladders, vines or scaffolding too fast.");
    }

    private static final class State {
        boolean wasClimbing;
    }

    @Override
    protected void handle(PlayerData data) {
        State state = data.state(State.class, State::new);
        boolean climbing = data.climbing;
        boolean wasClimbing = state.wasClimbing;
        state.wasClimbing = climbing;
        if (!climbing || !wasClimbing || exempt(data) || data.deltaY <= 0.0 || data.lastOnGround
                || data.inLiquid || since(data.lastLevitation) < 20 || since(data.lastSlime) < 20) {
            return;
        }
        // jumping next to a ladder keeps the normal jump arc
        double arc = (data.lastDeltaY - data.gravity) * 0.98;
        double allowed = Math.max(CLIMB_SPEED, arc) + 0.005;
        Buffer buffer = data.buffer("fastclimb");
        if (data.deltaY > allowed && !matchesVelocity(data, data.deltaY)) {
            if (buffer.increase() > 1.0) {
                if (flag(data, "Speed", "dy=" + format(data.deltaY) + " max=" + format(allowed))) {
                    setback(data);
                }
            }
        } else {
            buffer.decrease(0.1);
        }
    }
}
