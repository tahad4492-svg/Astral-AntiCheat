package me.mrtahadarvish.astral.check.movement;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.MovementCheck;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.BlockUtil;

import static me.mrtahadarvish.astral.data.PlayerData.since;
import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * Vanilla players can only step up blocks lower than their step height (0.6). Step cheats walk
 * straight up full blocks.
 */
public final class StepCheck extends MovementCheck {

    public StepCheck(AstralAntiCheat plugin) {
        super(plugin, "step", "Step", "Stepping up blocks higher than vanilla allows.");
    }

    @Override
    protected void handle(PlayerData data) {
        if (exempt(data) || !data.onGround || data.deltaY <= 0.0) {
            return;
        }
        if (data.inLiquid || since(data.lastLiquid) < 5 || data.climbing || since(data.lastClimb) < 5
                || since(data.lastSlime) < 20 || since(data.lastBed) < 20 || since(data.lastScaffolding) < 10
                || since(data.lastPowderSnow) < 10) {
            return;
        }
        double allowed = data.lastOnGround ? Math.max(data.stepHeight, maxJump(data)) : data.stepHeight;
        if (data.deltaY > allowed + 0.001 && !matchesVelocity(data, data.deltaY)) {
            if (BlockUtil.nearCollidable(data.player())) {
                return;
            }
            if (flag(data, data.lastOnGround ? "Height" : "AirStep", "dy=" + format(data.deltaY) + " max=" + format(allowed))) {
                setback(data);
            }
        }
    }
}
