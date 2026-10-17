package me.mrtahadarvish.astral.check.movement;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.TickCheck;
import me.mrtahadarvish.astral.data.Buffer;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.data.VelocityRecord;

import static me.mrtahadarvish.astral.data.PlayerData.since;
import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * Elytra physics can not hover, can not climb without speed and have a top speed. Limits were
 * found by simulating the vanilla glide formula with a search over every pitch (a legit
 * elytra hovers for at most ~8 ticks and climbs at low speed for at most ~22 ticks).
 */
public final class ElytraFlyCheck extends Check implements TickCheck {

    private double maxSpeed;

    public ElytraFlyCheck(AstralAntiCheat plugin) {
        super(plugin, "elytrafly", "ElytraFly", CheckCategory.MOVEMENT, "Elytra flight that ignores vanilla glide physics.");
    }

    @Override
    protected void onLoad() {
        maxSpeed = option("max-speed", 4.3);
    }

    private static final class State {
        int hoverTicks;
        int climbTicks;
    }

    @Override
    public void onTick(PlayerData data) {
        if (!shouldCheck(data)) {
            return;
        }
        State state = data.state(State.class, State::new);
        if (!data.gliding || data.skipMovement || !data.chunkLoaded || data.inLiquid || data.inVehicle
                || since(data.lastFirework) < 60 || since(data.lastRiptide) < 40 || since(data.lastTeleport) < 10
                || since(data.lastExplosion) < 40 || since(data.lastLevitation) < 20 || data.player().isFlying()
                || velocityPending(data)) {
            state.hoverTicks = 0;
            state.climbTicks = 0;
            return;
        }
        double horizontal = data.deltaXZ;
        double vertical = data.deltaY;
        double total = Math.sqrt(horizontal * horizontal + vertical * vertical);
        // slow falling / low or no gravity: a legit elytra can hover and climb slowly for a long time
        boolean lowGravity = data.gravity < 0.075 || data.slowFalling || since(data.gravityChange) < 20 + data.pingTicks();
        if (lowGravity) {
            state.hoverTicks = 0;
            state.climbTicks = 0;
        }

        if (!lowGravity && horizontal < 0.3 && Math.abs(vertical) < 0.06 && !data.horizontalCollision && !data.headBlocked) {
            if (++state.hoverTicks >= 20) {
                state.hoverTicks = 10;
                fail(data, "Hover", "speed=" + format(horizontal) + " dy=" + format(vertical));
            }
        } else {
            state.hoverTicks = 0;
        }

        if (!lowGravity && vertical > 0.0 && horizontal < 0.2) {
            if (++state.climbTicks >= 30) {
                state.climbTicks = 15;
                fail(data, "Ascend", "speed=" + format(horizontal) + " dy=" + format(vertical));
            }
        } else {
            state.climbTicks = 0;
        }

        Buffer buffer = data.buffer("elytrafly.speed");
        if (total > maxSpeed) {
            if (buffer.increase() > 2.0 && !rocketAttached(data)) {
                fail(data, "Speed", "speed=" + format(total) + " max=" + format(maxSpeed));
            }
        } else {
            buffer.decrease(0.1);
        }
    }

    private boolean velocityPending(PlayerData data) {
        for (VelocityRecord record : data.velocities) {
            if (data.clientTicks - record.clientTick <= record.window + 2) {
                return true;
            }
        }
        return false;
    }

    /** Rockets a plugin attached to the player (no PlayerElytraBoostEvent for those). */
    private boolean rocketAttached(PlayerData data) {
        for (org.bukkit.entity.Entity entity : data.player().getNearbyEntities(4.0, 4.0, 4.0)) {
            if (entity instanceof org.bukkit.entity.Firework firework && firework.getAttachedTo() == data.player()) {
                data.lastFirework = PlayerData.now();
                return true;
            }
        }
        return false;
    }

    private void fail(PlayerData data, String type, String debug) {
        if (flag(data, type, debug)) {
            data.player().setGliding(false);
            setback(data);
        }
    }
}
