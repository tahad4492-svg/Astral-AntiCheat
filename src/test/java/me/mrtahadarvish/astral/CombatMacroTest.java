package me.mrtahadarvish.astral;

import me.mrtahadarvish.astral.sim.Sim;
import me.mrtahadarvish.astral.sim.SimPlayer;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Weapon swap macros and mace exploits of 1.21 cheat clients, with blocking on (default): the
 * cheater must be flagged with the right name AND his hits must stop registering. Legit fast
 * players doing the same tricks by hand must not be flagged or blocked.
 */
class CombatMacroTest {

    private static final int SWORD = 0;
    private static final int MACE = 1;
    private static final int AXE = 2;

    private static Sim arena() {
        return new Sim().floor(30);
    }

    private static SimPlayer fighter(Sim sim, String name) {
        SimPlayer p = sim.join(name, 0.5, 64, 0.5);
        p.hotbar[SWORD] = Material.NETHERITE_SWORD;
        p.hotbar[MACE] = Material.MACE;
        p.hotbar[AXE] = Material.NETHERITE_AXE;
        p.slot = SWORD;
        return p;
    }

    /** Looks straight at the victim standing 2.2 blocks in front (yaw 0 = +z). */
    private static SimPlayer victim(Sim sim, String name) {
        return sim.join(name, 0.5, 64, 2.7);
    }

    private static void aim(SimPlayer attacker, SimPlayer victim) {
        double[] r = SimPlayer.rotationTo(attacker.eyeClient(), new Vector(victim.sx, victim.sy + 1.2, victim.sz));
        attacker.yaw = (float) r[0];
        attacker.pitch = (float) r[1];
    }

    private static void report(Sim sim, SimPlayer cheater, String scenario, boolean ok) {
        Report.add("CHEAT", scenario, sim.summary(cheater), ok);
    }

    // ------------------------------------------------------------------ cheats

    @Test
    void autoAttributeSwapIsCaughtAndHitsStopCounting() {
        // Meteor style: swap to the mace in the same tick as the hit, swap back 2 ticks later
        Sim sim = arena();
        SimPlayer a = fighter(sim, "AttrSwap");
        SimPlayer v = victim(sim, "AttrVictim");
        sim.idle(5);
        int[] registered = {0, 0};
        int flaggedAt = -1;
        for (int hit = 0; hit < 40; hit++) {
            int h = hit;
            sim.tick(pl -> {
                if (pl == a) {
                    aim(pl, v);
                    sim.slot(pl, MACE);
                    if (sim.attack(pl, v.player)) {
                        registered[h < 20 ? 0 : 1]++;
                    }
                    sim.swing(pl);
                }
            });
            sim.idle(2);
            sim.tick(pl -> {
                if (pl == a) {
                    sim.slot(pl, SWORD);
                }
            });
            sim.idle(9);
            if (flaggedAt < 0 && sim.flagged(a, "SilentSwap", "AttributeSwap")) {
                flaggedAt = hit;
            }
        }
        boolean caught = sim.flagged(a, "SilentSwap", "AttributeSwap");
        report(sim, a, "Auto attribute swap (swap in the hit tick, swap back 2 ticks later), hits blocked after "
                + flaggedAt + " hits, last 20 hits registered " + registered[1], caught && registered[1] == 0);
        assertTrue(caught, sim.summary(a));
        assertEquals(0, registered[1], "hits after the detection must not register");
    }

    @Test
    void switchHitSwitchBackInOneTickIsBlockedAtOnce() {
        // swap to the mace, hit, swap back - all in one tick (impossible for vanilla)
        Sim sim = arena();
        SimPlayer a = fighter(sim, "SameTickSwap");
        SimPlayer v = victim(sim, "SameTickVictim");
        sim.idle(5);
        int cancelledSwaps = 0;
        int secondHits = 0;
        for (int i = 0; i < 10; i++) {
            int[] result = {0, 0};
            sim.tick(pl -> {
                if (pl == a) {
                    aim(pl, v);
                    sim.attack(pl, v.player);
                    sim.swing(pl);
                    if (!sim.slot(pl, MACE)) {
                        result[0]++;
                    }
                    if (sim.attack(pl, v.player)) {
                        result[1]++;
                    }
                    sim.swing(pl);
                }
            });
            cancelledSwaps += result[0];
            secondHits += result[1];
            sim.idle(10);
        }
        boolean caught = sim.flagged(a, "SilentSwap");
        report(sim, a, "Silent swap in one tick: swaps cancelled " + cancelledSwaps + "/10, mace hits registered " + secondHits,
                caught && cancelledSwaps == 10 && secondHits == 0);
        assertTrue(caught, sim.summary(a));
        assertEquals(10, cancelledSwaps, "the impossible swap is cancelled");
        assertEquals(0, secondHits, "hits after the impossible swap do not register");
    }

    @Test
    void autoStunSlamSameTickIsBlocked() {
        Sim sim = arena();
        SimPlayer a = fighter(sim, "StunSlammer");
        SimPlayer v = victim(sim, "ShieldUser");
        v.blocking = true;
        sim.idle(5);
        int maceHits = 0;
        for (int i = 0; i < 6; i++) {
            int[] result = {0};
            sim.tick(pl -> {
                if (pl == a) {
                    aim(pl, v);
                    sim.slot(pl, AXE);
                    sim.attack(pl, v.player); // breaks the shield
                    sim.swing(pl);
                    sim.slot(pl, MACE);
                    if (sim.attack(pl, v.player)) {
                        result[0]++;
                    }
                    sim.swing(pl);
                    sim.slot(pl, SWORD);
                }
            });
            maceHits += result[0];
            a.slot = SWORD;
            sim.idle(30);
        }
        boolean caught = sim.flagged(a, "SilentSwap", "StunSlam");
        report(sim, a, "Auto stun slam (axe + mace in one tick), mace hits registered " + maceHits + "/6", caught && maceHits == 0);
        assertTrue(caught, sim.summary(a));
        assertEquals(0, maceHits, "the mace smash after the impossible swap must not register");
    }

    @Test
    void autoStunSlamOneTickLaterIsAlerted() {
        Sim sim = arena();
        SimPlayer a = fighter(sim, "StunSlam1t");
        SimPlayer v = victim(sim, "ShieldUser2");
        v.blocking = true;
        sim.idle(5);
        for (int i = 0; i < 12; i++) {
            sim.tick(pl -> {
                if (pl == a) {
                    aim(pl, v);
                    sim.slot(pl, AXE);
                    sim.attack(pl, v.player);
                    sim.swing(pl);
                }
            });
            sim.tick(pl -> {
                if (pl == a) {
                    sim.slot(pl, MACE);
                    sim.attack(pl, v.player);
                    sim.swing(pl);
                }
            });
            sim.idle(2);
            sim.tick(pl -> {
                if (pl == a) {
                    sim.slot(pl, SWORD);
                }
            });
            sim.idle(25);
        }
        boolean caught = sim.flagged(a, "SilentSwap", "StunSlam");
        report(sim, a, "Auto stun slam (mace exactly 1 tick after the axe, every time)", caught);
        assertTrue(caught, sim.summary(a));
    }

    @Test
    void maceKillFakeFallIsCancelled() {
        // up and down position packets right before the hit: the server thinks he fell 10 blocks
        Sim sim = arena();
        SimPlayer a = fighter(sim, "MaceKill");
        SimPlayer v = victim(sim, "MaceVictim");
        a.slot = MACE;
        sim.idle(5);
        int registered = 0;
        for (int i = 0; i < 6; i++) {
            int[] result = {0};
            sim.tick(pl -> {
                if (pl == a) {
                    aim(pl, v);
                    pl.sendRawPosition(pl.sx, pl.sy + 10.0, pl.sz, pl.yaw, pl.pitch, false);
                    pl.sendRawPosition(pl.sx, pl.sy - 10.0, pl.sz, pl.yaw, pl.pitch, false);
                    if (sim.attack(pl, v.player)) {
                        result[0]++;
                    }
                    sim.swing(pl);
                    pl.sFall = 0.0; // the server resets it when the smash lands
                }
            });
            registered += result[0];
            sim.idle(20);
        }
        boolean caught = sim.flagged(a, "Mace", "FakeFall");
        report(sim, a, "Mace kill (fake 10 block fall before the hit), smashes registered " + registered + "/6",
                caught && registered == 0);
        assertTrue(caught, sim.summary(a));
        assertEquals(0, registered, "fake fall smashes must not register");
    }

    // ------------------------------------------------------------------ legit

    @Test
    void legitMaceSmashFromAPillar() {
        Sim sim = arena();
        sim.world.fill(0, 64, 0, 0, 72, 0, Material.STONE);
        SimPlayer a = sim.join("MaceLegit", 0.5, 73, 0.5);
        a.hotbar[MACE] = Material.MACE;
        a.slot = MACE;
        SimPlayer v = sim.join("MaceTarget", 0.5, 64, 3.2);
        sim.idle(5);
        boolean[] hit = {false};
        boolean[] registered = {false};
        for (int t = 0; t < 120 && !hit[0]; t++) {
            sim.tick(pl -> {
                if (pl != a) {
                    return;
                }
                pl.forward = 1; // walks off the pillar towards the target
                aim(pl, v);
                pl.pitch = Math.max(pl.pitch, 10.0F);
                if (!pl.onGround && pl.sFall > 3.0 && pl.eyeServer().distance(new Vector(v.sx, v.sy + 1.0, v.sz)) < 3.0) {
                    aim(pl, v);
                    hit[0] = true;
                    registered[0] = sim.attack(pl, v.player);
                    sim.swing(pl);
                }
            });
        }
        sim.idle(20);
        String summary = sim.summary(a);
        boolean ok = hit[0] && registered[0] && sim.flags(a).isEmpty();
        Report.add("LEGIT", "mace smash after falling off a 9 block pillar", summary + (hit[0] ? "" : " (no hit)"), ok);
        assertTrue(hit[0], "the attacker should have reached the target");
        assertTrue(registered[0], "a real smash must register -> " + summary);
        assertTrue(sim.flags(a).isEmpty(), summary);
    }

    @Test
    void legitManualAttributeSwapping() {
        // a human: the swap lands in the same tick as the click about half of the time and the
        // swap back comes after 2-7 ticks
        Sim sim = arena();
        SimPlayer a = fighter(sim, "HandSwapper");
        SimPlayer v = victim(sim, "HandVictim");
        Random random = new Random(7);
        sim.idle(5);
        int cancelled = 0;
        for (int hit = 0; hit < 60; hit++) {
            boolean sameTick = random.nextDouble() < 0.5;
            if (!sameTick) {
                sim.tick(pl -> {
                    if (pl == a) {
                        sim.slot(pl, MACE);
                    }
                });
            }
            int[] result = {0};
            sim.tick(pl -> {
                if (pl == a) {
                    aim(pl, v);
                    if (sameTick) {
                        sim.slot(pl, MACE);
                    }
                    if (!sim.attack(pl, v.player)) {
                        result[0]++;
                    }
                    sim.swing(pl);
                }
            });
            cancelled += result[0];
            sim.idle(2 + random.nextInt(6));
            sim.tick(pl -> {
                if (pl == a) {
                    sim.slot(pl, SWORD);
                }
            });
            sim.idle(6 + random.nextInt(8));
        }
        boolean ok = sim.flags(a).isEmpty() && cancelled == 0;
        Report.add("LEGIT", "attribute swapping by hand (60 hits)", sim.summary(a) + " cancelled=" + cancelled, ok);
        assertTrue(sim.flags(a).isEmpty(), sim.summary(a));
        assertEquals(0, cancelled);
    }

    @Test
    void legitFastStunSlams() {
        // a very fast player: mace hit 1-3 ticks after the axe broke the shield
        Sim sim = arena();
        SimPlayer a = fighter(sim, "FastSlammer");
        SimPlayer v = victim(sim, "ShieldUser3");
        v.blocking = true;
        Random random = new Random(11);
        sim.idle(5);
        int cancelled = 0;
        for (int combo = 0; combo < 30; combo++) {
            sim.tick(pl -> {
                if (pl == a) {
                    aim(pl, v);
                    sim.slot(pl, AXE);
                    sim.attack(pl, v.player);
                    sim.swing(pl);
                }
            });
            double r = random.nextDouble();
            int delay = r < 0.4 ? 1 : r < 0.8 ? 2 : 3;
            sim.idle(delay - 1);
            int[] result = {0};
            sim.tick(pl -> {
                if (pl == a) {
                    sim.slot(pl, MACE);
                    if (!sim.attack(pl, v.player)) {
                        result[0]++;
                    }
                    sim.swing(pl);
                }
            });
            cancelled += result[0];
            sim.idle(3 + random.nextInt(4));
            sim.tick(pl -> {
                if (pl == a) {
                    sim.slot(pl, SWORD);
                }
            });
            sim.idle(20 + random.nextInt(20));
        }
        boolean ok = sim.flags(a).isEmpty() && cancelled == 0;
        Report.add("LEGIT", "fast stun slams by hand (30 combos, 1-3 ticks)", sim.summary(a) + " cancelled=" + cancelled, ok);
        assertTrue(sim.flags(a).isEmpty(), sim.summary(a));
        assertEquals(0, cancelled);
    }
}
