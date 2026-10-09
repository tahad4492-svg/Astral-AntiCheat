package me.mrtahadarvish.astral.check.player;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Bow spam: shooting more arrows per second than drawing a bow allows.
 */
public final class FastBowCheck extends Check {

    private int maxShots;

    public FastBowCheck(AstralAntiCheat plugin) {
        super(plugin, "fastbow", "FastBow", CheckCategory.PLAYER, "Shooting arrows faster than a bow can be drawn.");
    }

    @Override
    protected void onLoad() {
        maxShots = option("max-shots-per-second", 6);
    }

    private static final class State {
        final Deque<Integer> shots = new ArrayDeque<>();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack bow = event.getBow();
        if (bow == null || bow.getType() != Material.BOW) {
            return;
        }
        PlayerData data = data(player);
        if (!shouldCheck(data)) {
            return;
        }
        State state = data.state(State.class, State::new);
        state.shots.addLast(data.clientTicks);
        while (!state.shots.isEmpty() && data.clientTicks - state.shots.peekFirst() >= 20) {
            state.shots.removeFirst();
        }
        if (state.shots.size() > maxShots) {
            if (flag(data, "Rate", "shots/s=" + state.shots.size() + " force=" + Math.round(event.getForce() * 100) + "%")) {
                event.setCancelled(true);
            }
        }
    }
}
