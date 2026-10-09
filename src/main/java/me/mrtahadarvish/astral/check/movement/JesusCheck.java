package me.mrtahadarvish.astral.check.movement;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.MovementCheck;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.BlockUtil;

import static me.mrtahadarvish.astral.data.PlayerData.since;
import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * Walking on water / lava. Type A: standing on top of a fluid surface. Type B: skimming the
 * surface with the feet barely in the fluid at walking speed.
 */
public final class JesusCheck extends MovementCheck {

    public JesusCheck(AstralAntiCheat plugin) {
        super(plugin, "jesus", "Jesus", "Walking on water or lava.");
    }

    private static final class State {
        int surfaceTicks;
        int skimTicks;
    }

    @Override
    protected void handle(PlayerData data) {
        State state = data.state(State.class, State::new);
        if (exempt(data) || data.swimming || since(data.lastVelocity) < 10 || since(data.lastClimb) < 5) {
            state.surfaceTicks = 0;
            state.skimTicks = 0;
            return;
        }

        // A - standing above the fluid surface with nothing solid below
        if (data.liquidBelow && !data.onGround && !data.groundLenient && Math.abs(data.deltaY) < 0.02) {
            state.surfaceTicks++;
            if (state.surfaceTicks >= 6) {
                if (BlockUtil.nearCollidable(data.player())) {
                    state.surfaceTicks = 0;
                    return;
                }
                state.surfaceTicks = 3;
                if (flag(data, "Walk", "dy=" + format(data.deltaY) + " speed=" + format(data.deltaXZ) + " clientGround=" + data.clientOnGround)) {
                    setback(data);
                }
            }
        } else {
            state.surfaceTicks = 0;
        }

        // B - feet barely in the fluid, no vertical movement, moving fast
        if (data.inLiquid && data.liquidDepth > 0.0 && data.liquidDepth < 0.3 && !data.onGround && !data.groundLenient
                && Math.abs(data.deltaY) < 0.02 && data.deltaXZ > 0.13 && since(data.lastDolphin) > 20) {
            state.skimTicks++;
            if (state.skimTicks >= 10) {
                if (BlockUtil.nearCollidable(data.player())) {
                    state.skimTicks = 0;
                    return;
                }
                state.skimTicks = 5;
                if (flag(data, "Skim", "depth=" + format(data.liquidDepth) + " speed=" + format(data.deltaXZ))) {
                    setback(data);
                }
            }
        } else {
            state.skimTicks = 0;
        }
    }
}
