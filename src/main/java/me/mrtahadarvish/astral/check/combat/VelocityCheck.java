package me.mrtahadarvish.astral.check.combat;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.check.TickCheck;
import me.mrtahadarvish.astral.data.Buffer;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.data.VelocityRecord;

import static me.mrtahadarvish.astral.data.PlayerData.since;
import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * Anti-KnockBack. After the server sends knockback, the client has to move with it within
 * its ping (+ a few ticks). Vertical knockback is exact (the first tick moves exactly the
 * sent motion), horizontal knockback is checked with a ratio.
 */
public final class VelocityCheck extends Check implements TickCheck {

    private double minVertical;
    private double minHorizontal;

    public VelocityCheck(AstralAntiCheat plugin) {
        super(plugin, "velocity", "Velocity", CheckCategory.COMBAT, "Taking less knockback than vanilla (anti-KB).");
    }

    @Override
    protected void onLoad() {
        minVertical = option("min-vertical", 0.98);
        minHorizontal = option("min-horizontal", 0.35);
    }

    @Override
    public void onTick(PlayerData data) {
        if (data.velocities.isEmpty()) {
            return;
        }
        boolean check = shouldCheck(data);
        boolean invalid = !check || data.skipMovement || data.headBlocked || data.inWeb || data.inLiquid || data.climbing
                || data.nearPowderSnow || data.nearHoney || data.nearSlime || data.nearScaffolding || data.gliding
                || data.inVehicle || data.flying || data.riptiding || since(data.lastTeleport) < 5
                || since(data.lastLevitation) < 5 || since(data.lastPiston) < 10 || since(data.lastFlight) < 20
                || since(data.lastExplosion) < 3
                || data.player().isDead();

        for (VelocityRecord record : data.velocities) {
            int age = data.clientTicks - record.clientTick;
            if (age < 1 || record.evaluated) {
                continue;
            }
            if (invalid) {
                record.invalid = true;
            }
            if (data.horizontalCollision || since(data.lastCollision) < 2) {
                record.horizontalInvalid = true;
            }
            record.maxDeltaY = Math.max(record.maxDeltaY, data.deltaY);
            record.maxDeltaXZ = Math.max(record.maxDeltaXZ, data.deltaXZ);
            if (data.deltaY >= record.y * minVertical - 0.003) {
                record.verticalTaken = true;
            }
            if (data.deltaXZ >= record.horizontal * minHorizontal) {
                record.horizontalTaken = true;
            }
            if (age < record.window) {
                continue;
            }
            record.evaluated = true;
            if (record.invalid || !check || overlaps(data, record)) {
                continue;
            }
            if (record.checkVertical && !record.verticalTaken) {
                double ratio = record.y <= 0.0 ? 0.0 : record.maxDeltaY / record.y;
                flag(data, "Vertical", "taken=" + format(Math.max(0.0, ratio) * 100.0, 1) + "% kb=" + format(record.y));
            } else if (record.checkHorizontal && !record.horizontalTaken && !record.horizontalInvalid) {
                Buffer buffer = data.buffer("velocity.horizontal");
                if (buffer.increase() > 2.0) {
                    buffer.set(1.0);
                    double ratio = record.maxDeltaXZ / record.horizontal;
                    flag(data, "Horizontal", "taken=" + format(ratio * 100.0, 1) + "% kb=" + format(record.horizontal));
                }
            } else if (record.checkHorizontal && record.horizontalTaken) {
                data.buffer("velocity.horizontal").decrease(0.5);
            }
        }
    }

    /** A second knockback inside the window replaces the first one on the client - skip both. */
    private boolean overlaps(PlayerData data, VelocityRecord record) {
        for (VelocityRecord other : data.velocities) {
            boolean verified = other.checkVertical || other.checkHorizontal;
            if (other != record && verified && Math.abs(other.clientTick - record.clientTick) <= record.window) {
                return true;
            }
        }
        return false;
    }
}
