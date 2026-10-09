package me.mrtahadarvish.astral.sim;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.Tag;
import org.bukkit.UnsafeValues;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** A fake Paper server: tick counter, scheduler that runs tasks immediately, tags, players. */
public final class SimServer {

    public static int tick = 1000;
    public static final List<Player> ONLINE = new ArrayList<>();

    private static Server server;

    private SimServer() {
    }

    @SuppressWarnings("unchecked")
    public static synchronized void init() {
        if (server != null) {
            return;
        }
        server = Mockito.mock(Server.class, Mockito.withSettings().stubOnly());
        when(server.getLogger()).thenReturn(Logger.getLogger("SimServer"));
        when(server.getName()).thenReturn("Paper");
        when(server.getVersion()).thenReturn("1.21.11-sim");
        when(server.getBukkitVersion()).thenReturn("1.21.11-R0.1-SNAPSHOT");
        when(server.getMinecraftVersion()).thenReturn("1.21.11");
        when(server.isPrimaryThread()).thenReturn(true);
        when(server.getCurrentTick()).thenAnswer(invocation -> tick);
        when(server.getAverageTickTime()).thenReturn(50.0);
        when(server.getOnlinePlayers()).thenAnswer(invocation -> (Collection<Player>) (Collection<?>) ONLINE);
        when(server.getPlayer(any(UUID.class))).thenAnswer(invocation -> {
            UUID uuid = invocation.getArgument(0);
            return ONLINE.stream().filter(p -> p.getUniqueId().equals(uuid)).findFirst().orElse(null);
        });
        when(server.getRegistry(any())).thenAnswer(invocation ->
                io.papermc.paper.registry.RegistryAccess.registryAccess().getRegistry((Class<Keyed>) invocation.getArgument(0)));
        UnsafeValues unsafe = Mockito.mock(UnsafeValues.class, Mockito.withSettings().stubOnly());
        when(unsafe.getProtocolVersion()).thenReturn(774);
        when(server.getUnsafe()).thenReturn(unsafe);
        when(server.getPluginManager()).thenReturn(Mockito.mock(PluginManager.class, Mockito.withSettings().stubOnly()));
        when(server.getTag(anyString(), any(NamespacedKey.class), any())).thenAnswer(invocation ->
                new SimTag(invocation.getArgument(1)));

        BukkitTask task = Mockito.mock(BukkitTask.class, Mockito.withSettings().stubOnly());
        BukkitScheduler scheduler = Mockito.mock(BukkitScheduler.class, Mockito.withSettings().stubOnly().defaultAnswer((Answer<Object>) invocation -> {
            String name = invocation.getMethod().getName();
            if (name.startsWith("runTask") && !name.contains("Timer") && !name.contains("Async")) {
                for (Object argument : invocation.getArguments()) {
                    if (argument instanceof Runnable runnable) {
                        runnable.run();
                        return task;
                    }
                }
            }
            return Mockito.RETURNS_DEFAULTS.answer(invocation);
        }));
        when(server.getScheduler()).thenReturn(scheduler);
        Bukkit.setServer(server);
    }

    /** Material tags used by Astral (climbable, beds). */
    @SuppressWarnings("rawtypes")
    private static final class SimTag implements Tag {
        private static final Set<Material> CLIMBABLE = Set.of(Material.LADDER, Material.VINE, Material.SCAFFOLDING,
                Material.TWISTING_VINES, Material.WEEPING_VINES, Material.CAVE_VINES);
        private final NamespacedKey key;

        SimTag(NamespacedKey key) {
            this.key = key;
        }

        @Override
        public boolean isTagged(Keyed item) {
            if (!(item instanceof Material material)) {
                return false;
            }
            return switch (key.getKey()) {
                case "climbable" -> CLIMBABLE.contains(material);
                case "beds" -> material.name().endsWith("_BED");
                default -> false;
            };
        }

        @Override
        public Set getValues() {
            return Set.of();
        }

        @Override
        public NamespacedKey getKey() {
            return key;
        }
    }
}
