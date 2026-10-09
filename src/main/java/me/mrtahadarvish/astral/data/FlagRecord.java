package me.mrtahadarvish.astral.data;

/** One flag, kept in memory for /astral logs. */
public record FlagRecord(long time, String check, String type, double vl, String debug) {
}
