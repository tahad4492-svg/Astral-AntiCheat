package me.mrtahadarvish.astral.listener;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.player.PlayerFailMoveEvent;
import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.data.VelocityRecord;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.Vector;

/**
 * Keeps {@link PlayerData} up to date with everything that can legitimately change how a
 * player moves (teleports, knockback, elytra, pistons ...) so checks can exempt it.
 */
public final class DataListener implements Listener {

    private final AstralAntiCheat plugin;

    public DataListener(AstralAntiCheat plugin) {
        this.plugin = plugin;
    }

    private PlayerData data(Player player) {
        return plugin.data().get(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        PlayerData data = plugin.data().create(event.getPlayer());
        data.lastSafe = event.getPlayer().getLocation();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.data().remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerData data = data(event.getPlayer());
        if (data == null) {
            return;
        }
        data.lastTeleport = PlayerData.now();
        data.resetPosition = true;
        data.velocities.clear();
        data.fallDistance = 0.0;
        data.groundSpoofed = false;
        Location to = event.getTo();
        data.teleportPending = true;
        data.teleportTick = PlayerData.now();
        data.teleportX = to.getX();
        data.teleportY = to.getY();
        data.teleportZ = to.getZ();
        if (to.getWorld() != null) {
            // where the server put the player is legit, but it is not known to be ground
            // (minigames teleport players into the air - a battle bus, a drop ship ...)
            data.lastSafe = null;
            data.hasLegit = true;
            data.legitX = to.getX();
            data.legitY = to.getY();
            data.legitZ = to.getZ();
            data.legitWorld = to.getWorld().getUID();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        PlayerData data = data(event.getPlayer());
        if (data != null) {
            data.lastRespawn = PlayerData.now();
            data.lastTeleport = PlayerData.now();
            data.resetPosition = true;
            data.lastSafe = null;
            data.hasLegit = false;
            data.velocities.clear();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        PlayerData data = data(event.getPlayer());
        if (data != null) {
            data.resetPosition = true;
            data.velocities.clear();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        PlayerData data = data(event.getPlayer());
        if (data != null) {
            data.lastWorldChange = PlayerData.now();
            data.lastTeleport = PlayerData.now();
            data.resetPosition = true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        PlayerData data = data(event.getPlayer());
        if (data != null) {
            data.lastGameMode = PlayerData.now();
            data.lastFlight = PlayerData.now();
        }
    }

    // not ignoreCancelled: the client already started flying before a plugin cancelled it
    @EventHandler(priority = EventPriority.MONITOR)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        PlayerData data = data(event.getPlayer());
        if (data != null) {
            data.lastFlight = PlayerData.now();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        PlayerData data = data(event.getPlayer());
        if (data == null) {
            return;
        }
        addVelocity(data, event.getVelocity(), true);
    }

    /** Explosion / wind charge / mace knockback (sent to the client inside other packets). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerData data = data(player);
        if (data == null) {
            return;
        }
        Vector knockback = event.getKnockback();
        // approximation only: used to exempt movement, never to verify anti-knockback
        addVelocity(data, player.getVelocity().clone().add(knockback), false);
        if (event.getCause() == EntityKnockbackEvent.Cause.EXPLOSION) {
            data.lastExplosion = PlayerData.now();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        markExplosion(event.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        markExplosion(event.getBlock().getLocation());
    }

    private void markExplosion(Location location) {
        if (location.getWorld() == null) {
            return;
        }
        int now = PlayerData.now();
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) < 16 * 16) {
                PlayerData data = data(player);
                if (data != null) {
                    data.lastExplosion = now;
                }
            }
        }
    }

    private void addVelocity(PlayerData data, Vector velocity, boolean verify) {
        data.lastVelocity = PlayerData.now();
        data.lastVelocityHorizontal = Math.hypot(velocity.getX(), velocity.getZ());
        data.lastVelocityVertical = velocity.getY();
        data.fallDistance = 0.0;
        int window = Math.min(40, data.pingTicks() + 6);
        boolean vertical = verify && data.onGround && velocity.getY() > 0.15 && velocity.getY() < 1.5;
        boolean horizontal = verify && Math.hypot(velocity.getX(), velocity.getZ()) > 0.25;
        data.velocities.addLast(new VelocityRecord(velocity.getX(), velocity.getY(), velocity.getZ(),
                data.clientTicks, window, vertical, horizontal));
        while (data.velocities.size() > 20) {
            data.velocities.removeFirst();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        PlayerData data = data(player);
        if (data == null) {
            return;
        }
        data.lastDamage = PlayerData.now();
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            data.fallDamageTaken += event.getDamage();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onElytraBoost(PlayerElytraBoostEvent event) {
        PlayerData data = data(event.getPlayer());
        if (data != null) {
            data.lastFirework = PlayerData.now();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRiptide(PlayerRiptideEvent event) {
        PlayerData data = data(event.getPlayer());
        if (data != null) {
            data.lastRiptide = PlayerData.now();
        }
    }

    // not ignoreCancelled: the client already started gliding before a plugin cancelled it
    @EventHandler(priority = EventPriority.MONITOR)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player) {
            PlayerData data = data(player);
            if (data != null) {
                data.lastGlide = PlayerData.now();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player) {
            PlayerData data = data(player);
            if (data != null) {
                data.lastVehicle = PlayerData.now();
                data.lastSafe = null; // the ground before boarding (a bus) is no setback target later
            }
        }
    }

    /** Minigames cancel landing damage (battle royale drops) - NoFall must not hand it out then. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onFallDamageCancelled(EntityDamageEvent event) {
        if (event.isCancelled() && event.getCause() == EntityDamageEvent.DamageCause.FALL
                && event.getEntity() instanceof Player player) {
            PlayerData data = data(player);
            if (data != null) {
                data.lastFallCancelled = PlayerData.now();
            }
        }
    }

    /**
     * Lag compensation history, recorded at the end of the tick: riders are on their seat then
     * (Paper snaps a vehicle driver to the vehicle's feet while handling its packet).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerTickEnd(ServerTickEndEvent event) {
        plugin.data().recordHistory(PlayerData.now() + 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) {
            PlayerData data = data(player);
            if (data != null) {
                data.lastVehicle = PlayerData.now();
                data.resetPosition = true;
                data.dismountPending = true;
                data.lastSafe = null;
                data.hasLegit = false;
            }
        }
    }

    /** Paper snaps the player back without a teleport event when a move is rejected. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onFailMove(PlayerFailMoveEvent event) {
        PlayerData data = data(event.getPlayer());
        if (data != null && !event.isAllowed()) {
            data.lastFailMove = PlayerData.now();
            data.resetPosition = true;
        }
    }

    /** Another plugin cancelled the move (regions, combat tag ...) - the player is snapped back. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCancelledMove(PlayerMoveEvent event) {
        if (!event.isCancelled()) {
            return;
        }
        PlayerData data = data(event.getPlayer());
        if (data != null) {
            data.lastTeleport = PlayerData.now();
            data.resetPosition = true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        markPiston(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        markPiston(event.getBlock());
    }

    private void markPiston(Block piston) {
        int now = PlayerData.now();
        for (Entity entity : piston.getWorld().getNearbyEntities(piston.getLocation().add(0.5, 0.5, 0.5), 14, 14, 14)) {
            if (entity instanceof Player player) {
                PlayerData data = data(player);
                if (data != null) {
                    data.lastPiston = now;
                }
            }
        }
    }

    /** A cancelled place / break next to the player leaves a "ghost block" on the client for a moment. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onGhostPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) {
            markGhost(event.getPlayer(), event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGhostBreak(BlockBreakEvent event) {
        if (event.isCancelled()) {
            markGhost(event.getPlayer(), event.getBlock());
        }
    }

    private void markGhost(Player player, Block block) {
        PlayerData data = data(player);
        if (data == null) {
            return;
        }
        Location location = player.getLocation();
        if (Math.abs(block.getX() + 0.5 - location.getX()) < 2.5
                && Math.abs(block.getZ() + 0.5 - location.getZ()) < 2.5
                && block.getY() - location.getY() < 3.0
                && location.getY() - block.getY() < 3.0) {
            data.lastGhostBlock = PlayerData.now();
        }
    }
}
