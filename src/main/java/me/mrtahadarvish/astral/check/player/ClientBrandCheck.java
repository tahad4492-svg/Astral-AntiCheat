package me.mrtahadarvish.astral.check.player;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.ClientUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;

import java.util.List;
import java.util.Locale;

/**
 * Blocks known hacked clients that announce themselves through their client brand or the plugin
 * channels they register.
 */
public final class ClientBrandCheck extends Check {

    private List<String> blockedBrands = List.of();
    private List<String> blockedChannels = List.of();

    public ClientBrandCheck(AstralAntiCheat plugin) {
        super(plugin, "clientbrand", "ClientBrand", CheckCategory.PLAYER, "Joining with a known hacked client.");
    }

    @Override
    protected void onLoad() {
        blockedBrands = lower(optionList("blocked-brands"));
        blockedChannels = lower(optionList("blocked-channels"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            PlayerData data = data(player);
            if (data == null || !enabled() || data.isBypassing(id())) {
                return;
            }
            String brand = ClientUtil.brand(player).toLowerCase(Locale.ROOT);
            for (String blocked : blockedBrands) {
                if (brand.contains(blocked)) {
                    punishClient(data, "Brand", "brand=" + brand);
                    return;
                }
            }
        }, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChannel(PlayerRegisterChannelEvent event) {
        PlayerData data = data(event.getPlayer());
        if (data == null || !enabled() || data.isBypassing(id())) {
            return;
        }
        String channel = event.getChannel().toLowerCase(Locale.ROOT);
        for (String blocked : blockedChannels) {
            if (channel.contains(blocked)) {
                punishClient(data, "Channel", "channel=" + channel);
                return;
            }
        }
    }

    private void punishClient(PlayerData data, String type, String debug) {
        if (flag(data, type, debug)) {
            Player player = data.player();
            Bukkit.getScheduler().runTask(plugin, () -> player.kick(plugin.messages().lines("kick-screen",
                    "reason", "Disallowed client modification")));
        }
    }

    private static List<String> lower(List<String> values) {
        return values.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
    }
}
