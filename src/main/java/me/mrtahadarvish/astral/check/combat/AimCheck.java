package me.mrtahadarvish.astral.check.combat;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.check.TickCheck;
import me.mrtahadarvish.astral.data.Buffer;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.MathUtil;

import static me.mrtahadarvish.astral.data.PlayerData.since;
import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * Rotation analysis while fighting.
 * <ul>
 *   <li>GCD - a mouse turns the camera in steps of sensitivity^3 * 1.2 degrees. Rotations
 *       calculated by aimbots / auras do not share that step.</li>
 *   <li>Robotic - perfectly repeated rotation steps (smooth rotation auras).</li>
 *   <li>SilentAim - the rotation jumps to the target for the tick of the hit and then jumps
 *       straight back to where the camera really is. A hand on a mouse never returns to the
 *       exact same angle one tick after a flick.</li>
 * </ul>
 */
public final class AimCheck extends Check implements TickCheck {

    private boolean gcd;
    private boolean robotic;
    private boolean silentAim;

    public AimCheck(AstralAntiCheat plugin) {
        super(plugin, "aim", "Aim", CheckCategory.COMBAT, "Inhuman rotations (aimbot, silent aim, rotation auras).");
    }

    @Override
    protected void onLoad() {
        gcd = option("gcd", true);
        robotic = option("robotic", true);
        silentAim = option("silent-aim", true);
    }

    private static final class State {
        int smoothTicks;
        int roboticTicks;
        boolean attackedLastTick;
    }

    @Override
    public void onTick(PlayerData data) {
        State state = data.state(State.class, State::new);
        boolean attackedLastTick = state.attackedLastTick;
        state.attackedLastTick = data.attacksThisTick > 0;
        if (!shouldCheck(data) || !data.inCombat() || data.skipMovement || data.inVehicle
                || since(data.lastTeleport) < 20 || since(data.lastRespawn) < 20) {
            return;
        }
        float yaw = Math.abs(data.deltaYaw);
        float pitch = Math.abs(data.deltaPitch);
        float lastPitch = Math.abs(data.lastDeltaPitch);

        // cinematic camera (smooth zoom / optifine) creates fake inhuman rotations - ignore it
        boolean smooth = yaw > 0.0F && yaw < 1.5F && pitch > 0.0F && pitch < 1.5F;
        state.smoothTicks = smooth ? Math.min(20, state.smoothTicks + 1) : Math.max(0, state.smoothTicks - 1);
        boolean cinematic = state.smoothTicks > 8;

        if (gcd && !cinematic && pitch > 0.5F && pitch < 20.0F && lastPitch > 0.5F && lastPitch < 20.0F
                && Math.abs(data.pitch) < 85.0F) {
            double divisor = MathUtil.gcd(pitch, lastPitch);
            Buffer buffer = data.buffer("aim.gcd");
            if (divisor < MathUtil.MIN_SENSITIVITY_STEP * 0.9) {
                if (buffer.increase() > 20.0) {
                    buffer.set(12.0);
                    flag(data, "GCD", "divisor=" + format(divisor, 5) + " dp=" + format(pitch, 4));
                }
            } else {
                buffer.decrease(0.75);
            }
        }

        if (robotic && yaw > 1.0F && data.deltaYaw == data.lastDeltaYaw && data.deltaPitch == data.lastDeltaPitch) {
            if (++state.roboticTicks >= 4) {
                state.roboticTicks = 0;
                flag(data, "Robotic", "yaw step=" + format(data.deltaYaw, 4) + " pitch step=" + format(data.deltaPitch, 4));
            }
        } else {
            state.roboticTicks = 0;
        }

        // lastDelta = turn towards the target, delta = turn of this tick. The hit happened in one
        // of the two ticks (pre or post order). Silent aim turns back by almost exactly the same amount.
        if (silentAim && (attackedLastTick || data.attacksThisTick > 0)) {
            double in = Math.hypot(data.lastDeltaYaw, data.lastDeltaPitch);
            double out = Math.hypot(data.deltaYaw, data.deltaPitch);
            double rest = Math.hypot(data.lastDeltaYaw + data.deltaYaw, data.lastDeltaPitch + data.deltaPitch);
            Buffer buffer = data.buffer("aim.silent");
            if (in >= 8.0 && out >= in * 0.7 && rest <= in * 0.15) {
                if (buffer.increase() > 1.0) {
                    buffer.set(0.5);
                    flag(data, "SilentAim", "turned " + format(in, 1) + " deg for the hit and " + format(out, 1)
                            + " deg back (diff " + format(rest, 2) + ")");
                }
            } else {
                buffer.decrease(0.05);
            }
        }
    }
}
