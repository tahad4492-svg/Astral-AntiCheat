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
 *   <li>Snap - turning 40+ degrees to the target and back within two ticks (silent aim).</li>
 * </ul>
 */
public final class AimCheck extends Check implements TickCheck {

    private boolean gcd;
    private boolean robotic;
    private boolean snap;

    public AimCheck(AstralAntiCheat plugin) {
        super(plugin, "aim", "Aim", CheckCategory.COMBAT, "Inhuman rotations (aimbot, aim assist, rotation auras).");
    }

    @Override
    protected void onLoad() {
        gcd = option("gcd", true);
        robotic = option("robotic", true);
        snap = option("snap", true);
    }

    private static final class State {
        int smoothTicks;
        int roboticTicks;
    }

    @Override
    public void onTick(PlayerData data) {
        if (!shouldCheck(data) || !data.inCombat() || data.skipMovement || data.inVehicle
                || since(data.lastTeleport) < 20 || since(data.lastRespawn) < 20) {
            return;
        }
        State state = data.state(State.class, State::new);
        float yaw = Math.abs(data.deltaYaw);
        float pitch = Math.abs(data.deltaPitch);
        float lastYaw = Math.abs(data.lastDeltaYaw);
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

        if (snap && data.clientTicks - data.lastAttackClientTick <= 2 && lastYaw > 40.0F && yaw > 40.0F
                && Math.signum(data.deltaYaw) != Math.signum(data.lastDeltaYaw)
                && Math.abs(data.deltaYaw + data.lastDeltaYaw) < 5.0F) {
            Buffer buffer = data.buffer("aim.snap");
            if (buffer.increase() > 1.0) {
                buffer.set(0.5);
                if (flag(data, "Snap", "out=" + format(data.lastDeltaYaw, 1) + " back=" + format(data.deltaYaw, 1))) {
                    plugin.checks().lockCombat(data);
                }
            }
        }
    }
}
