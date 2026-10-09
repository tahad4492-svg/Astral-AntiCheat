package me.mrtahadarvish.astral;

import me.mrtahadarvish.astral.sim.Sim;
import me.mrtahadarvish.astral.sim.SimPlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SmokeTest {

    @Test
    void walkingIsClean() {
        Sim sim = new Sim().floor(80);
        SimPlayer p = sim.join("Walker", 0.5, 64, 0.5);
        sim.idle(5);
        for (int i = 0; i < 200; i++) {
            sim.tick(player -> {
                player.forward = 1;
                player.sprinting = true;
            });
        }
        System.out.println("walker: " + sim.summary(p) + " pos=" + p.sx + "," + p.sy + "," + p.sz);
        assertTrue(sim.flags(p).isEmpty(), sim.summary(p));
    }
}
