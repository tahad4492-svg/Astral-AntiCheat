package me.mrtahadarvish.astral.manager;

import me.mrtahadarvish.astral.AstralAntiCheat;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** /astral freeze - lock a player in place for a screen share / check. */
public final class FreezeManager implements Listener {

    private final AstralAntiCheat plugin;
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

    public FreezeManager(AstralAntiCheat plugin) {
        this.plugin = plugin;
    }

    public boolean isFrozen(Player player) {
        return frozen.contains(player.getUniqueId());
    }

    /** Toggles freeze, returns the new state. */
    public boolean toggle(Player player) {
        if (frozen.remove(player.getUniqueId())) {
            player.clearTitle();
            return false;
        }
        frozen.add(player.getUniqueId());
        remind(player);
        return true;
    }

    public void tick() {
        for (UUID uuid : frozen) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                remind(player);
            }
        }
    }

    private void remind(Player player) {
        player.sendMessage(plugin.messages().lines("freeze-message"));
        player.showTitle(Title.title(
                plugin.messages().get("prefix"),
                me.mrtahadarvish.astral.util.Text.color("&cYou are frozen - do not log out"),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(500))));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!isFrozen(event.getPlayer())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isFrozen(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isFrozen(player)) {
            event.setCancelled(true);
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player damager && isFrozen(damager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isFrozen(event.getPlayer())) {
            return;
        }
        String label = event.getMessage().split(" ")[0].toLowerCase(Locale.ROOT);
        for (String allowed : plugin.settings().freezeAllowedCommands) {
            if (label.equalsIgnoreCase(allowed)) {
                return;
            }
        }
        event.setCancelled(true);
        plugin.messages().send(event.getPlayer(), "freeze-blocked-command");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!frozen.remove(player.getUniqueId())) {
            return;
        }
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("astral.alerts")) {
                plugin.messages().send(staff, "freeze-quit", "player", player.getName());
            }
        }
        plugin.logs().log("FREEZE-QUIT " + player.getName() + " logged out while frozen");
        for (String command : plugin.settings().freezeQuitCommands) {
            String parsed = command.replace("%player%", player.getName());
            if (parsed.startsWith("/")) {
                parsed = parsed.substring(1);
            }
            String finalCommand = parsed;
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand));
        }
    }
}
