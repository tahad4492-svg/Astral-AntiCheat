package me.mrtahadarvish.astral.check.combat;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.check.TickCheck;
import me.mrtahadarvish.astral.data.Buffer;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.listener.MovementProcessor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.List;

import static me.mrtahadarvish.astral.data.PlayerData.since;

/**
 * KillAura / ForceField / TriggerBot:
 * <ul>
 *   <li>Multi - hitting two different entities in the same client tick (impossible with a mouse)</li>
 *   <li>Post - the attack is sent after the movement packet of the tick. A vanilla client always
 *       attacks first and moves after, silent rotation auras do it the other way around</li>
 *   <li>NoSwing - attacking without swinging the arm</li>
 *   <li>Walls - attacking through full solid blocks</li>
 * </ul>
 */
public final class KillAuraCheck extends Check implements TickCheck {

    private boolean multi;
    private boolean post;
    private boolean noSwing;
    private boolean walls;
    private int lockTicks;

    public KillAuraCheck(AstralAntiCheat plugin) {
        super(plugin, "killaura", "KillAura", CheckCategory.COMBAT, "Automatic attacking (aura, forcefield, triggerbot, multi aura).");
    }

    @Override
    protected void onLoad() {
        multi = option("multi", true);
        post = option("post", true);
        noSwing = option("no-swing", true);
        walls = option("walls", true);
        lockTicks = option("block-attacks-ticks", 60);
    }

    public int lockTicks() {
        return lockTicks;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        Player player = event.getPlayer();
        Entity target = event.getAttacked();
        PlayerData data = data(player);
        if (!shouldCheck(data) || !CombatUtil.isCombatTarget(target) || player.getGameMode() == GameMode.SPECTATOR
                || data.ping() > plugin.settings().maxCombatPing) {
            return;
        }

        if (post && data.hasTickSnapshot && !data.inVehicle && since(data.lastTeleport) > 40 && since(data.lastFailMove) > 40 && since(data.lastPiston) > 20
                && since(data.lastVehicle) > 20 && since(data.lastRespawn) > 40) {
            Location location = player.getLocation();
            boolean moved = location.getX() != data.tickEndX || location.getY() != data.tickEndY || location.getZ() != data.tickEndZ
                    || location.getYaw() != data.tickEndYaw || location.getPitch() != data.tickEndPitch;
            Buffer buffer = data.buffer("killaura.post");
            if (moved) {
                if (buffer.increase() > 3.0) {
                    buffer.set(1.5);
                    if (fail(data, "Post", "attack sent after the movement packet")) {
                        event.setCancelled(true);
                    }
                }
            } else {
                buffer.decrease(0.1);
            }
        }

        if (walls && target.getWorld().equals(player.getWorld()) && !MovementProcessor.isSpear(player.getInventory().getItemInMainHand())) {
            List<BoundingBox> boxes = CombatUtil.targetBoxes(plugin, target, data);
            Vector eye = player.getEyeLocation().toVector();
            if (CombatUtil.allBlocked(player.getWorld(), eye, boxes)) {
                Buffer buffer = data.buffer("killaura.walls");
                if (buffer.increase() > 1.0) {
                    if (fail(data, "Walls", "hit " + target.getType().name().toLowerCase() + " through a wall")) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @Override
    public void onTick(PlayerData data) {
        if (data.attacksThisTick == 0 || !shouldCheck(data)) {
            return;
        }
        if (multi && data.targetsThisTick.size() >= 2) {
            fail(data, "Multi", "targets=" + data.targetsThisTick.size() + " in one tick");
        }
        if (noSwing && data.swingsThisTick == 0 && since(data.lastSpear) > 10) {
            Buffer buffer = data.buffer("killaura.noswing");
            if (buffer.increase() > 2.0) {
                buffer.set(1.0);
                fail(data, "NoSwing", "attacks=" + data.attacksThisTick + " swings=0");
            }
        } else if (data.swingsThisTick > 0) {
            data.buffer("killaura.noswing").decrease(0.25);
        }
    }

    private boolean fail(PlayerData data, String type, String debug) {
        boolean mitigate = flag(data, type, debug);
        if (mitigate) {
            plugin.checks().lockCombat(data);
        }
        return mitigate;
    }
}
