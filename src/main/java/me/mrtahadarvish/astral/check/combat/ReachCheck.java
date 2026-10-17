package me.mrtahadarvish.astral.check.combat;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
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

import java.util.List;

import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * Lag compensated reach: distance from the attacker's eyes to the closest point of every box
 * the target had during the attacker's ping. Uses the real vanilla attack range (attribute,
 * creative bonus and the attack_range component of 1.21.11 spears).
 */
public final class ReachCheck extends Check {

    private double tolerance;
    private double maxBuffer;

    public ReachCheck(AstralAntiCheat plugin) {
        super(plugin, "reach", "Reach", CheckCategory.COMBAT, "Hitting entities from further away than vanilla allows.");
    }

    @Override
    protected void onLoad() {
        tolerance = option("tolerance", 0.1);
        maxBuffer = option("buffer", 2.0);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        Player player = event.getPlayer();
        Entity target = event.getAttacked();
        PlayerData data = data(player);
        if (!shouldCheck(data) || !CombatUtil.isCombatTarget(target) || player.getGameMode() == GameMode.SPECTATOR
                || data.ping() > plugin.settings().maxCombatPing || !target.getWorld().equals(player.getWorld())) {
            return;
        }
        double range = CombatUtil.attackRange(player, target);
        double margin = CombatUtil.hitboxMargin(player);
        List<Vector> eyes = CombatUtil.eyes(player, data);
        List<BoundingBox> boxes = CombatUtil.targetBoxes(plugin, target, data);

        double best = Double.MAX_VALUE;
        for (BoundingBox box : boxes) {
            BoundingBox expanded = margin > 0.0 ? box.clone().expand(margin) : box;
            for (Vector eye : eyes) {
                best = Math.min(best, MathUtil.distanceToBox(eye, expanded));
            }
        }

        // the client sees its own vehicle late: its eyes trail the seat by (ping + lerp) x vehicle speed
        boolean riding = data.recentlyRode();
        double seatLag = riding ? Math.min(6.0, (data.deltaXZ + Math.abs(data.deltaY)) * (data.pingTicks() + 4)) : 0.0;
        Buffer buffer = data.buffer("reach");
        if (best > range + tolerance + seatLag) {
            boolean cancel = !riding && best > range + tolerance + 0.3;
            if (buffer.increase() >= maxBuffer) {
                if (flag(data, best > range + 1.0 ? "Blatant" : "Distance",
                        "reach=" + format(best) + " max=" + format(range) + " ping=" + data.ping())) {
                    cancel = true;
                }
            }
            if (cancel && mitigate()) {
                event.setCancelled(true);
            }
        } else {
            buffer.decrease(0.5);
        }
    }
}
