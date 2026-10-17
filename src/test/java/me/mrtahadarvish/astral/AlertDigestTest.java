package me.mrtahadarvish.astral;

import me.mrtahadarvish.astral.manager.AlertDigest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compact alerts: a cheater flagging all the time must produce a handful of lines, not hundreds. */
class AlertDigestTest {

    private static final long GATHER = 1_000;
    private static final long INTERVAL = 5_000;
    private static final long REPEAT = 30_000;
    private static final long QUIET = 120_000;

    /** Runs the alert task every 500 ms like the plugin, returns the lines that were sent. */
    private static List<List<AlertDigest.Entry>> run(AlertDigest digest, long from, long to, long flagEvery, String... cheats) {
        List<List<AlertDigest.Entry>> lines = new ArrayList<>();
        for (long now = from; now < to; now += 50) {
            if ((now - from) % flagEvery == 0) {
                for (String cheat : cheats) {
                    digest.add(cheat, cheat + " Type", 5.0, "debug", now, QUIET);
                }
            }
            if (now % 500 == 0 && digest.due(now, GATHER, INTERVAL, REPEAT)) {
                lines.add(digest.drain(now));
            }
        }
        return lines;
    }

    @Test
    void flyingCheaterGivesAFewLinesNotHundreds() {
        AlertDigest digest = new AlertDigest();
        // a flag every 100 ms for one minute = 600 flags
        List<List<AlertDigest.Entry>> lines = run(digest, 0, 60_000, 100, "Fly");
        int flags = lines.stream().mapToInt(line -> line.getFirst().count).sum();
        Report.add("ALERTS", "600 Fly flags in one minute -> " + lines.size() + " alert lines",
                "lines=" + lines.size() + " flags shown=" + flags, lines.size() <= 3);
        assertTrue(lines.size() >= 2 && lines.size() <= 3, "lines=" + lines.size());
        assertTrue(lines.getFirst().getFirst().count >= 9, "the first line gathers the first second of flags");
    }

    @Test
    void aNewCheatIsReportedQuicklyAndFirst() {
        AlertDigest digest = new AlertDigest();
        List<List<AlertDigest.Entry>> first = run(digest, 0, 10_000, 100, "Fly");
        assertEquals(1, first.size());
        // he starts using a kill aura too: reported within interval-seconds, listed first
        List<List<AlertDigest.Entry>> next = run(digest, 10_000, 12_000, 100, "KillAura (silent rotations)", "Fly");
        assertEquals(1, next.size(), "new cheat -> one new line");
        assertEquals("KillAura (silent rotations)", next.getFirst().getFirst().name);
        Report.add("ALERTS", "New cheat while flagging: reported at once, listed first",
                next.getFirst().getFirst().name + " x" + next.getFirst().getFirst().count, true);
    }

    @Test
    void quietPlayerStartsFresh() {
        AlertDigest digest = new AlertDigest();
        run(digest, 0, 5_000, 200, "Speed");
        List<List<AlertDigest.Entry>> later = run(digest, 200_000, 202_000, 200, "Speed");
        assertEquals(1, later.size(), "after a quiet time the same cheat is reported again");
    }
}
