package me.mrtahadarvish.astral.check.movement;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.MovementCheck;
import me.mrtahadarvish.astral.data.Buffer;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.BlockUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import static me.mrtahadarvish.astral.data.PlayerData.since;
import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * Horizontal movement prediction using vanilla friction and acceleration:
 * <pre>
 *   speed = lastSpeed * friction + acceleration (+0.2 on a sprint jump)
 *   ground acceleration = movementSpeed * 0.216 / blockFriction^3
 *   air acceleration    = 0.026
 * </pre>
 * Type A catches speed / bhop / long jump, type B catches air strafe (changing direction in
 * the air faster than vanilla allows).
 */
public final class SpeedCheck extends MovementCheck {

    private double tolerance;
    private double maxBuffer;

    public SpeedCheck(AstralAntiCheat plugin) {
        super(plugin, "speed", "Speed", "Moving faster than vanilla physics allow (speed, bhop, strafe).");
    }

    @Override
    protected void onLoad() {
        tolerance = option("tolerance", 0.01);
        maxBuffer = option("buffer", 4.0);
    }

    private static final class State {
        double lastBase = 0.13;
        /** Speed of the last tick, capped to what was allowed (so cheats can not build on their own speed). */
        double allowed;
        boolean allowedValid;
    }

    @Override
    protected void handle(PlayerData data) {
        State state = data.state(State.class, State::new);
        double attribute = data.speedAttribute;
        // the attribute already contains the sprint bonus while sprinting; stay lenient otherwise
        double base = data.sprinting ? attribute : attribute * 1.3;
        double usedBase = Math.max(base, state.lastBase);
        state.lastBase = base;

        if (exempt(data) || data.inLiquid || since(data.lastLiquid) < 6 || data.inWeb
                || data.climbing || since(data.lastSlime) < 5 || since(data.lastBed) < 5) {
            state.allowedValid = false;
            return;
        }

        // ---------------------------------------------------------------- A: speed limit
        double momentumFriction = data.lastLastOnGround ? data.lastLastFriction * 0.91 : 0.91;
        double last = state.allowedValid ? Math.min(data.lastDeltaXZ, state.allowed) : data.lastDeltaXZ;
        double momentum = last * momentumFriction;
        double acceleration;
        if (data.lastOnGround) {
            double f = data.lastFriction;
            acceleration = usedBase * (0.21600002 / (f * f * f));
        } else {
            acceleration = 0.026;
        }
        double jump = data.lastOnGround && (data.deltaY > 0.0 || data.headBlocked) ? 0.2 : 0.0;
        double limit = momentum + acceleration + jump + tolerance;

        double velocity = pendingHorizontal(data);
        if (velocity > 0.0) {
            limit = Math.max(limit, velocity + acceleration + jump + 0.05);
        }

        Buffer buffer = data.buffer("speed");
        state.allowed = data.deltaXZ;
        state.allowedValid = true;
        if (data.deltaXZ > limit) {
            double excess = data.deltaXZ - limit;
            if (excess < 0.15) {
                // small but impossible gain: the next tick may only build on the allowed speed
                state.allowed = limit;
            }
            if (buffer.increase(excess > 0.1 ? 2.0 : 1.0) > maxBuffer) {
                if (nearEntities(data.player())) {
                    buffer.reset();
                    state.allowed = data.deltaXZ;
                } else if (flag(data, "Limit", "speed=" + format(data.deltaXZ) + " limit=" + format(limit)
                        + " ground=" + data.lastOnGround + " f=" + format(data.lastFriction, 3))) {
                    setback(data);
                }
            }
        } else {
            buffer.decrease(0.1);
        }

        // ---------------------------------------------------------------- B: air strafe
        if (data.lastOnGround || data.lastLastOnGround || data.onGround || velocity > 0.0
                || data.horizontalCollision || since(data.lastCollision) < 3 || since(data.lastClimb) < 5
                || since(data.lastWeb) < 5 || since(data.lastPowderSnow) < 5 || since(data.lastHoney) < 5
                || since(data.lastScaffolding) < 5 || data.nearLiquid) {
            return;
        }
        double predictedX = data.lastDeltaX * 0.91;
        double predictedZ = data.lastDeltaZ * 0.91;
        if (Math.abs(predictedX) < 0.003) {
            predictedX = 0.0;
        }
        if (Math.abs(predictedZ) < 0.003) {
            predictedZ = 0.0;
        }
        double accel = Math.hypot(data.deltaX - predictedX, data.deltaZ - predictedZ);
        Buffer strafe = data.buffer("speed.strafe");
        if (accel > 0.026 + 0.004 + tolerance * 0.2) {
            if (strafe.increase() > maxBuffer) {
                if (nearEntities(data.player())) {
                    strafe.reset();
                } else if (flag(data, "Strafe", "accel=" + format(accel, 4) + " max=0.026")) {
                    setback(data);
                }
            }
        } else {
            strafe.decrease(0.05);
        }
    }

    /** Other entities push players (entity cramming) - never flag then. */
    private boolean nearEntities(Player player) {
        for (Entity entity : player.getNearbyEntities(1.0, 1.5, 1.0)) {
            if (entity instanceof LivingEntity || entity.getType().name().contains("BOAT") || entity.getType().name().contains("MINECART")) {
                return true;
            }
        }
        return BlockUtil.nearCollidable(player);
    }
}
