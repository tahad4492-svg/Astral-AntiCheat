package me.mrtahadarvish.astral.data;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.util.ClientUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerDataManager {

    private final AstralAntiCheat plugin;
    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();

    public PlayerDataManager(AstralAntiCheat plugin) {
        this.plugin = plugin;
    }

    public PlayerData create(Player player) {
        PlayerData created = new PlayerData(player);
        created.bedrock = ClientUtil.isBedrock(player.getUniqueId());
        created.alerts = player.hasPermission("astral.alerts") && plugin.settings().alertsByDefault;
        data.put(player.getUniqueId(), created);
        refreshBypass(created);
        return created;
    }

    public PlayerData get(Player player) {
        return data.get(player.getUniqueId());
    }

    public PlayerData get(UUID uuid) {
        return data.get(uuid);
    }

    public PlayerData remove(Player player) {
        return data.remove(player.getUniqueId());
    }

    public Collection<PlayerData> all() {
        return data.values();
    }

    /** Re-reads astral.bypass, astral.bypass.<check> and the /astral bypass list. */
    public void refreshBypass(PlayerData playerData) {
        Player player = playerData.player();
        playerData.bypassPermission = player.hasPermission("astral.bypass")
                || plugin.bypass().contains(player.getUniqueId());
        Set<String> checks = new HashSet<>();
        for (Check check : plugin.checks().all()) {
            if (player.hasPermission("astral.bypass." + check.id())) {
                checks.add(check.id());
            }
        }
        playerData.bypassChecks = checks;
    }

    /** Boxes of every player for lag compensation (end of tick, labelled with the next tick). */
    public void recordHistory(int tick) {
        for (PlayerData playerData : data.values()) {
            Player player = playerData.player();
            if (player.isOnline()) {
                playerData.history.add(tick, player.getBoundingBox());
            }
        }
    }

    /** Runs every server tick. */
    public void tick() {
        int tick = Bukkit.getCurrentTick();
        boolean refresh = tick % 20 == 0;
        for (PlayerData playerData : data.values()) {
            Player player = playerData.player();
            if (!player.isOnline()) {
                continue;
            }
            if (refresh) {
                refreshBypass(playerData);
            }
        }
    }
}
