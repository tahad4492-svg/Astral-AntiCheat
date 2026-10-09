package me.mrtahadarvish.astral.check;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.data.VelocityRecord;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import static me.mrtahadarvish.astral.data.PlayerData.since;

/**
 * Base for checks that simulate movement every client tick.
 */
public abstract class MovementCheck extends Check implements TickCheck {

    protected MovementCheck(AstralAntiCheat plugin, String id, String name, String description) {
        super(plugin, id, name, CheckCategory.MOVEMENT, description);
    }

    @Override
    public final void onTick(PlayerData data) {
        if (!shouldCheck(data)) {
            return;
        }
        handle(data);
    }

    protected abstract void handle(PlayerData data);

    /**
     * Situations where vanilla physics are replaced by something Astral does not simulate
     * (creative flight, elytra, vehicles, teleports ...).
     */
    protected boolean exempt(PlayerData data) {
        Player player = data.player();
        if (data.skipMovement || !data.chunkLoaded || data.inVehicle || data.gliding || data.riptiding || data.flying) {
            return true;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR || player.isDead() || player.isSleeping()) {
            return true;
        }
        return since(data.lastTeleport) < 5
                || since(data.lastFailMove) < 5
                || since(data.lastFlight) < 40
                || since(data.lastVehicle) < 20
                || since(data.lastRiptide) < 40
                || since(data.lastGlide) < 20
                || since(data.lastPiston) < 20
                || since(data.lastRespawn) < 20
                || since(data.lastWorldChange) < 40
                || since(data.lastGameMode) < 20
                || since(data.lastGhostBlock) < 20
                || since(data.lastSpear) < 30
                || since(data.lastExplosion) < 30 + data.pingTicks();
    }

    /** True while a knockback the server sent can still arrive at the client. */
    protected boolean velocityPending(PlayerData data) {
        for (VelocityRecord record : data.velocities) {
            if (data.clientTicks - record.clientTick <= record.window + 2) {
                return true;
            }
        }
        return false;
    }

    /** Highest horizontal velocity that can currently be applied to the player. */
    protected double pendingHorizontal(PlayerData data) {
        double max = 0.0;
        for (VelocityRecord record : data.velocities) {
            if (data.clientTicks - record.clientTick <= record.window + 2) {
                max = Math.max(max, record.horizontal);
            }
        }
        return max;
    }

    /** True when the vertical movement of this tick equals a pending knockback. */
    protected boolean matchesVelocity(PlayerData data, double deltaY) {
        for (VelocityRecord record : data.velocities) {
            if (data.clientTicks - record.clientTick > record.window + 2) {
                continue;
            }
            if (Math.abs(deltaY - record.y) < 0.01 || Math.abs(deltaY - Math.max(record.y, data.jumpStrength)) < 0.01) {
                return true;
            }
        }
        return false;
    }

    protected double jumpBoost(Player player) {
        var effect = player.getPotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST);
        return effect == null ? 0.0 : 0.1 * (effect.getAmplifier() + 1);
    }

    /** Highest jump motion the player can have right now. */
    protected double maxJump(PlayerData data) {
        return data.jumpStrength + jumpBoost(data.player());
    }
}
