package me.mrtahadarvish.astral;

import me.mrtahadarvish.astral.check.CheckManager;
import me.mrtahadarvish.astral.command.AstralCommand;
import me.mrtahadarvish.astral.config.Messages;
import me.mrtahadarvish.astral.config.Settings;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.data.PlayerDataManager;
import me.mrtahadarvish.astral.gui.MenuManager;
import me.mrtahadarvish.astral.listener.DataListener;
import me.mrtahadarvish.astral.listener.InteractionListener;
import me.mrtahadarvish.astral.listener.MovementProcessor;
import me.mrtahadarvish.astral.manager.AlertManager;
import me.mrtahadarvish.astral.manager.BypassManager;
import me.mrtahadarvish.astral.manager.FreezeManager;
import me.mrtahadarvish.astral.manager.LogManager;
import me.mrtahadarvish.astral.manager.ViolationManager;
import me.mrtahadarvish.astral.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Astral anti-cheat for Paper 1.21.11, by MrTahaDarvish.
 */
public final class AstralAntiCheat extends JavaPlugin {

    public static final String DISPLAY_NAME = "Astral";
    public static final String AUTHOR = "MrTahaDarvish";

    private Settings settings;
    private Messages messages;
    private LogManager logs;
    private BypassManager bypass;
    private PlayerDataManager data;
    private ViolationManager violations;
    private AlertManager alerts;
    private FreezeManager freeze;
    private CheckManager checks;
    private MenuManager menus;
    private long startTime;
    private int secondsCounter;

    @Override
    public void onEnable() {
        startTime = System.currentTimeMillis();
        saveDefaultConfig();
        settings = new Settings();
        settings.load(getConfig());
        messages = new Messages(this);
        messages.load();

        logs = new LogManager(this);
        bypass = new BypassManager(this);
        bypass.load();
        data = new PlayerDataManager(this);
        violations = new ViolationManager(this);
        alerts = new AlertManager(this);
        freeze = new FreezeManager(this);
        menus = new MenuManager(this);
        checks = new CheckManager(this);
        checks.registerAll();

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new DataListener(this), this);
        pluginManager.registerEvents(new MovementProcessor(this), this);
        pluginManager.registerEvents(new InteractionListener(this), this);
        pluginManager.registerEvents(freeze, this);
        pluginManager.registerEvents(menus, this);

        PluginCommand command = getCommand("astral");
        if (command != null) {
            AstralCommand executor = new AstralCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        // support /reload and plugin managers: create data for players that are already online
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData created = data.create(player);
            created.lastSafe = player.getLocation();
        }

        Bukkit.getScheduler().runTaskTimer(this, data::tick, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, this::everySecond, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, freeze::tick, 60L, 60L);
        Bukkit.getScheduler().runTaskTimer(this, this::cpsDisplay, 5L, 5L);

        Bukkit.getConsoleSender().sendMessage(Text.color("&8&m                                                  "));
        Bukkit.getConsoleSender().sendMessage(Text.color(" &#00E5FF&l" + DISPLAY_NAME + " &7v" + getPluginMeta().getVersion()));
        Bukkit.getConsoleSender().sendMessage(Text.color(" &7Loaded &f" + checks.all().size() + " &7checks &8(&a"
                + checks.enabledCount() + " enabled&8) &7for &f" + Bukkit.getName() + " " + Bukkit.getMinecraftVersion()));
        Bukkit.getConsoleSender().sendMessage(Text.color(" &7Mode: " + (settings.mitigation ? "&cBLOCK &7(cheats are blocked)" : "&aWATCH &7(alerts only, nobody is kicked or blocked)")));
        Bukkit.getConsoleSender().sendMessage(Text.color(" &7Bypass permission: &fastral.bypass &8| &7Command: &f/astral"));
        Bukkit.getConsoleSender().sendMessage(Text.color("&8&m                                                  "));
    }

    @Override
    public void onDisable() {
        if (bypass != null) {
            bypass.save();
        }
        if (logs != null) {
            logs.shutdown();
        }
    }

    /** /astral reload */
    public void reload() {
        reloadConfig();
        settings.load(getConfig());
        messages.load();
        checks.reload();
        bypass.load();
        for (PlayerData playerData : data.all()) {
            data.refreshBypass(playerData);
        }
    }

    private void everySecond() {
        secondsCounter++;
        if (secondsCounter % settings.decayIntervalSeconds == 0) {
            violations.decay();
        }
    }

    private void cpsDisplay() {
        for (PlayerData staff : data.all()) {
            if (staff.cpsTarget == null) {
                continue;
            }
            Player target = Bukkit.getPlayer(staff.cpsTarget);
            PlayerData targetData = target == null ? null : data.get(target);
            if (targetData == null) {
                staff.cpsTarget = null;
                continue;
            }
            int cps = targetData.cps();
            String color = cps >= 16 ? "&c" : cps >= 11 ? "&e" : "&a";
            staff.player().sendActionBar(Text.color("&#00E5FFAstral &8» &f" + target.getName() + " &7CPS: " + color + cps
                    + " &8| &7Ping: &f" + targetData.ping() + "ms"));
        }
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public LogManager logs() {
        return logs;
    }

    public BypassManager bypass() {
        return bypass;
    }

    public PlayerDataManager data() {
        return data;
    }

    public ViolationManager violations() {
        return violations;
    }

    public AlertManager alerts() {
        return alerts;
    }

    public FreezeManager freeze() {
        return freeze;
    }

    public CheckManager checks() {
        return checks;
    }

    public MenuManager menus() {
        return menus;
    }

    public long startTime() {
        return startTime;
    }
}
