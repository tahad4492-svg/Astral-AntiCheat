package me.mrtahadarvish.astral.manager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects the flags of one player and decides when staff get one compact alert line for them,
 * so a flying cheater produces a few lines instead of hundreds.
 * <ul>
 *   <li>The first flags are gathered for {@code gatherMs}, then sent as one line.</li>
 *   <li>A cheat that was not reported yet is sent at most once per {@code intervalMs}.</li>
 *   <li>The same cheats again are only reminded every {@code repeatMs} (with the new count).</li>
 *   <li>After {@code quietMs} without flags everything counts as new again.</li>
 * </ul>
 * Pure logic (no Bukkit), so it is unit tested directly.
 */
public final class AlertDigest {

    /** One likely cheat in the next alert line. */
    public static final class Entry {
        public final String name;
        /** "Check(Type)" -> flags, in the order they were first seen. */
        public final Map<String, Integer> types = new LinkedHashMap<>();
        public int count;
        public double vl;
        public String lastDebug = "";

        Entry(String name) {
            this.name = name;
        }
    }

    private final Map<String, Entry> pending = new LinkedHashMap<>();
    private final Set<String> reported = new HashSet<>();
    private long lastSent = Long.MIN_VALUE / 4;
    private long firstPending;
    private long lastFlag = Long.MIN_VALUE / 4;

    public void add(String name, String checkType, double vl, String debug, long now, long quietMs) {
        if (now - lastFlag > quietMs) {
            reported.clear();
        }
        lastFlag = now;
        if (pending.isEmpty()) {
            firstPending = now;
        }
        Entry entry = pending.computeIfAbsent(name, Entry::new);
        entry.count++;
        entry.types.merge(checkType, 1, Integer::sum);
        entry.vl = Math.max(entry.vl, vl);
        entry.lastDebug = debug;
    }

    /** True when an alert line should be sent now. */
    public boolean due(long now, long gatherMs, long intervalMs, long repeatMs) {
        if (pending.isEmpty() || now - firstPending < gatherMs) {
            return false;
        }
        boolean fresh = false;
        for (String name : pending.keySet()) {
            if (!reported.contains(name)) {
                fresh = true;
                break;
            }
        }
        return now - lastSent >= (fresh ? intervalMs : repeatMs);
    }

    /** Takes everything gathered for the alert line that is being sent. Newly reported cheats first. */
    public List<Entry> drain(long now) {
        List<Entry> fresh = new ArrayList<>();
        List<Entry> known = new ArrayList<>();
        for (Entry entry : pending.values()) {
            (reported.contains(entry.name) ? known : fresh).add(entry);
        }
        fresh.addAll(known);
        for (Entry entry : fresh) {
            reported.add(entry.name);
        }
        pending.clear();
        lastSent = now;
        return fresh;
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }
}
