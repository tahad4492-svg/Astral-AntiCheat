package me.mrtahadarvish.astral.check.movement;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.MovementCheck;
import me.mrtahadarvish.astral.data.Buffer;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.BlockUtil;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import static me.mrtahadarvish.astral.data.PlayerData.since;
import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * Vanilla trusts the "on ground" flag of the client for fall damage. NoFall cheats always send
 * "on ground". Astral compares the flag with real block collisions and gives the player the
 * fall damage he tried to avoid.
 */
public final class NoFallCheck extends MovementCheck {

    private boolean applyDamage;

    public NoFallCheck(AstralAntiCheat plugin) {
        super(plugin, "nofall", "NoFall", "Spoofing the on-ground state to avoid fall damage.");
    }

    @Override
    protected void onLoad() {
        applyDamage = option("apply-damage", true);
    }

    @Override
    protected void handle(PlayerData data) {
        if (exempt(data, true) || resets(data)) {
            data.fallDistance = 0.0;
            data.groundSpoofed = false;
            data.fallDamageTaken = 0.0;
            return;
        }

        if (!data.onGround && data.deltaY < 0.0) {
            data.fallDistance -= data.deltaY;
        }
        if (exempt(data)) {
            // just got off a vehicle (battle bus): keep counting the fall, but do not judge yet
            if (data.onGround) {
                data.fallDistance = 0.0;
                data.fallDamageTaken = 0.0;
            }
            return;
        }

        Buffer buffer = data.buffer("nofall");
        // the client says "on ground" but there is nothing under its feet
        if (data.clientOnGround && !data.onGround && !data.groundLenient
                && since(data.lastGhostBlock) > 20 && since(data.lastPowderSnow) > 10 && since(data.lastScaffolding) > 10
                && !BlockUtil.nearCollidable(data.player())) {
            if (buffer.increase() > 1.0) {
                data.groundSpoofed = true;
                flag(data, "GroundSpoof", "fall=" + format(data.fallDistance, 2) + " dy=" + format(data.deltaY));
            }
        } else {
            buffer.decrease(0.25);
        }

        // landed for real
        if (data.onGround && !data.lastOnGround) {
            if (data.groundSpoofed && applyDamage && mitigate() && data.violation(id()) >= mitigateVl()) {
                compensate(data);
            }
            data.fallDistance = 0.0;
            data.groundSpoofed = false;
            data.fallDamageTaken = 0.0;
        } else if (data.onGround) {
            data.fallDistance = 0.0;
            data.fallDamageTaken = 0.0;
        }
    }

    private boolean resets(PlayerData data) {
        return data.inLiquid || data.climbing || data.inWeb || data.nearPowderSnow
                || since(data.lastSlowFalling) < 2 || since(data.lastLevitation) < 2
                || since(data.lastVelocity) < 2 || data.player().isInPowderedSnow();
    }

    private void compensate(PlayerData data) {
        if (since(data.lastFallCancelled) < 100) {
            return; // a minigame cancels fall damage on purpose
        }
        Player player = data.player();
        Block below = player.getLocation().subtract(0.0, 0.2, 0.0).getBlock();
        Material type = below.getType();
        if (type == Material.SLIME_BLOCK || type == Material.HAY_BLOCK || type == Material.HONEY_BLOCK
                || type == Material.POWDER_SNOW || type == Material.COBWEB || Tag.BEDS.isTagged(type)
                || data.inLiquid || data.nearLiquid) {
            return;
        }
        double safe = attribute(player, Attribute.SAFE_FALL_DISTANCE, 3.0);
        double multiplier = attribute(player, Attribute.FALL_DAMAGE_MULTIPLIER, 1.0);
        PotionEffect jump = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
        double jumpBonus = jump == null ? 0.0 : jump.getAmplifier() + 1.0;
        double expected = Math.floor((data.fallDistance - safe - jumpBonus) * multiplier);
        double missing = expected - data.fallDamageTaken;
        if (missing >= 1.0) {
            player.damage(missing, DamageSource.builder(DamageType.FALL).build());
        }
    }

    private static double attribute(Player player, Attribute attribute, double def) {
        AttributeInstance instance = player.getAttribute(attribute);
        return instance == null ? def : instance.getValue();
    }
}
