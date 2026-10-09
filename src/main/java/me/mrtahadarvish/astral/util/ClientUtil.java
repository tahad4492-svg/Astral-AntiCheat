package me.mrtahadarvish.astral.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Detects the real Minecraft version / brand / platform of a client.
 * ViaVersion and Floodgate are used through reflection when they are installed.
 */
public final class ClientUtil {

    private static final Map<Integer, String> VERSIONS = new TreeMap<>();

    static {
        VERSIONS.put(774, "1.21.11");
        VERSIONS.put(773, "1.21.9 - 1.21.10");
        VERSIONS.put(772, "1.21.7 - 1.21.8");
        VERSIONS.put(771, "1.21.6");
        VERSIONS.put(770, "1.21.5");
        VERSIONS.put(769, "1.21.4");
        VERSIONS.put(768, "1.21.2 - 1.21.3");
        VERSIONS.put(767, "1.21 - 1.21.1");
        VERSIONS.put(766, "1.20.5 - 1.20.6");
        VERSIONS.put(765, "1.20.3 - 1.20.4");
        VERSIONS.put(764, "1.20.2");
        VERSIONS.put(763, "1.20 - 1.20.1");
        VERSIONS.put(762, "1.19.4");
        VERSIONS.put(761, "1.19.3");
        VERSIONS.put(760, "1.19.1 - 1.19.2");
        VERSIONS.put(759, "1.19");
        VERSIONS.put(758, "1.18.2");
        VERSIONS.put(757, "1.18 - 1.18.1");
        VERSIONS.put(756, "1.17.1");
        VERSIONS.put(755, "1.17");
        VERSIONS.put(754, "1.16.4 - 1.16.5");
        VERSIONS.put(753, "1.16.3");
        VERSIONS.put(751, "1.16.2");
        VERSIONS.put(736, "1.16.1");
        VERSIONS.put(735, "1.16");
        VERSIONS.put(578, "1.15.2");
        VERSIONS.put(575, "1.15.1");
        VERSIONS.put(573, "1.15");
        VERSIONS.put(498, "1.14.4");
        VERSIONS.put(490, "1.14.3");
        VERSIONS.put(485, "1.14.2");
        VERSIONS.put(480, "1.14.1");
        VERSIONS.put(477, "1.14");
        VERSIONS.put(404, "1.13.2");
        VERSIONS.put(401, "1.13.1");
        VERSIONS.put(393, "1.13");
        VERSIONS.put(340, "1.12.2");
        VERSIONS.put(338, "1.12.1");
        VERSIONS.put(335, "1.12");
        VERSIONS.put(316, "1.11.1 - 1.11.2");
        VERSIONS.put(315, "1.11");
        VERSIONS.put(210, "1.10.x");
        VERSIONS.put(110, "1.9.3 - 1.9.4");
        VERSIONS.put(109, "1.9.2");
        VERSIONS.put(108, "1.9.1");
        VERSIONS.put(107, "1.9");
        VERSIONS.put(47, "1.8.x");
        VERSIONS.put(5, "1.7.6 - 1.7.10");
        VERSIONS.put(4, "1.7.2 - 1.7.5");
    }

    private static Boolean viaPresent;
    private static Method viaGetApi;
    private static Method viaGetPlayerVersion;

    private static Boolean floodgatePresent;
    private static Object floodgateApi;
    private static Method floodgateIsPlayer;

    private ClientUtil() {
    }

    @SuppressWarnings("deprecation")
    public static int serverProtocol() {
        try {
            return Bukkit.getUnsafe().getProtocolVersion();
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Real protocol version of the player (uses ViaVersion when installed). */
    public static int protocol(Player player) {
        int via = viaProtocol(player.getUniqueId());
        if (via > 0) {
            return via;
        }
        return player.getProtocolVersion();
    }

    public static String versionName(int protocol) {
        if (protocol <= 0) {
            return "Unknown";
        }
        if (protocol == serverProtocol()) {
            return Bukkit.getMinecraftVersion();
        }
        String name = VERSIONS.get(protocol);
        if (name != null) {
            return name;
        }
        if (protocol > 774) {
            return "Newer than 1.21.11";
        }
        return "Unknown";
    }

    public static String version(Player player) {
        int protocol = protocol(player);
        return versionName(protocol) + " (protocol " + protocol + ")";
    }

    public static String brand(Player player) {
        String brand = player.getClientBrandName();
        return brand == null || brand.isBlank() ? "unknown" : brand;
    }

    public static String describe(Player player) {
        return versionName(protocol(player)) + " / " + brand(player) + (isBedrock(player.getUniqueId()) ? " / Bedrock" : "");
    }

    private static int viaProtocol(UUID uuid) {
        try {
            if (viaPresent == null) {
                viaPresent = Bukkit.getPluginManager().getPlugin("ViaVersion") != null;
                if (viaPresent) {
                    Class<?> via = Class.forName("com.viaversion.viaversion.api.Via");
                    viaGetApi = via.getMethod("getAPI");
                    Object api = viaGetApi.invoke(null);
                    viaGetPlayerVersion = api.getClass().getMethod("getPlayerVersion", UUID.class);
                }
            }
            if (!viaPresent) {
                return -1;
            }
            Object api = viaGetApi.invoke(null);
            Object result = viaGetPlayerVersion.invoke(api, uuid);
            return result instanceof Integer i ? i : -1;
        } catch (Throwable t) {
            viaPresent = false;
            return -1;
        }
    }

    /** Bedrock player joined through Geyser / Floodgate. */
    public static boolean isBedrock(UUID uuid) {
        try {
            if (floodgatePresent == null) {
                floodgatePresent = Bukkit.getPluginManager().getPlugin("floodgate") != null;
                if (floodgatePresent) {
                    Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                    floodgateApi = apiClass.getMethod("getInstance").invoke(null);
                    floodgateIsPlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
                }
            }
            if (floodgatePresent) {
                Object result = floodgateIsPlayer.invoke(floodgateApi, uuid);
                if (result instanceof Boolean b && b) {
                    return true;
                }
            }
        } catch (Throwable t) {
            floodgatePresent = false;
        }
        // Floodgate default UUID format: 00000000-0000-0000-xxxx-xxxxxxxxxxxx
        return uuid.getMostSignificantBits() == 0L;
    }
}
