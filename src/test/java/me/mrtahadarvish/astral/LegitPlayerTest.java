package me.mrtahadarvish.astral;

import me.mrtahadarvish.astral.sim.Human;
import me.mrtahadarvish.astral.sim.Sim;
import me.mrtahadarvish.astral.sim.SimPlayer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Normal players doing normal things. Astral must not flag any of them.
 */
class LegitPlayerTest {

    private static void assertClean(Sim sim, SimPlayer player, String scenario) {
        String summary = sim.summary(player);
        Report.add("LEGIT", scenario, summary, sim.flags(player).isEmpty());
        assertTrue(sim.flags(player).isEmpty(), scenario + " -> " + summary);
    }

    /** A world with slabs, 1 and 2 block walls, a pit and an ice tunnel. */
    private static Sim terrain() {
        Sim sim = new Sim().floor(40);
        var w = sim.world;
        // a fence around the arena so nobody walks off the edge
        w.fill(-40, 64, -40, 40, 66, -40, Material.STONE);
        w.fill(-40, 64, 40, 40, 66, 40, Material.STONE);
        w.fill(-40, 64, -40, -40, 66, 40, Material.STONE);
        w.fill(40, 64, -40, 40, 66, 40, Material.STONE);
        w.fill(4, 64, -6, 8, 64, -2, Material.STONE_SLAB);          // slabs to step on
        w.fill(-8, 64, 4, -8, 64, 12, Material.STONE);              // 1 block wall
        w.fill(10, 64, 10, 14, 65, 10, Material.STONE);             // 2 block wall
        w.fill(-14, 63, -14, -10, 63, -10, Material.AIR);           // pit
        w.fill(-14, 59, -14, -10, 59, -10, Material.STONE);         // pit floor (3 deep)
        w.fill(15, 63, -10, 25, 63, -6, Material.PACKED_ICE);       // ice tunnel floor
        w.fill(15, 66, -10, 25, 66, -6, Material.STONE);            // ice tunnel ceiling (2 high)
        w.fill(0, 64, 15, 2, 66, 17, Material.STONE);               // pillar
        return sim;
    }

    @ParameterizedTest(name = "random wandering seed {0}")
    @ValueSource(longs = {1, 2, 3, 4, 5})
    void wanderingOnTerrain(long seed) {
        Sim sim = terrain();
        SimPlayer p = sim.join("Wanderer" + seed, 0.5, 64, 0.5);
        Random random = new Random(seed);
        int[] change = {0};
        sim.idle(3);
        for (int i = 0; i < 4000; i++) {
            sim.tick(pl -> {
                if (--change[0] <= 0) {
                    change[0] = 5 + random.nextInt(40);
                    pl.forward = random.nextInt(4) == 0 ? 0 : (random.nextInt(6) == 0 ? -1 : 1);
                    pl.strafe = random.nextInt(3) - 1;
                    pl.sneaking = random.nextInt(10) == 0;
                    pl.sprinting = pl.forward > 0 && !pl.sneaking && random.nextBoolean();
                    pl.jump = random.nextInt(3) == 0;
                }
                if (random.nextInt(4) == 0) {
                    pl.mouseTurn(random.nextInt(81) - 40, random.nextInt(21) - 10);
                }
                // walk back to the middle when far away
                if (Math.hypot(pl.x, pl.z) > 32) {
                    double[] r = SimPlayer.rotationTo(new Vector(pl.x, 0, pl.z), new Vector(0, 0, 0));
                    pl.yaw = (float) r[0];
                    pl.forward = 1;
                }
            });
        }
        assertClean(sim, p, "random wandering (seed " + seed + ")");
    }

    @Test
    void bunnyHopping() {
        Sim sim = new Sim().floor(60);
        SimPlayer p = sim.join("Bhop", 0.5, 64, -50.5);
        sim.idle(3);
        for (int i = 0; i < 600; i++) {
            int t = i;
            sim.tick(pl -> {
                pl.forward = 1;
                pl.sprinting = true;
                pl.jump = true;
                if (t % 40 == 0) {
                    pl.mouseTurn(30, 0);
                }
            });
        }
        assertClean(sim, p, "sprint jumping non stop");
    }

    @Test
    void iceTunnelBoosting() {
        Sim sim = new Sim().floor(60);
        sim.world.fill(-2, 63, -50, 2, 63, 50, Material.PACKED_ICE);
        sim.world.fill(-2, 66, -50, 2, 66, 50, Material.STONE);
        SimPlayer p = sim.join("IceRunner", 0.5, 64, -45.5);
        sim.idle(3);
        for (int i = 0; i < 300; i++) {
            sim.tick(pl -> {
                pl.forward = 1;
                pl.sprinting = true;
                pl.jump = true;
            });
        }
        assertClean(sim, p, "ice tunnel jump boosting (" + String.format("%.2f", p.z + 45.5) + " blocks)");
    }

    @Test
    void longFall() {
        Sim sim = new Sim().floor(20);
        sim.world.fill(-1, 90, -1, 1, 90, 1, Material.STONE);
        SimPlayer p = sim.join("Faller", 0.5, 91, 0.5);
        sim.idle(3);
        for (int i = 0; i < 80; i++) {
            sim.tick(pl -> pl.forward = 1);
        }
        assertClean(sim, p, "walking off a 26 block tower");
    }

    @ParameterizedTest(name = "pvp fight seed {0}")
    @ValueSource(longs = {11, 12, 13})
    void humanPvpFight(long seed) {
        Sim sim = new Sim().floor(60);
        SimPlayer a = sim.join("FighterA" + seed, 0.5, 64, 0.5);
        SimPlayer b = sim.join("FighterB" + seed, 0.5, 64, 3.5);
        a.yaw = 0;
        b.yaw = 180;
        Human ha = new Human(a, 9 + seed % 4, seed);
        Human hb = new Human(b, 8 + seed % 3, seed * 7);
        sim.idle(3);
        fight(sim, ha, hb, 1500);
        Report.note("pvp seed " + seed + ": A hits=" + ha.hits + " misses=" + ha.misses + ", B hits=" + hb.hits + " misses=" + hb.misses);
        assertClean(sim, a, "human PvP fight, attacker (seed " + seed + ")");
        assertClean(sim, b, "human PvP fight, defender (seed " + seed + ")");
    }

    /** Two humans fighting: mouse aim, human clicks, strafing, jumps, knockback, hotbar switches. */
    static void fight(Sim sim, Human ha, Human hb, int ticks) {
        Human[] humans = {ha, hb};
        for (int i = 0; i < ticks; i++) {
            for (Human h : humans) {
                Human other = h == ha ? hb : ha;
                h.record(other.self);
            }
            for (Human h : humans) {
                Human other = h == ha ? hb : ha;
                SimPlayer self = h.self;
                sim.clientTick(self, pl -> {
                    h.applyVelocity();
                    if (h.random().nextDouble() < 0.01) {
                        sim.slot(pl, h.random().nextInt(9));
                    }
                    h.aim(other.self);
                    h.move(other.self);
                    int clicks = h.clicksThisTick();
                    for (int c = 0; c < clicks; c++) {
                        if (h.click(sim, other.self) && other.hurtTime <= 0) {
                            other.hurtTime = 10;
                            Vector direction = new Vector(other.self.sx - pl.sx, 0, other.self.sz - pl.sz).normalize().multiply(0.4);
                            Vector kb = new Vector(other.self.mx / 2 + direction.getX(), other.self.sOnGround ? 0.4 : Math.min(0.0, other.self.my),
                                    other.self.mz / 2 + direction.getZ());
                            sim.velocity(other.self, kb);
                            other.queueVelocity(kb, 2);
                        }
                    }
                }, null);
            }
            for (Human h : humans) {
                h.hurtTime--;
            }
            sim.serverTick();
        }
    }

    @Test
    void inventoryUseStandingStill() {
        Sim sim = new Sim().floor(10);
        SimPlayer p = sim.join("Organizer", 0.5, 64, 0.5);
        sim.idle(3);
        p.releaseKeys();
        for (int i = 0; i < 200; i++) {
            int t = i;
            sim.tick(pl -> {
                if (t % 7 == 0) {
                    sim.click(pl, 9 + t % 27, ClickType.LEFT, InventoryAction.PICKUP_ALL, SimPlayer.item(Material.DIRT), null);
                }
            });
        }
        // totem pop, then the human opens the inventory and moves a totem after ~0.6s
        sim.totemPop(p);
        sim.idle(12);
        sim.click(p, 20, ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP, SimPlayer.item(Material.TOTEM_OF_UNDYING), null);
        sim.idle(5);
        assertClean(sim, p, "inventory sorting + manual totem");
    }

    @Test
    void buildingAndMining() {
        Sim sim = new Sim().floor(20);
        SimPlayer p = sim.join("Builder", 0.5, 64, 0.5);
        p.pitch = 60;
        sim.idle(3);
        // hold right click: vanilla places every 4 ticks. A small wall in front (top faces below the eyes)
        for (int i = 0; i < 80; i++) {
            int t = i;
            sim.tick(pl -> {
                if (t % 4 == 0 && t / 4 < 10) {
                    int column = t / 4 % 5 - 2;
                    int level = t / 4 / 5;
                    Block against = sim.world.block(column, 63 + level, 2);
                    double[] r = SimPlayer.rotationTo(pl.eyeClient(), new Vector(column + 0.5, 64 + level, 2.5));
                    pl.mouseAim(new Vector(column + 0.5, 64 + level, 2.5), 1.0, 0);
                    if (against.getType().isAir()) {
                        return;
                    }
                    sim.place(pl, against, BlockFace.UP, Material.OAK_PLANKS);
                    sim.swing(pl);
                }
            });
        }
        // mine the wall back down at vanilla speed (20 ticks per block)
        for (int index = 9; index >= 0; index--) {
            Block block = sim.world.block(index % 5 - 2, 64 + index / 5, 2);
            if (block.getType().isAir()) {
                continue;
            }
            int ticks = 0;
            sim.tick(pl -> {
                pl.mouseAim(new Vector(block.getX() + 0.5, block.getY() + 0.5, 2.5), 1.0, 0);
                sim.startDig(pl, block);
                sim.swing(pl);
            });
            while (++ticks < 20) {
                sim.tick(pl -> sim.swing(pl));
            }
            sim.tick(pl -> sim.breakBlock(pl, block));
            sim.idle(5);
        }
        assertClean(sim, p, "building a wall and mining it");
    }

    @Test
    void crystalPvpLegit() {
        Sim sim = new Sim().floor(20);
        SimPlayer p = sim.join("CrystalLegit", 0.5, 64, 0.5);
        sim.world.set(0, 64, 3, Material.OBSIDIAN);
        sim.idle(3);
        for (int round = 0; round < 10; round++) {
            Block obsidian = sim.world.block(0, 64, 3);
            EnderCrystal crystal = sim.crystal(obsidian);
            sim.tick(pl -> {
                double[] r = SimPlayer.rotationTo(pl.eyeClient(), new Vector(0.5, 65.5, 3.5));
                pl.yaw = (float) r[0];
                pl.pitch = (float) r[1];
                sim.placeCrystal(pl, obsidian, crystal);
                sim.swing(pl);
            });
            sim.idle(3); // the crystal arrives at the client after its ping
            sim.tick(pl -> {
                sim.attack(pl, crystal);
                sim.swing(pl);
            });
            sim.idle(8);
        }
        assertClean(sim, p, "crystal PvP at human speed");
    }

    @Test
    void eatingWhileWalking() {
        Sim sim = new Sim().floor(60);
        SimPlayer p = sim.join("Eater", 0.5, 64, -40.5);
        p.mainHand = Material.COOKED_BEEF;
        sim.idle(3);
        for (int i = 0; i < 400; i++) {
            int t = i;
            sim.tick(pl -> {
                pl.forward = 1;
                if (t % 60 == 0) {
                    pl.usingItem = true;
                    pl.useTicks = 0;
                    pl.sprinting = false;
                    sim.useItem(pl);
                } else if (pl.usingItem && pl.useTicks >= 32) {
                    pl.usingItem = false;
                } else if (!pl.usingItem) {
                    pl.sprinting = true;
                }
            });
        }
        assertClean(sim, p, "eating while walking");
    }

    @Test
    void scrollingHotbarAndHitting() {
        Sim sim = new Sim().floor(20);
        SimPlayer a = sim.join("Scroller", 0.5, 64, 0.5);
        SimPlayer b = sim.join("Dummy", 0.5, 64, 2.5);
        sim.idle(3);
        for (int i = 0; i < 300; i++) {
            int t = i;
            sim.clientTick(a, pl -> {
                if (t % 3 == 0) {
                    sim.slot(pl, (pl.slot + 1) % 9); // vanilla sends the slot before the attack
                }
                double[] r = SimPlayer.rotationTo(pl.eyeClient(), new Vector(0.5, 65.2, 2.5));
                pl.mouseAim(new Vector(0.5, 65.2, 2.5), 1.0, 0);
                if (t % 3 == 0) {
                    sim.attack(pl, b.player);
                    sim.swing(pl);
                }
            }, null);
            sim.clientTick(b, null, null);
            sim.serverTick();
        }
        assertClean(sim, a, "switching hotbar slots then hitting");
    }
}
