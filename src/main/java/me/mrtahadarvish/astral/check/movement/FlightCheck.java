package me.mrtahadarvish.astral.check.movement;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.MovementCheck;
import me.mrtahadarvish.astral.data.Buffer;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.BlockUtil;

import static me.mrtahadarvish.astral.data.PlayerData.since;
import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * Simulates vanilla gravity every client tick: motionY = (lastMotionY - gravity) * 0.98.
 * Any difference while in the air means the client is flying, gliding, air jumping,
 * spidering, hovering or fast falling.
 */
public final class FlightCheck extends MovementCheck {

    private double tolerance;
    private double maxBuffer;

    public FlightCheck(AstralAntiCheat plugin) {
        super(plugin, "flight", "Flight", "Moving in the air against gravity (fly, glide, air jump, spider, hover).");
    }

    @Override
    protected void onLoad() {
        tolerance = option("tolerance", 0.005);
        maxBuffer = option("buffer", 3.0);
    }

    @Override
    protected void handle(PlayerData data) {
        if (exempt(data) || environmentExempt(data)) {
            return;
        }
        Buffer buffer = data.buffer("flight");
        double deltaY = data.deltaY;

        if (data.lastOnGround) {
            if (deltaY <= 0.0) {
                return;
            }
            double maxJump = maxJump(data);
            double max = Math.max(maxJump, data.stepHeight) + 0.001;
            if (deltaY > max && !matchesVelocity(data, deltaY)) {
                fail(data, "HighJump", "dy=" + format(deltaY) + " max=" + format(max));
                return;
            }
            // left the ground with a hop smaller than a jump (packet criticals / low hop)
            if (!data.onGround && deltaY < maxJump - 0.01 && !data.headBlocked && since(data.lastHeadBlocked) > 3
                    && !matchesVelocity(data, deltaY) && !data.groundLenient && !BlockUtil.nearCollidable(data.player())) {
                if (buffer.increase() > 1.0) {
                    fail(data, "MiniJump", "dy=" + format(deltaY) + " jump=" + format(maxJump));
                }
            }
            return;
        }

        if (since(data.lastHeadBlocked) < 3 && deltaY <= 0.0) {
            return;
        }

        boolean slowFalling = since(data.lastSlowFalling) < 2;
        double gravity = slowFalling && data.lastDeltaY <= 0.0 ? Math.min(data.gravity, 0.01) : data.gravity;
        double predicted = (data.lastDeltaY - gravity) * 0.98;
        if (Math.abs(predicted) < 0.003) {
            predicted = 0.0;
        }
        double difference = deltaY - predicted;

        if (data.onGround) {
            // landing: a collision can only slow the fall down, never speed it up
            if (difference < -tolerance - 0.01 && !matchesVelocity(data, deltaY)) {
                if (buffer.increase() > maxBuffer) {
                    fail(data, "FastFall", "dy=" + format(deltaY) + " pred=" + format(predicted));
                }
            }
            return;
        }

        if (Math.abs(difference) > tolerance && !matchesVelocity(data, deltaY)) {
            if (data.headBlocked && difference < 0.0) {
                return;
            }
            double weight = Math.abs(difference) > 0.1 ? 1.5 : 1.0;
            if (buffer.increase(weight) > maxBuffer) {
                if (BlockUtil.nearCollidable(data.player())) {
                    buffer.reset();
                    return;
                }
                String type;
                if (difference > 0.0) {
                    type = deltaY > 0.0 ? (data.horizontalCollision ? "Spider" : "Ascend") : (Math.abs(deltaY) < 0.01 ? "Hover" : "Glide");
                } else {
                    type = "FastFall";
                }
                fail(data, type, "dy=" + format(deltaY) + " pred=" + format(predicted) + " air=" + data.airTicks);
            }
        } else {
            buffer.decrease(0.2);
        }
    }

    private void fail(PlayerData data, String type, String debug) {
        if (flag(data, type, debug)) {
            setback(data);
        }
    }

    private boolean environmentExempt(PlayerData data) {
        return data.inLiquid || since(data.lastLiquid) < 8
                || data.climbing || since(data.lastClimb) < 6
                || data.inWeb || since(data.lastWeb) < 6
                || since(data.lastPowderSnow) < 10
                || since(data.lastHoney) < 10
                || since(data.lastSlime) < 40
                || since(data.lastBed) < 40
                || since(data.lastScaffolding) < 10
                || since(data.lastLevitation) < 20
                || data.swimming
                || data.player().isInPowderedSnow();
    }
}
