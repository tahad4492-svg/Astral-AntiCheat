package me.mrtahadarvish.astral.check.combat;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.data.PlayerData;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPlaceEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Crystal aura with "ID predict" / instant break: the crystal is attacked in the same client
 * tick it was placed. A real client can only hit an entity after the server told it the entity
 * exists, which takes at least one tick (plus ping).
 */
public final class CrystalAuraCheck extends Check {

    public CrystalAuraCheck(AstralAntiCheat plugin) {
        super(plugin, "crystalaura", "CrystalAura", CheckCategory.COMBAT, "Breaking end crystals before the client could see them (crystal aura).");
    }

    private static final class State {
        /** entity id -> client tick it was placed */
        final Map<Integer, Integer> placed = new LinkedHashMap<>();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null || !(event.getEntity() instanceof EnderCrystal)) {
            return;
        }
        PlayerData data = data(player);
        if (data == null) {
            return;
        }
        State state = data.state(State.class, State::new);
        state.placed.put(event.getEntity().getEntityId(), data.clientTicks);
        while (state.placed.size() > 16) {
            state.placed.remove(state.placed.keySet().iterator().next());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (!(event.getAttacked() instanceof EnderCrystal crystal)) {
            return;
        }
        PlayerData data = data(event.getPlayer());
        if (!shouldCheck(data)) {
            return;
        }
        Integer placedTick = data.state(State.class, State::new).placed.remove(crystal.getEntityId());
        if (placedTick == null) {
            return;
        }
        int ticks = data.clientTicks - placedTick;
        if (ticks <= 0) {
            if (flag(data, "Predict", "hit the crystal in the same tick it was placed")) {
                event.setCancelled(true);
            }
        }
    }
}
