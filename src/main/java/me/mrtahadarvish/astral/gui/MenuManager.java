package me.mrtahadarvish.astral.gui;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.ClientUtil;
import me.mrtahadarvish.astral.util.MathUtil;
import me.mrtahadarvish.astral.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** /astral gui - staff menus. */
public final class MenuManager implements Listener {

    private enum Type {
        PLAYERS, PLAYER, CHECKS
    }

    /** Marks Astral inventories. */
    private static final class Menu implements InventoryHolder {
        private final Type type;
        private final UUID target;
        private final List<UUID> slots = new ArrayList<>();
        private final List<Check> checks = new ArrayList<>();
        private Inventory inventory;

        private Menu(Type type, UUID target) {
            this.type = type;
            this.target = target;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final AstralAntiCheat plugin;

    public MenuManager(AstralAntiCheat plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ menus

    public void openPlayers(Player viewer) {
        Menu menu = new Menu(Type.PLAYERS, null);
        Inventory inventory = Bukkit.createInventory(menu, 54, Text.color("&8Astral &7» &fPlayers"));
        menu.inventory = inventory;

        List<PlayerData> sorted = new ArrayList<>(plugin.data().all());
        sorted.sort(Comparator.comparingDouble(PlayerData::totalViolations).reversed());
        int slot = 0;
        for (PlayerData data : sorted) {
            if (slot >= 45) {
                break;
            }
            Player player = data.player();
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(player);
            meta.displayName(Text.item((data.totalViolations() > 0 ? "&c" : "&a") + player.getName()));
            meta.lore(Text.lore(List.of(
                    "&7Total VL: &c" + MathUtil.format(data.totalViolations(), 1),
                    "&7Top: " + plugin.violations().topChecks(data, 3),
                    "&7Ping: &f" + data.ping() + "ms &8| &7CPS: &f" + data.cps(),
                    "&7Client: &f" + ClientUtil.describe(player),
                    "&7Bypass: " + (data.bypassPermission ? "&aYes" : "&cNo"),
                    "",
                    "&eClick for actions")));
            head.setItemMeta(meta);
            inventory.setItem(slot, head);
            menu.slots.add(player.getUniqueId());
            slot++;
        }
        inventory.setItem(45, item(Material.BOOK, "&b" + AstralAntiCheat.DISPLAY_NAME + " AntiCheat",
                "&7Mode: " + (plugin.settings().mitigation ? "&cBLOCK" : "&aWATCH (alerts only)"),
                "&7Version: &f" + plugin.getPluginMeta().getVersion(),
                "&7Flags: &f" + plugin.violations().totalFlags(),
                "&7Punishments: &f" + plugin.violations().totalPunishments()));
        inventory.setItem(49, item(Material.COMPARATOR, "&bChecks", "&7Enable / disable checks"));
        inventory.setItem(53, item(Material.BARRIER, "&cClose"));
        viewer.openInventory(inventory);
    }

    public void openPlayer(Player viewer, Player target) {
        Menu menu = new Menu(Type.PLAYER, target.getUniqueId());
        Inventory inventory = Bukkit.createInventory(menu, 27, Text.color("&8Astral &7» &f" + target.getName()));
        menu.inventory = inventory;
        PlayerData data = plugin.data().get(target);
        boolean bypass = data != null && data.bypassPermission;
        boolean frozen = plugin.freeze().isFrozen(target);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(target);
        meta.displayName(Text.item("&b" + target.getName()));
        if (data != null) {
            meta.lore(Text.lore(List.of(
                    "&7Total VL: &c" + MathUtil.format(data.totalViolations(), 1),
                    "&7Flags: &f" + data.totalFlags,
                    "&7Ping: &f" + data.ping() + "ms",
                    "&7Client: &f" + ClientUtil.describe(target))));
        }
        head.setItemMeta(meta);
        inventory.setItem(4, head);
        inventory.setItem(10, item(Material.ENDER_PEARL, "&bTeleport", "&7Teleport to the player"));
        inventory.setItem(11, item(Material.BOOK, "&bInfo", "&7Show the full profile in chat"));
        inventory.setItem(12, item(Material.WATER_BUCKET, "&bReset violations", "&7Clear every VL of the player"));
        inventory.setItem(13, item(bypass ? Material.LIME_DYE : Material.GRAY_DYE, bypass ? "&aBypass: ON" : "&cBypass: OFF",
                "&7Toggle /astral bypass"));
        inventory.setItem(14, item(frozen ? Material.PACKED_ICE : Material.ICE, frozen ? "&bFrozen: YES" : "&7Frozen: NO",
                "&7Freeze / unfreeze"));
        inventory.setItem(15, item(Material.CLOCK, "&bWatch CPS", "&7Show the CPS in your action bar"));
        inventory.setItem(16, item(Material.PAPER, "&bViolations", "&7Violation level per check"));
        inventory.setItem(22, item(Material.WRITABLE_BOOK, "&bLogs", "&7Every recent flag of the player"));
        inventory.setItem(18, item(Material.ARROW, "&7Back"));
        viewer.openInventory(inventory);
    }

    public void openChecks(Player viewer) {
        Menu menu = new Menu(Type.CHECKS, null);
        Inventory inventory = Bukkit.createInventory(menu, 54, Text.color("&8Astral &7» &fChecks"));
        menu.inventory = inventory;
        int slot = 0;
        for (Check check : plugin.checks().all()) {
            if (slot >= 45) {
                break;
            }
            ItemStack item = new ItemStack(check.enabled() ? Material.LIME_DYE : Material.GRAY_DYE);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Text.item((check.enabled() ? "&a" : "&c") + check.name()));
            meta.lore(Text.lore(List.of(
                    "&7Category: " + check.category().color() + check.category().display(),
                    "&7" + check.description(),
                    "&7Flags since start: &f" + check.totalFlags(),
                    "",
                    check.enabled() ? "&eClick to disable" : "&eClick to enable")));
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            menu.checks.add(check);
            slot++;
        }
        inventory.setItem(45, item(Material.ARROW, "&7Back"));
        viewer.openInventory(inventory);
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.item(name));
        if (lore.length > 0) {
            meta.lore(Text.lore(List.of(lore)));
        }
        item.setItemMeta(meta);
        return item;
    }

    // ------------------------------------------------------------------ clicks

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer) || event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }
        int slot = event.getRawSlot();
        switch (menu.type) {
            case PLAYERS -> {
                if (slot < menu.slots.size()) {
                    Player target = Bukkit.getPlayer(menu.slots.get(slot));
                    if (target != null) {
                        openPlayer(viewer, target);
                    }
                } else if (slot == 49) {
                    openChecks(viewer);
                } else if (slot == 53) {
                    viewer.closeInventory();
                }
            }
            case CHECKS -> {
                if (slot < menu.checks.size()) {
                    Check check = menu.checks.get(slot);
                    viewer.performCommand("astral toggle " + check.id());
                    openChecks(viewer);
                } else if (slot == 45) {
                    openPlayers(viewer);
                }
            }
            case PLAYER -> {
                Player target = Bukkit.getPlayer(menu.target);
                if (target == null) {
                    openPlayers(viewer);
                    return;
                }
                String name = target.getName();
                switch (slot) {
                    case 10 -> {
                        viewer.closeInventory();
                        viewer.performCommand("astral tp " + name);
                    }
                    case 11 -> {
                        viewer.closeInventory();
                        viewer.performCommand("astral info " + name);
                    }
                    case 12 -> {
                        viewer.performCommand("astral reset " + name);
                        openPlayer(viewer, target);
                    }
                    case 13 -> {
                        viewer.performCommand("astral bypass " + name);
                        Bukkit.getScheduler().runTask(plugin, () -> openPlayer(viewer, target));
                    }
                    case 14 -> {
                        viewer.performCommand("astral freeze " + name);
                        openPlayer(viewer, target);
                    }
                    case 15 -> {
                        viewer.closeInventory();
                        viewer.performCommand("astral cps " + name);
                    }
                    case 16 -> {
                        viewer.closeInventory();
                        viewer.performCommand("astral violations " + name);
                    }
                    case 22 -> {
                        viewer.closeInventory();
                        viewer.performCommand("astral logs " + name);
                    }
                    case 18 -> openPlayers(viewer);
                    default -> {
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
