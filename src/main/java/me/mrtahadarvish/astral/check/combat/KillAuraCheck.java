package me.mrtahadarvish.astral.check.combat;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.check.PacketOrder;
import me.mrtahadarvish.astral.check.TickCheck;
import me.mrtahadarvish.astral.data.Buffer;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.listener.MovementProcessor;
import me.mrtahadarvish.astral.util.MathUtil;
import org.bukkit.GameMode;
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
 *   <li>Switch - hitting two entities far apart from each other in back-to-back ticks</li>
 * </ul>
 */
public final class KillAuraCheck extends Check implements TickCheck {

    private boolean multi;
    private boolean post;
    private boolean noSwing;
    private boolean walls;
    private boolean switchCheck;
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
        switchCheck = option("switch", true);
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

        if (post) {
            Buffer buffer = data.buffer("killaura.post");
            if (PacketOrder.isPost(data)) {
                if (buffer.increase() > 2.0) {
                    buffer.set(1.0);
                    if (fail(data, "Post", "attack sent after the movement packet (silent rotations)")) {
                        event.setCancelled(true);
                    }
                }
            } else {
                buffer.decrease(0.1);
            }
        }

        if (switchCheck) {
            checkSwitch(event, player, target, data);
        }

        if (walls && !data.recentlyRode() && target.getWorld().equals(player.getWorld())
                && !MovementProcessor.isSpear(player.getInventory().getItemInMainHand())) {
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

    private static final class State {
        int lastTarget = -1;
        int lastAttackTick = -1000;
        Vector lastDirection;
    }

    /**
     * Switch aura: hitting two different entities in back-to-back ticks that are far apart from
     * each other (a mouse can not turn 100+ degrees between two ticks and hit both).
     */
    private void checkSwitch(PrePlayerAttackEntityEvent event, Player player, Entity target, PlayerData data) {
        State state = data.state(State.class, State::new);
        Vector eye = player.getEyeLocation().toVector();
        Vector direction = target.getBoundingBox().getCenter().subtract(eye);
        int ticks = data.clientTicks - state.lastAttackTick;
        if (state.lastDirection != null && state.lastTarget != target.getEntityId() && ticks <= 1
                && direction.lengthSquared() > 1.0 && state.lastDirection.lengthSquared() > 1.0) {
            double angle = MathUtil.angle(direction, state.lastDirection);
            if (angle > 100.0) {
                Buffer buffer = data.buffer("killaura.switch");
                if (buffer.increase() > 1.0) {
                    buffer.set(0.5);
                    if (fail(data, "Switch", "switched " + MathUtil.format(angle, 1) + " deg between targets in " + ticks + " tick(s)")) {
                        event.setCancelled(true);
                    }
                }
            }
        }
        state.lastTarget = target.getEntityId();
        state.lastAttackTick = data.clientTicks;
        state.lastDirection = direction;
    }

    private boolean fail(PlayerData data, String type, String debug) {
        boolean mitigate = flag(data, type, debug);
        if (mitigate) {
            plugin.checks().lockCombat(data);
        }
        return mitigate;
    }
}
