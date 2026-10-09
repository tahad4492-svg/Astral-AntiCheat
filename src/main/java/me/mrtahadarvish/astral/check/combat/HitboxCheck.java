package me.mrtahadarvish.astral.check.combat;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.check.TickCheck;
import me.mrtahadarvish.astral.data.Buffer;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.MathUtil;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * The look direction of the attacker must actually point at the target (hitbox expanders,
 * aura without rotations). The client picks its target with the rotation of a rendered frame
 * between two ticks, so the check waits for the next tick and tests every rotation in between.
 */
public final class HitboxCheck extends Check implements TickCheck {

    private double margin;
    private double maxBuffer;

    public HitboxCheck(AstralAntiCheat plugin) {
        super(plugin, "hitbox", "Hitbox", CheckCategory.COMBAT, "Hitting entities without looking at their hitbox.");
    }

    @Override
    protected void onLoad() {
        margin = option("margin", 0.1);
        maxBuffer = option("buffer", 3.0);
    }

    private record Pending(List<Vector> eyes, List<BoundingBox> boxes, double range,
                           float yaw, float pitch, float previousYaw, float previousPitch) {
    }

    private static final class State {
        final List<Pending> pending = new ArrayList<>();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        Player player = event.getPlayer();
        Entity target = event.getAttacked();
        PlayerData data = data(player);
        if (!shouldCheck(data) || !CombatUtil.isCombatTarget(target) || player.getGameMode() == GameMode.SPECTATOR
                || data.ping() > plugin.settings().maxCombatPing || data.inVehicle || !target.getWorld().equals(player.getWorld())) {
            return;
        }
        double expand = margin + CombatUtil.hitboxMargin(player);
        List<BoundingBox> boxes = new ArrayList<>();
        for (BoundingBox box : CombatUtil.targetBoxes(plugin, target, data)) {
            boxes.add(box.clone().expand(expand));
        }
        State state = data.state(State.class, State::new);
        if (state.pending.size() < 10) {
            state.pending.add(new Pending(CombatUtil.eyes(player, data), boxes, CombatUtil.attackRange(player, target) + 1.0,
                    player.getLocation().getYaw(), player.getLocation().getPitch(), data.lastYaw, data.lastPitch));
        }
    }

    @Override
    public void onTick(PlayerData data) {
        State state = data.state(State.class, State::new);
        if (state.pending.isEmpty()) {
            return;
        }
        List<Pending> pending = new ArrayList<>(state.pending);
        state.pending.clear();
        if (!shouldCheck(data) || PlayerData.since(data.lastTeleport) < 10) {
            return;
        }
        Buffer buffer = data.buffer("hitbox");
        for (Pending hit : pending) {
            if (hits(hit, data.yaw, data.pitch)) {
                buffer.decrease(0.5);
                continue;
            }
            if (buffer.increase() > maxBuffer) {
                if (flag(data, "Miss", "yaw=" + MathUtil.format(data.yaw, 1) + " pitch=" + MathUtil.format(data.pitch, 1))) {
                    plugin.checks().lockCombat(data);
                }
            }
        }
    }

    private boolean hits(Pending hit, float endYaw, float endPitch) {
        float[][] rotations = new float[7][];
        rotations[0] = new float[]{hit.previousYaw, hit.previousPitch};
        rotations[1] = new float[]{hit.yaw, hit.pitch};
        rotations[2] = new float[]{endYaw, endPitch};
        for (int i = 1; i <= 4; i++) {
            float t = i / 5.0F;
            rotations[2 + i] = new float[]{MathUtil.lerpAngle(hit.yaw, endYaw, t), hit.pitch + (endPitch - hit.pitch) * t};
        }
        for (float[] rotation : rotations) {
            Vector direction = MathUtil.direction(rotation[0], rotation[1]);
            for (Vector eye : hit.eyes) {
                for (BoundingBox box : hit.boxes) {
                    if (CombatUtil.rayHits(eye, direction, hit.range, box)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
