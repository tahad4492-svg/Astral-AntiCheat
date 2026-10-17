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

        double predicted = predict(data.lastDeltaY, data.gravity, data.slowFalling);
        // gravity / slow falling changed: the client may still use the old values for a round trip
        if (since(data.gravityChange) <= data.pingTicks() + 3) {
            double old = predict(data.lastDeltaY, data.oldGravity, data.oldSlowFalling);
            if (Math.abs(deltaY - old) < Math.abs(deltaY - predicted)) {
                predicted = old;
            }
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
            // > 0.25 blocks away from gravity in one tick is never legit (air jump, jetpack, blink up)
            double weight = Math.abs(difference) > 0.25 ? maxBuffer + 1.0 : Math.abs(difference) > 0.1 ? 1.5 : 1.0;
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
                buffer.set(maxBuffer * 0.5); // one streak of bad ticks = a few flags, not one per tick
            }
        } else {
            buffer.decrease(0.2);
        }
    }

    /** Vanilla: motionY = (motionY - gravity) * 0.98, slow falling caps gravity at 0.01 while falling. */
    private static double predict(double lastDeltaY, double gravity, boolean slowFalling) {
        double g = slowFalling && lastDeltaY <= 0.0 ? Math.min(gravity, 0.01) : gravity;
        double predicted = (lastDeltaY - g) * 0.98;
        return Math.abs(predicted) < 0.003 ? 0.0 : predicted;
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
