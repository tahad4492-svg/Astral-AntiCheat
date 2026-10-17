package me.mrtahadarvish.astral.check.combat;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import static me.mrtahadarvish.astral.util.MathUtil.format;

/**
 * Mace damage grows with the fall distance, and the server adds up the fall distance from every
 * movement packet. "Mace kill" cheats send extra position packets (up, then back down) right
 * before the hit, so the server thinks the player fell from very high and the smash one-shots
 * the target.
 * <p>
 * Astral follows the real position once per client tick and knows how far the player can have
 * fallen. A mace hit with a much bigger server fall distance is cancelled.
 */
public final class MaceCheck extends Check {

    private double tolerance;

    public MaceCheck(AstralAntiCheat plugin) {
        super(plugin, "mace", "Mace", CheckCategory.COMBAT, "Mace smashes with a faked fall distance (mace kill).");
    }

    @Override
    protected void onLoad() {
        tolerance = option("tolerance", 1.5);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() != Material.MACE) {
            return;
        }
        PlayerData data = data(player);
        if (!shouldCheck(data) || !data.hasTickSnapshot) {
            return;
        }
        double server = player.getFallDistance();
        if (server <= 1.5) {
            return; // no smash attack
        }
        // moved down after the last tick end (attack sent after the movement packet)
        double pending = Math.max(0.0, data.tickEndY - player.getLocation().getY());
        double real = data.legitFall + pending;
        if (server > real + tolerance + real * 0.05) {
            if (flag(data, "FakeFall", "server fall=" + format(server, 2) + " real fall=" + format(real, 2))) {
                event.setCancelled(true);
            }
        }
    }
}
