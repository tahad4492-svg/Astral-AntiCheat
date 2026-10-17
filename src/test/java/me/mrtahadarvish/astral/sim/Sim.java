package me.mrtahadarvish.astral.sim;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import io.papermc.paper.event.packet.ClientTickEndEvent;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckManager;
import me.mrtahadarvish.astral.config.Messages;
import me.mrtahadarvish.astral.config.Settings;
import me.mrtahadarvish.astral.data.FlagRecord;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.data.PlayerDataManager;
import me.mrtahadarvish.astral.listener.DataListener;
import me.mrtahadarvish.astral.listener.InteractionListener;
import me.mrtahadarvish.astral.listener.MovementProcessor;
import me.mrtahadarvish.astral.manager.AlertManager;
import me.mrtahadarvish.astral.manager.BypassManager;
import me.mrtahadarvish.astral.manager.FreezeManager;
import me.mrtahadarvish.astral.manager.LogManager;
import me.mrtahadarvish.astral.manager.ViolationManager;
import me.mrtahadarvish.astral.util.Clock;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.mockito.Mockito.when;

/**
 * Runs the real Astral code (data, listeners, every check, violation manager) against simulated
 * players. Events are dispatched like Bukkit does (priority order, ignoreCancelled).
 */
public final class Sim {

    private static Sim current;

    public final AstralAntiCheat plugin;
    public final Settings settings = new Settings();
    public final SimWorld world;
    public final List<SimPlayer> players = new ArrayList<>();
    private final List<Handler> handlers = new ArrayList<>();
    private long nanos = 1_000_000_000L;

    private record Handler(Object listener, Method method, Class<?> type, EventPriority priority, boolean ignoreCancelled, int order) {
    }

    public static Sim current() {
        return current;
    }

    /**
     * Creates a stub-only mock owned by the current simulation. Inline mocks keep a strong
     * reference to themselves (last invocation), so they are released explicitly in
     * {@link #close()} instead of piling up across tests.
     */
    public static <T> T mock(Class<T> type) {
        return track(Mockito.mock(type, Mockito.withSettings().stubOnly()));
    }

    public static <T> T mock(Class<T> type, Answer<Object> answer) {
        return track(Mockito.mock(type, Mockito.withSettings().stubOnly().defaultAnswer(answer)));
    }

    private static final List<Object> MOCKS = new ArrayList<>();

    static synchronized <T> T track(T mock) {
        MOCKS.add(mock);
        return mock;
    }

    /** Releases every mock created for this simulation so the whole world can be collected. */
    public void close() {
        synchronized (Sim.class) {
            for (Object mock : MOCKS) {
                Mockito.framework().clearInlineMock(mock);
            }
            MOCKS.clear();
        }
        handlers.clear();
        players.clear();
        world.clear();
        SimServer.ONLINE.clear();
        if (current == this) {
            current = null;
        }
    }

    public Sim() {
        if (current != null) {
            current.close();
        }
        world = new SimWorld();
        SimServer.init();
        SimServer.ONLINE.clear();
        // initialise registry backed classes before any Mockito stubbing uses them
        InventoryType.values();
        InventoryType.SlotType.values();
        EntityType.values();
        ClickType.values();
        InventoryAction.values();
        Material.STONE.isAir();
        current = this;
        Clock.use(() -> nanos);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Sim.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8));
        settings.load(config);
        settings.joinExemptTicks = 0;
        settings.logFile = false;

        plugin = mock(AstralAntiCheat.class);
        when(plugin.settings()).thenReturn(settings);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("Astral-Sim"));
        when(plugin.messages()).thenReturn(mock(Messages.class));
        when(plugin.logs()).thenReturn(mock(LogManager.class));
        when(plugin.alerts()).thenReturn(mock(AlertManager.class));
        when(plugin.bypass()).thenReturn(mock(BypassManager.class));
        when(plugin.freeze()).thenReturn(mock(FreezeManager.class));
        PlayerDataManager data = new PlayerDataManager(plugin);
        when(plugin.data()).thenReturn(data);
        ViolationManager violations = new ViolationManager(plugin);
        when(plugin.violations()).thenReturn(violations);
        CheckManager checks = new CheckManager(plugin);
        when(plugin.checks()).thenReturn(checks);
        checks.registerAll();

        // same registration order as the plugin: checks first, then the listeners
        for (Check check : checks.all()) {
            register(check);
        }
        register(new DataListener(plugin));
        register(new MovementProcessor(plugin));
        register(new InteractionListener(plugin));
    }

    // ------------------------------------------------------------------ events

    private void register(Listener listener) {
        for (Method method : listener.getClass().getMethods()) {
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null || method.getParameterCount() != 1) {
                continue;
            }
            method.setAccessible(true);
            handlers.add(new Handler(listener, method, method.getParameterTypes()[0], annotation.priority(),
                    annotation.ignoreCancelled(), handlers.size()));
        }
        handlers.sort(Comparator.comparingInt((Handler h) -> h.priority().getSlot()).thenComparingInt(Handler::order));
    }

    public <T extends Event> T call(T event) {
        for (Handler handler : handlers) {
            if (!handler.type().isInstance(event)) {
                continue;
            }
            if (handler.ignoreCancelled() && event instanceof Cancellable cancellable && cancellable.isCancelled()) {
                continue;
            }
            try {
                handler.method().invoke(handler.listener(), event);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Handler " + handler.method() + " failed", e.getCause() != null ? e.getCause() : e);
            }
        }
        return event;
    }

    // ------------------------------------------------------------------ world / players

    /** Flat stone floor, top surface at y = 64. */
    public Sim floor(int radius) {
        world.fill(-radius, 63, -radius, radius, 63, radius, Material.STONE);
        return this;
    }

    public SimPlayer join(String name, double x, double y, double z) {
        SimPlayer player = new SimPlayer(world, name, x, y, z, name.hashCode());
        players.add(player);
        SimServer.ONLINE.add(player.player);
        call(new PlayerJoinEvent(player.player, Component.empty()));
        return player;
    }

    public PlayerData data(SimPlayer player) {
        return plugin.data().get(player.player);
    }

    /**
     * A movement packet was received: fires PlayerMoveEvent exactly when Paper does (moved more
     * than 1/16 block or turned more than 10 degrees since the last fired event).
     */
    void moved(SimPlayer player) {
        double dx = player.sx - player.eventX;
        double dy = player.sy - player.eventY;
        double dz = player.sz - player.eventZ;
        float angle = Math.abs(player.syaw - player.eventYaw) + Math.abs(player.spitch - player.eventPitch);
        if (dx * dx + dy * dy + dz * dz <= 1.0 / 256.0 && angle <= 10.0F) {
            return;
        }
        Location from = new Location(world.world, player.eventX, player.eventY, player.eventZ, player.eventYaw, player.eventPitch);
        Location to = new Location(world.world, player.sx, player.sy, player.sz, player.syaw, player.spitch);
        player.eventX = player.sx;
        player.eventY = player.sy;
        player.eventZ = player.sz;
        player.eventYaw = player.syaw;
        player.eventPitch = player.spitch;
        call(new PlayerMoveEvent(player.player, from, to));
    }

    public void teleport(SimPlayer player, Location to) {
        Location from = new Location(world.world, player.sx, player.sy, player.sz);
        PlayerTeleportEvent event = new PlayerTeleportEvent(player.player, from, to, PlayerTeleportEvent.TeleportCause.PLUGIN);
        call(event);
        player.teleportTo(to);
    }

    // ------------------------------------------------------------------ ticking

    /**
     * One tick for everybody: each client runs its tick (actions -> movement -> tick end), then
     * the server ticks.
     */
    public void tick(Consumer<SimPlayer> beforeMove) {
        tick(beforeMove, null);
    }

    public void tick(Consumer<SimPlayer> beforeMove, Consumer<SimPlayer> afterMove) {
        for (SimPlayer player : players) {
            clientTick(player, beforeMove, afterMove);
        }
        serverTick();
    }

    public void clientTick(SimPlayer player, Consumer<SimPlayer> beforeMove, Consumer<SimPlayer> afterMove) {
        if (beforeMove != null) {
            beforeMove.accept(player);
        }
        player.physicsTick();
        player.sendMovement();
        if (afterMove != null) {
            afterMove.accept(player);
        }
        tickEnd(player);
    }

    public void tickEnd(SimPlayer player) {
        call(new ClientTickEndEvent(player.player));
    }

    public void serverTick() {
        call(new ServerTickEndEvent(SimServer.tick, 50.0, 0L));
        SimServer.tick++;
        nanos += 50_000_000L;
        plugin.data().tick();
    }

    /** Advances only the real time clock (for timer tests). */
    public void advanceNanos(long amount) {
        nanos += amount;
    }

    public void idle(int ticks) {
        for (int i = 0; i < ticks; i++) {
            tick(null);
        }
    }

    // ------------------------------------------------------------------ actions (packets)

    public boolean attack(SimPlayer attacker, Entity target) {
        PrePlayerAttackEntityEvent event = call(new PrePlayerAttackEntityEvent(attacker.player, target, true));
        return !event.isCancelled();
    }

    public void swing(SimPlayer player) {
        call(new PlayerArmSwingEvent(player.player, EquipmentSlot.HAND));
    }

    /** Hotbar slot change. Returns false when Astral cancelled it (the server keeps the old slot). */
    public boolean slot(SimPlayer player, int slot) {
        PlayerItemHeldEvent event = call(new PlayerItemHeldEvent(player.player, player.slot, slot));
        if (!event.isCancelled()) {
            player.slot = slot;
        }
        return !event.isCancelled();
    }

    public void useItem(SimPlayer player) {
        call(new PlayerInteractEvent(player.player, Action.RIGHT_CLICK_AIR, SimPlayer.item(player.mainHand), null, BlockFace.SELF, EquipmentSlot.HAND));
    }

    public void velocity(SimPlayer player, Vector velocity) {
        call(new PlayerVelocityEvent(player.player, velocity));
    }

    // ------------------------------------------------------------------ minigame plugin actions

    /** A plugin puts the player on a vehicle (bus seat, boat ...). */
    public void mount(SimPlayer player, SimEntity vehicle) {
        call(new EntityMountEvent(player.player, vehicle.entity));
        player.vehicle = vehicle;
        vehicle.passengers.add(player.player);
        player.ride(vehicle.x, vehicle.y + vehicle.height, vehicle.z);
    }

    /** The player leaves the vehicle: Paper teleports him to the dismount position. */
    public void dismount(SimPlayer player, Location to) {
        SimEntity vehicle = player.vehicle;
        call(new EntityDismountEvent(player.player, vehicle.entity));
        vehicle.passengers.remove(player.player);
        player.vehicle = null;
        teleport(player, to);
    }

    public void glide(SimPlayer player, boolean gliding) {
        call(new EntityToggleGlideEvent(player.player, gliding));
        player.gliding = gliding;
    }

    /** A plugin adds / removes a potion effect: the client gets it half a round trip later. */
    public void effect(SimPlayer player, String key, boolean on) {
        if (on) {
            player.effects.add(key);
        } else {
            player.effects.remove(key);
        }
        player.later(player.halfRtt(), () -> {
            if (on) {
                player.clientEffects.add(key);
            } else {
                player.clientEffects.remove(key);
            }
        });
    }

    /** Entity#setGravity from a plugin (also reaches the client later). */
    public void gravity(SimPlayer player, boolean on) {
        player.gravity = on;
        player.later(player.halfRtt(), () -> player.clientGravity = on);
    }

    /** Player#setVelocity from a plugin: event now, the client applies it later. */
    public void push(SimPlayer player, Vector velocity) {
        call(new PlayerVelocityEvent(player.player, velocity.clone()));
        Vector v = velocity.clone();
        player.later(player.halfRtt(), () -> {
            player.mx = v.getX();
            player.my = v.getY();
            player.mz = v.getZ();
        });
    }

    public void gameMode(SimPlayer player, org.bukkit.GameMode mode) {
        call(new org.bukkit.event.player.PlayerGameModeChangeEvent(player.player, mode));
        player.gameMode = mode;
    }

    /** The client asks to start / stop flying (double jump). Returns false when a plugin cancelled it. */
    public boolean toggleFlight(SimPlayer player, boolean flying, boolean pluginCancels) {
        org.bukkit.event.player.PlayerToggleFlightEvent event = new org.bukkit.event.player.PlayerToggleFlightEvent(player.player, flying);
        event.setCancelled(pluginCancels); // the minigame's own listener runs before Astral's MONITOR one
        call(event);
        if (!event.isCancelled()) {
            player.sFlying = flying;
        }
        return !event.isCancelled();
    }

    public void totemPop(SimPlayer player) {
        call(new EntityResurrectEvent(player.player, EquipmentSlot.OFF_HAND));
    }

    public boolean place(SimPlayer player, Block against, BlockFace face, Material material) {
        Block placed = against.getRelative(face);
        call(new PlayerInteractEvent(player.player, Action.RIGHT_CLICK_BLOCK, SimPlayer.item(material), against, face, EquipmentSlot.HAND));
        BlockPlaceEvent event = new BlockPlaceEvent(placed, mock(BlockState.class), against, SimPlayer.item(material),
                player.player, true, EquipmentSlot.HAND);
        call(event);
        if (!event.isCancelled()) {
            world.set(placed.getX(), placed.getY(), placed.getZ(), material);
        }
        return !event.isCancelled();
    }

    public void startDig(SimPlayer player, Block block) {
        call(new PlayerInteractEvent(player.player, Action.LEFT_CLICK_BLOCK, SimPlayer.item(player.mainHand), block, BlockFace.UP, EquipmentSlot.HAND));
        call(new BlockDamageEvent(player.player, block, SimPlayer.item(player.mainHand), false));
    }

    public boolean breakBlock(SimPlayer player, Block block) {
        BlockBreakEvent event = call(new BlockBreakEvent(block, player.player));
        if (!event.isCancelled()) {
            world.set(block.getX(), block.getY(), block.getZ(), Material.AIR);
        }
        return !event.isCancelled();
    }

    /** A click in the player's own inventory (or a container when top != null). */
    public InventoryClickEvent click(SimPlayer player, int slot, ClickType click, InventoryAction action, ItemStack current,
                                     InventoryType top) {
        InventoryView view = mock(InventoryView.class);
        Inventory topInventory = mock(Inventory.class);
        when(topInventory.getType()).thenReturn(top == null ? InventoryType.CRAFTING : top);
        PlayerInventory bottom = mock(PlayerInventory.class);
        when(bottom.getType()).thenReturn(InventoryType.PLAYER);
        when(view.getTopInventory()).thenReturn(topInventory);
        when(view.getBottomInventory()).thenReturn(bottom);
        when(view.getPlayer()).thenReturn(player.player);
        when(view.getItem(Mockito.anyInt())).thenReturn(current);
        boolean inTop = top != null && slot < 27;
        when(view.getInventory(Mockito.anyInt())).thenReturn(inTop ? topInventory : bottom);
        when(view.convertSlot(Mockito.anyInt())).thenReturn(slot);
        when(view.getSlotType(Mockito.anyInt())).thenReturn(InventoryType.SlotType.CONTAINER);
        return call(new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, click, action));
    }

    public EnderCrystal crystal(Block on) {
        EnderCrystal crystal = mock(EnderCrystal.class);
        int id = 50_000 + world.entities.size();
        int bx = on.getX();
        int by = on.getY();
        int bz = on.getZ();
        BoundingBox box = new BoundingBox(bx - 0.5, by + 1, bz - 0.5, bx + 1.5, by + 3, bz + 1.5);
        Location location = new Location(world.world, bx + 0.5, by + 1, bz + 0.5);
        when(crystal.getEntityId()).thenReturn(id);
        when(crystal.getBoundingBox()).thenReturn(box);
        when(crystal.getWorld()).thenReturn(world.world);
        when(crystal.getType()).thenReturn(EntityType.END_CRYSTAL);
        when(crystal.getVelocity()).thenReturn(new Vector());
        when(crystal.getLocation()).thenReturn(location);
        world.entities.add(crystal);
        return crystal;
    }

    public void placeCrystal(SimPlayer player, Block on, EnderCrystal crystal) {
        call(new PlayerInteractEvent(player.player, Action.RIGHT_CLICK_BLOCK, SimPlayer.item(Material.END_CRYSTAL), on, BlockFace.UP, EquipmentSlot.HAND));
        call(new EntityPlaceEvent(crystal, player.player, on, BlockFace.UP, EquipmentSlot.HAND));
    }

    // ------------------------------------------------------------------ results

    public List<FlagRecord> flags(SimPlayer player) {
        PlayerData data = data(player);
        return data == null ? List.of() : new ArrayList<>(data.flags);
    }

    public boolean flagged(SimPlayer player, String check) {
        return flags(player).stream().anyMatch(f -> f.check().equalsIgnoreCase(check));
    }

    public boolean flagged(SimPlayer player, String check, String type) {
        return flags(player).stream().anyMatch(f -> f.check().equalsIgnoreCase(check) && f.type().equalsIgnoreCase(type));
    }

    public String summary(SimPlayer player) {
        StringBuilder sb = new StringBuilder();
        List<FlagRecord> list = flags(player);
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        for (FlagRecord f : list) {
            counts.merge(f.check() + "(" + f.type() + ")", 1, Integer::sum);
        }
        counts.forEach((k, v) -> sb.append(k).append(" x").append(v).append("  "));
        if (!list.isEmpty()) {
            sb.append("| first: ").append(list.getLast().check()).append(" ").append(list.getLast().debug());
        }
        return sb.isEmpty() ? "no flags" : sb.toString();
    }
}
