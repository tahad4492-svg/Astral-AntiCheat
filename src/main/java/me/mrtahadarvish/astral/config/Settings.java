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
    public boolean setbacks;

    public boolean alertsByDefault;
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
        setbacks = config.getBoolean("settings.setbacks", true);

        alertsByDefault = config.getBoolean("alerts.enabled-by-default", true);
        alertCooldownMs = config.getLong("alerts.cooldown-ms", 400L);
        alertConsole = config.getBoolean("alerts.console", true);
        alertClickCommand = config.getString("alerts.click-command", "/astral tp %player%");

        decayIntervalSeconds = Math.max(1, config.getInt("violations.decay-interval-seconds", 30));

        punishEnabled = config.getBoolean("punishments.enabled", true);
        punishBroadcast = config.getBoolean("punishments.broadcast", true);
        defaultBanDuration = config.getString("punishments.default-ban-duration", "30d");

        freezeQuitCommands = config.getStringList("freeze.quit-commands");
        freezeAllowedCommands = config.getStringList("freeze.allowed-commands");

        logFile = config.getBoolean("logging.file", true);
        logMemorySize = Math.max(10, config.getInt("logging.memory-size", 100));
    }
}
