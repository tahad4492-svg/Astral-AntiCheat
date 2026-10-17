package me.mrtahadarvish.astral.check;

import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.data.PlayerData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.List;

/**
 * Base class of every check. A check listens to Bukkit / Paper events (and / or the client
 * tick through {@link TickCheck}) and calls {@link #flag} when it detects a cheat.
 */
public abstract class Check implements Listener {

    protected final AstralAntiCheat plugin;
    private final String id;
    private final String name;
    private final CheckCategory category;
    private final String description;

    private boolean enabled = true;
    private double alertVl;
    private boolean mitigate;
    private double mitigateVl;
    private boolean punish;
    private double punishVl;
    private List<String> punishCommands = List.of();
    private double decay;
    private long totalFlags;

    protected ConfigurationSection section = new MemoryConfiguration();

    protected Check(AstralAntiCheat plugin, String id, String name, CheckCategory category, String description) {
        this.plugin = plugin;
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
    }

    public final void load(ConfigurationSection root) {
        ConfigurationSection loaded = root.getConfigurationSection("checks." + id);
        section = loaded != null ? loaded : new MemoryConfiguration();
        enabled = section.getBoolean("enabled", true);
        alertVl = section.getDouble("alert-vl", 1.0);
        mitigate = section.getBoolean("mitigate", true);
        mitigateVl = section.getDouble("mitigate-vl", 0.0);
        punish = section.getBoolean("punish", false);
        punishVl = section.getDouble("punish-vl", 20.0);
        punishCommands = section.getStringList("punish-commands");
        decay = section.getDouble("decay", 1.0);
        onLoad();
    }

    /** Read check specific options here. */
    protected void onLoad() {
    }

    protected double option(String key, double def) {
        return section.getDouble(key, def);
    }

    protected int option(String key, int def) {
        return section.getInt(key, def);
    }

    protected boolean option(String key, boolean def) {
        return section.getBoolean(key, def);
    }

    protected List<String> optionList(String key) {
        return section.getStringList(key);
    }

    protected PlayerData data(Player player) {
        return plugin.data().get(player);
    }

    /**
     * True when this check has to look at the player: check enabled, player not bypassing
     * (astral.bypass / astral.bypass.&lt;check&gt; / /astral bypass), not exempted for a while
     * (/astral exempt), not in an exempt world, not a Bedrock player and not just joined.
     */
    protected boolean shouldCheck(PlayerData data) {
        if (!enabled || data == null) {
            return false;
        }
        if (data.isBypassing(id)) {
            return false;
        }
        if (data.bedrock && plugin.settings().exemptBedrock) {
            return false;
        }
        if (data.isExempt(category)) {
            return false; // /astral exempt
        }
        if (!plugin.settings().exemptWorlds.isEmpty()
                && plugin.settings().exemptWorlds.contains(data.player().getWorld().getName().toLowerCase(java.util.Locale.ROOT))) {
            return false;
        }
        return PlayerData.since(data.joinTick) > plugin.settings().joinExemptTicks;
    }

    /** Flags with 1 VL. Returns true when the cheat should be blocked (mitigation). */
    protected boolean flag(PlayerData data, String type, String debug) {
        return flag(data, type, 1.0, debug);
    }

    protected boolean flag(PlayerData data, String type, double vl, String debug) {
        return plugin.violations().flag(this, data, type, vl, debug);
    }

    /** Teleports the player back to the last legit position. */
    protected void setback(PlayerData data) {
        plugin.violations().setback(data);
    }

    public void incrementFlags() {
        totalFlags++;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public CheckCategory category() {
        return category;
    }

    public String description() {
        return description;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double alertVl() {
        return alertVl;
    }

    /** True when this check may block cheats (also needs settings.mitigation, off = watch only). */
    public boolean mitigate() {
        return mitigate && plugin.settings().mitigation;
    }

    public double mitigateVl() {
        return mitigateVl;
    }

    public boolean punish() {
        return punish;
    }

    public double punishVl() {
        return punishVl;
    }

    public List<String> punishCommands() {
        return punishCommands;
    }

    public double decay() {
        return decay;
    }

    public long totalFlags() {
        return totalFlags;
    }
}
