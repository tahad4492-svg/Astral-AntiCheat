package me.mrtahadarvish.astral.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/** Global options from config.yml. */
public final class Settings {

    public boolean lagProtection;
    public double minTps;
    public int maxCombatPing;
    public int maxPingCompensation;
    public int joinExemptTicks;
    public boolean exemptBedrock;
    /** true = block cheats (cancel hits, setbacks ...), false = watch only (alerts + logs). */
    public boolean mitigation;
    public double maxSetbackDistance;
    /** Lower case names of worlds where nobody is checked. */
    public java.util.Set<String> exemptWorlds = java.util.Set.of();

    public boolean alertsByDefault;
    /** One grouped line per player instead of one line per flag. */
    public boolean alertCompact;
    public long alertGatherMs;
    public long alertIntervalMs;
    public long alertRepeatMs;
    public long alertQuietMs;
    public long alertCooldownMs;
    public boolean alertConsole;
    public String alertClickCommand;

    public int decayIntervalSeconds;

    public boolean punishEnabled;
    public boolean punishBroadcast;
    public String defaultBanDuration;

    public List<String> freezeQuitCommands;
    public List<String> freezeAllowedCommands;

    public boolean logFile;
    public int logMemorySize;

    public void load(FileConfiguration config) {
        lagProtection = config.getBoolean("settings.lag-protection.enabled", true);
        minTps = config.getDouble("settings.lag-protection.min-tps", 17.5);
        maxCombatPing = config.getInt("settings.max-combat-ping", 800);
        maxPingCompensation = config.getInt("settings.max-ping-compensation", 600);
        joinExemptTicks = config.getInt("settings.join-exempt-ticks", 60);
        exemptBedrock = config.getBoolean("settings.exempt-bedrock-players", true);
        mitigation = config.getBoolean("settings.mitigation", true);
        maxSetbackDistance = Math.max(1.0, config.getDouble("settings.max-setback-distance", 12.0));
        java.util.Set<String> worlds = new java.util.HashSet<>();
        for (String world : config.getStringList("settings.exempt-worlds")) {
            worlds.add(world.toLowerCase(java.util.Locale.ROOT));
        }
        exemptWorlds = worlds;

        alertsByDefault = config.getBoolean("alerts.enabled-by-default", true);
        alertCompact = config.getBoolean("alerts.compact", true);
        alertGatherMs = Math.max(0L, (long) (config.getDouble("alerts.gather-seconds", 1.0) * 1000.0));
        alertIntervalMs = Math.max(0L, (long) (config.getDouble("alerts.interval-seconds", 5.0) * 1000.0));
        alertRepeatMs = Math.max(alertIntervalMs, (long) (config.getDouble("alerts.repeat-seconds", 30.0) * 1000.0));
        alertQuietMs = Math.max(alertRepeatMs, (long) (config.getDouble("alerts.reset-after-seconds", 120.0) * 1000.0));
        alertCooldownMs = config.getLong("alerts.cooldown-ms", 400L);
        alertConsole = config.getBoolean("alerts.console", true);
        alertClickCommand = config.getString("alerts.click-command", "/astral tp %player%");

        decayIntervalSeconds = Math.max(1, config.getInt("violations.decay-interval-seconds", 30));

        punishEnabled = config.getBoolean("punishments.enabled", false);
        punishBroadcast = config.getBoolean("punishments.broadcast", false);
        defaultBanDuration = config.getString("punishments.default-ban-duration", "30d");

        freezeQuitCommands = config.getStringList("freeze.quit-commands");
        freezeAllowedCommands = config.getStringList("freeze.allowed-commands");

        logFile = config.getBoolean("logging.file", true);
        logMemorySize = Math.max(10, config.getInt("logging.memory-size", 150));
    }
}
