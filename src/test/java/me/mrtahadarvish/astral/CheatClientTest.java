package me.mrtahadarvish.astral;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.mrtahadarvish.astral.sim.Human;
import me.mrtahadarvish.astral.sim.Sim;
import me.mrtahadarvish.astral.sim.SimPlayer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.FishHook;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import com.google.common.base.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simulated hacked clients. Every cheat has to be detected by the right check.
 */
@SuppressWarnings({"deprecation", "removal"})
class CheatClientTest {

    private static void assertCaughtType(Sim sim, SimPlayer cheater, String scenario, String check, String type) {
        boolean caught = sim.flagged(cheater, check, type);
        Report.add("CHEAT", scenario, sim.summary(cheater), caught);
        assertTrue(caught, scenario + " should be caught by " + check + "(" + type + ") -> " + sim.summary(cheater));
    }

    /** Caught by one of the checks (with blocking on, the first check to catch a cheat stops it). */
    private static void assertCaught(Sim sim, SimPlayer cheater, String scenario, String... checks) {
        boolean caught = false;
        for (String check : checks) {
            caught |= sim.flagged(cheater, check);
        }
        Report.add("CHEAT", scenario, sim.summary(cheater), caught);
        assertTrue(caught, scenario + " should be caught by " + String.join(" / ", checks) + " -> " + sim.summary(cheater));
    }

    private static Sim arena() {
        Sim sim = new Sim().floor(60);
        return sim;
    }

    private static void face(SimPlayer player, Vector point) {
        double[] r = SimPlayer.rotationTo(player.eyeClient(), point);
        player.yaw = (float) r[0];
        player.pitch = (float) r[1];
    }

    private static Vector body(SimPlayer target) {
        return new Vector(target.sx, target.sy + 1.2, target.sz);
    }

    // ================================================================ movement

    @Test
    void flyHover() {
        Sim sim = arena();
        SimPlayer p = sim.join("FlyHover", 0.5, 64, 0.5);
        sim.idle(3);
        sim.tick(pl -> pl.jump = true);
        sim.tick(pl -> pl.jump = false);
        for (int i = 0; i < 60; i++) {
            sim.tick(pl -> {
                pl.cheatFlyY = 0.0;
                pl.forward = 1;
            });
        }
        assertCaught(sim, p, "Fly (hover)", "Flight");
    }

    @Test
    void flyAscend() {
        Sim sim = arena();
        SimPlayer p = sim.join("FlyUp", 0.5, 64, 0.5);
        sim.idle(3);
        for (int i = 0; i < 40; i++) {
            sim.tick(pl -> pl.cheatFlyY = 0.3);
        }
        assertCaught(sim, p, "Fly (vertical / creative fly)", "Flight");
    }

    @Test
    void glide() {
        Sim sim = arena();
        sim.world.fill(-1, 80, -1, 1, 80, 1, Material.STONE);
        SimPlayer p = sim.join("Glider", 0.5, 81, 0.5);
        sim.idle(3);
        for (int i = 0; i < 60; i++) {
            sim.tick(pl -> {
                pl.forward = 1;
                pl.cheatFlyY = pl.onGround ? null : -0.05;
            });
        }
        assertCaught(sim, p, "Glide / slow fall", "Flight");
    }

    @Test
    void airJump() {
        Sim sim = arena();
        SimPlayer p = sim.join("AirJumper", 0.5, 64, 0.5);
        sim.idle(3);
        for (int i = 0; i < 80; i++) {
            sim.tick(pl -> {
                pl.jump = true;
                if (!pl.onGround && pl.my < -0.2) {
                    pl.my = 0.42; // jump again in mid air
                }
            });
        }
        assertCaught(sim, p, "Air jump / jetpack", "Flight");
    }

    @Test
    void speedGround() {
        Sim sim = arena();
        SimPlayer p = sim.join("Speeder", 0.5, 64, -50.5);
        sim.idle(3);
        for (int i = 0; i < 100; i++) {
            sim.tick(pl -> {
                pl.forward = 1;
                pl.sprinting = true;
                pl.cheatSpeed = 1.5;
            });
        }
        assertCaught(sim, p, "Speed x1.5 (ground)", "Speed");
    }

    @Test
    void speedBhop() {
        Sim sim = arena();
        SimPlayer p = sim.join("BhopHack", 0.5, 64, -50.5);
        sim.idle(3);
        for (int i = 0; i < 150; i++) {
            sim.tick(pl -> {
                pl.forward = 1;
                pl.sprinting = true;
                pl.jump = true;
                pl.cheatSpeed = 1.25;
            });
        }
        assertCaught(sim, p, "Bhop speed x1.25", "Speed");
    }

    @Test
    void airStrafe() {
        Sim sim = arena();
        SimPlayer p = sim.join("Strafer", 0.5, 64, 0.5);
        sim.idle(3);
        for (int i = 0; i < 160; i++) {
            int t = i;
            sim.tick(pl -> {
                pl.jump = true;
                pl.sprinting = true;
                pl.forward = 1;
                pl.cheatStrafe = true;
                if (t % 3 == 0) {
                    pl.mouseTurn(400, 0); // snap the direction in the air
                }
            });
        }
        assertCaught(sim, p, "Air strafe", "Speed");
    }

    @Test
    void noFall() {
        Sim sim = arena();
        sim.world.fill(-1, 90, -1, 1, 90, 1, Material.STONE);
        SimPlayer p = sim.join("NoFaller", 0.5, 91, 0.5);
        sim.idle(3);
        for (int i = 0; i < 60; i++) {
            sim.tick(pl -> {
                pl.forward = 1;
                pl.cheatGround = true;
            });
        }
        assertCaught(sim, p, "NoFall (on-ground spoof)", "NoFall");
    }

    @Test
    void step() {
        Sim sim = arena();
        for (int z = 3; z < 30; z += 3) {
            sim.world.fill(-2, 64 + z / 3 - 1, z, 2, 64 + z / 3 - 1, z + 2, Material.STONE);
        }
        SimPlayer p = sim.join("Stepper", 0.5, 64, 0.5);
        sim.idle(3);
        for (int i = 0; i < 120; i++) {
            sim.tick(pl -> {
                pl.forward = 1;
                pl.stepHeight = 1.0;
            });
        }
        assertCaught(sim, p, "Step (1 block)", "Step");
    }

    @Test
    void timer() {
        Sim sim = arena();
        SimPlayer p = sim.join("Timer", 0.5, 64, -40.5);
        sim.idle(3);
        // 5 client ticks for every 4 server ticks = game speed 125%
        for (int i = 0; i < 400; i++) {
            sim.clientTick(p, pl -> pl.forward = 1, null);
            if (i % 5 != 4) {
                sim.serverTick();
            }
        }
        assertCaught(sim, p, "Timer 1.25x", "Timer");
    }

    @Test
    void jesus() {
        Sim sim = arena();
        sim.world.fill(-5, 63, -5, 5, 63, 40, Material.WATER);
        sim.world.fill(-5, 62, -5, 5, 62, 40, Material.STONE);
        SimPlayer p = sim.join("Jesus", 0.5, 63.9, 0.5);
        sim.idle(1);
        for (int i = 0; i < 80; i++) {
            sim.tick(pl -> {
                pl.forward = 1;
                pl.cheatFlyY = 0.0;
                pl.cheatGround = true;
            });
        }
        assertCaught(sim, p, "Jesus (walking on water)", "Jesus", "Flight");
    }

    @Test
    void phase() {
        Sim sim = arena();
        sim.world.fill(-3, 64, 3, 3, 66, 3, Material.STONE);
        SimPlayer p = sim.join("Phaser", 0.5, 64, 1.5);
        sim.idle(3);
        // the client walks straight through the wall
        for (int i = 0; i < 6; i++) {
            int t = i;
            sim.clientTick(p, null, pl -> pl.sendRawPosition(0.5, 64, 1.5 + (t + 1) * 0.6, 0, 0, true));
            sim.serverTick();
        }
        assertCaught(sim, p, "Phase / NoClip through a wall", "Phase");
    }

    @Test
    void noSlow() {
        Sim sim = arena();
        SimPlayer p = sim.join("NoSlow", 0.5, 64, -50.5);
        p.mainHand = Material.COOKED_BEEF;
        sim.idle(3);
        for (int i = 0; i < 100; i++) {
            int t = i;
            sim.tick(pl -> {
                pl.forward = 1;
                pl.sprinting = true;
                pl.cheatNoSlow = true;
                if (t % 32 == 0) {
                    pl.usingItem = true;
                    pl.useTicks = 0;
                    sim.useItem(pl);
                }
            });
        }
        assertCaught(sim, p, "NoSlow (eating at full speed)", "NoSlow");
    }

    @Test
    void omniSprint() {
        Sim sim = arena();
        SimPlayer p = sim.join("OmniSprint", 0.5, 64, 0.5);
        sim.idle(3);
        for (int i = 0; i < 60; i++) {
            sim.tick(pl -> {
                pl.forward = -1;
                pl.sprinting = true;
            });
        }
        assertCaught(sim, p, "OmniSprint (sprinting backwards)", "OmniSprint");
    }

    @Test
    void fastClimb() {
        Sim sim = arena();
        sim.world.fill(0, 64, 1, 0, 90, 1, Material.STONE);
        sim.world.fill(0, 64, 0, 0, 90, 0, Material.LADDER);
        SimPlayer p = sim.join("FastClimb", 0.5, 64, 0.5);
        sim.idle(3);
        for (int i = 0; i < 40; i++) {
            sim.tick(pl -> pl.cheatFlyY = 0.4);
        }
        assertCaught(sim, p, "FastClimb (ladder)", "FastClimb");
    }

    @Test
    void elytraFly() {
        Sim sim = arena();
        SimPlayer p = sim.join("ElytraFly", 0.5, 90, 0.5);
        sim.idle(3);
        p.gliding = true;
        for (int i = 0; i < 60; i++) {
            sim.tick(pl -> {
                pl.cheatFlyY = 0.0;
                pl.forward = 0;
            });
        }
        assertCaught(sim, p, "ElytraFly (hovering)", "ElytraFly");
    }

    // ================================================================ combat

    @Test
    void reach() {
        Sim sim = arena();
        SimPlayer a = sim.join("Reacher", 0.5, 64, 0.5);
        SimPlayer b = sim.join("Victim", 0.5, 64, 4.8);
        sim.idle(5);
        for (int i = 0; i < 40; i++) {
            int t = i;
            sim.clientTick(a, pl -> {
                face(pl, body(b));
                if (t % 4 == 0) {
                    sim.attack(pl, b.player);
                    sim.swing(pl);
                }
            }, null);
            sim.clientTick(b, null, null);
            sim.serverTick();
        }
        assertCaught(sim, a, "Reach 4.2 blocks", "Reach");
    }

    @Test
    void hitboxAuraWithoutRotations() {
        Sim sim = arena();
        SimPlayer a = sim.join("NoRotAura", 0.5, 64, 0.5);
        SimPlayer b = sim.join("Victim2", 2.5, 64, 0.5);
        sim.idle(5);
        for (int i = 0; i < 60; i++) {
            int t = i;
            sim.clientTick(a, pl -> {
                pl.yaw = 0; // looking +Z, target is at +X
                if (t % 4 == 0) {
                    sim.attack(pl, b.player);
                    sim.swing(pl);
                }
            }, null);
            sim.clientTick(b, null, null);
            sim.serverTick();
        }
        assertCaught(sim, a, "Aura without rotations (hits outside the crosshair)", "Hitbox");
    }

    @Test
    void killAuraMulti() {
        Sim sim = arena();
        SimPlayer a = sim.join("MultiAura", 0.5, 64, 0.5);
        SimPlayer b = sim.join("VictimB", 0.5, 64, 2.5);
        SimPlayer c = sim.join("VictimC", 1.5, 64, 2.5);
        sim.idle(5);
        for (int i = 0; i < 20; i++) {
            sim.clientTick(a, pl -> {
                face(pl, body(b));
                sim.attack(pl, b.player);
                sim.attack(pl, c.player);
                sim.swing(pl);
            }, null);
            sim.clientTick(b, null, null);
            sim.clientTick(c, null, null);
            sim.serverTick();
        }
        assertCaught(sim, a, "Multi aura (2 targets per tick)", "KillAura");
    }

    @Test
    void silentRotationAuraPost() {
        Sim sim = arena();
        SimPlayer a = sim.join("PostAura", 0.5, 64, 0.5);
        SimPlayer b = sim.join("VictimP", 2.5, 64, 1.5);
        sim.idle(5);
        for (int i = 0; i < 60; i++) {
            int t = i;
            sim.clientTick(a, pl -> pl.yaw = 0, t % 5 == 0 ? pl -> {
                // rotate in the movement packet, then attack after it (silent rotations)
                double[] r = SimPlayer.rotationTo(pl.eyeServer(), body(b));
                pl.sendRawPosition(pl.sx, pl.sy, pl.sz, (float) r[0], (float) r[1], true);
                sim.attack(pl, b.player);
                sim.swing(pl);
            } : null);
            sim.clientTick(b, null, null);
            sim.serverTick();
        }
        assertCaught(sim, a, "Silent rotation aura (attack after movement)", "KillAura", "Aim");
    }

    @Test
    void silentRotationAuraInMovePacket() {
        Sim sim = arena();
        SimPlayer a = sim.join("PostAura2", 0.5, 64, 0.5);
        SimPlayer b = sim.join("VictimP2", 2.5, 64, 1.5);
        sim.idle(5);
        for (int i = 0; i < 60; i++) {
            boolean hit = i % 5 == 0;
            a.yaw = 0;
            if (hit) {
                double[] r = SimPlayer.rotationTo(a.eyeClient(), body(b));
                a.silentYaw = (float) r[0];
                a.silentPitch = (float) r[1];
            } else {
                a.silentYaw = null;
                a.silentPitch = null;
            }
            sim.clientTick(a, null, hit ? pl -> {
                // the movement packet carried the aim, the attack comes after it
                sim.attack(pl, b.player);
                sim.swing(pl);
            } : null);
            sim.clientTick(b, null, null);
            sim.serverTick();
        }
        assertCaught(sim, a, "Silent rotation aura (aim inside the movement packet)", "KillAura", "Aim");
    }

    @Test
    void blink() {
        // movement packets are held back for 9 ticks and released at once (blink / fake lag)
        Sim sim = arena();
        SimPlayer p = sim.join("Blink", 0.5, 64, 0.5);
        sim.idle(5);
        List<double[]> held = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            p.yaw = 0;
            p.forward = 1;
            p.sprinting = true;
            p.physicsTick();
            if (i % 10 < 9) {
                held.add(new double[]{p.x, p.y, p.z});
            } else {
                for (double[] pos : held) {
                    p.sendRawPosition(pos[0], pos[1], pos[2], p.yaw, p.pitch, true);
                }
                held.clear();
                p.sendMovement();
            }
            sim.tickEnd(p);
            sim.serverTick();
        }
        assertCaughtType(sim, p, "Blink (movement held back, then sent at once)", "BadPackets", "Multi");
    }

    @Test
    void silentAim() {
        Sim sim = arena();
        SimPlayer a = sim.join("SilentAim", 0.5, 64, 0.5);
        SimPlayer b = sim.join("VictimS", 3.0, 64, 0.5);
        sim.idle(5);
        for (int i = 0; i < 80; i++) {
            int t = i;
            boolean hitTick = t % 8 == 0;
            sim.clientTick(a, pl -> {
                pl.yaw = 0;
                pl.pitch = 10;
                if (hitTick) {
                    // correct packet order, but the rotation only points at the target for this one tick
                    sim.attack(pl, b.player);
                    sim.swing(pl);
                    face(pl, body(b));
                }
            }, null);
            sim.clientTick(b, null, null);
            sim.serverTick();
        }
        assertCaught(sim, a, "Silent aim (flick to target and back)", "Aim");
    }

    @Test
    void aimbotGcd() {
        Sim sim = arena();
        SimPlayer a = sim.join("Aimbot", 0.5, 64, 0.5);
        SimPlayer b = sim.join("VictimG", 0.5, 64, 3.0);
        Random random = new Random(4);
        sim.idle(5);
        for (int i = 0; i < 500; i++) {
            int t = i;
            sim.clientTick(a, pl -> {
                double[] r = SimPlayer.rotationTo(pl.eyeClient(), body(b));
                // smooth aim with randomisation: perfect floats, no mouse steps
                pl.yaw += (float) ((r[0] - pl.yaw) * 0.6 + (random.nextDouble() - 0.5) * 3.0);
                pl.pitch += (float) ((r[1] - pl.pitch) * 0.6 + (random.nextDouble() - 0.5) * 4.0);
                if (t % 4 == 0) {
                    sim.attack(pl, b.player);
                    sim.swing(pl);
                }
            }, null);
            sim.clientTick(b, pl -> {
                pl.strafe = (t / 15) % 2 == 0 ? 1 : -1;
                pl.jump = true;
            }, null);
            sim.serverTick();
        }
        assertCaught(sim, a, "Aimbot / aim assist (rotations without mouse steps)", "Aim");
    }

    @Test
    void roboticRotations() {
        Sim sim = arena();
        SimPlayer a = sim.join("Robot", 0.5, 64, 0.5);
        SimPlayer b = sim.join("VictimR", 0.5, 64, 2.5);
        sim.idle(5);
        for (int i = 0; i < 60; i++) {
            int t = i;
            sim.clientTick(a, pl -> {
                pl.yaw += 2.5F;
                if (t % 5 == 0) {
                    sim.attack(pl, b.player);
                    sim.swing(pl);
                }
            }, null);
            sim.clientTick(b, null, null);
            sim.serverTick();
        }
        assertCaught(sim, a, "Rotation aura with constant steps", "Aim");
    }

    @Test
    void noSwing() {
        Sim sim = arena();
        SimPlayer a = sim.join("NoSwing", 0.5, 64, 0.5);
        SimPlayer b = sim.join("VictimN", 0.5, 64, 2.5);
        sim.idle(5);
        for (int i = 0; i < 40; i++) {
            int t = i;
            sim.clientTick(a, pl -> {
                face(pl, body(b));
                if (t % 4 == 0) {
                    sim.attack(pl, b.player);
                }
            }, null);
            sim.clientTick(b, null, null);
            sim.serverTick();
        }
        assertCaught(sim, a, "NoSwing", "KillAura");
    }

    @Test
    void switchAura() {
        Sim sim = arena();
        SimPlayer a = sim.join("SwitchAura", 0.5, 64, 0.5);
        SimPlayer b = sim.join("Front", 0.5, 64, 3.0);
        SimPlayer c = sim.join("Behind", 0.5, 64, -2.0);
        sim.idle(5);
        for (int i = 0; i < 30; i++) {
            int t = i;
            sim.clientTick(a, pl -> {
                SimPlayer target = t % 2 == 0 ? b : c;
                face(pl, body(target));
                sim.attack(pl, target.player);
                sim.swing(pl);
            }, null);
            sim.clientTick(b, null, null);
            sim.clientTick(c, null, null);
            sim.serverTick();
        }
        assertCaught(sim, a, "Switch aura (front/back every tick)", "KillAura");
    }

    @Test
    void autoBlock() {
        Sim sim = arena();
        SimPlayer a = sim.join("AutoBlock", 0.5, 64, 0.5);
        SimPlayer b = sim.join("VictimAB", 0.5, 64, 2.5);
        a.mainHand = Material.SHIELD;
        sim.idle(5);
        for (int i = 0; i < 30; i++) {
            int t = i;
            sim.clientTick(a, pl -> {
                face(pl, body(b));
                pl.blocking = true;
                pl.usingItem = true;
                if (t % 5 == 0) {
                    sim.attack(pl, b.player);
                    sim.swing(pl);
                }
            }, null);
            sim.clientTick(b, null, null);
            sim.serverTick();
        }
        assertCaught(sim, a, "AutoBlock (hit while blocking)", "AutoBlock");
    }

    @Test
    void criticals() {
        Sim sim = arena();
        SimPlayer a = sim.join("Crits", 0.5, 64, 0.5);
        SimPlayer b = sim.join("VictimCr", 0.5, 64, 2.5);
        sim.idle(5);
        for (int i = 0; i < 20; i++) {
            int t = i;
            sim.clientTick(a, pl -> {
                face(pl, body(b));
                if (t % 5 == 0) {
                    // packet criticals: tiny hop packets, then the hit counts as critical while standing
                    pl.sendRawPosition(pl.sx, pl.sy + 0.0625, pl.sz, pl.yaw, pl.pitch, false);
                    pl.sendRawPosition(pl.sx, 64.0, pl.sz, pl.yaw, pl.pitch, false);
                    sim.attack(pl, b.player);
                    sim.swing(pl);
                    crit(sim, pl, b);
                }
            }, null);
            sim.clientTick(b, null, null);
            sim.serverTick();
        }
        assertCaught(sim, a, "Packet criticals", "Criticals");
    }

    private static void crit(Sim sim, SimPlayer attacker, SimPlayer victim) {
        Map<EntityDamageEvent.DamageModifier, Double> modifiers = new EnumMap<>(EntityDamageEvent.DamageModifier.class);
        modifiers.put(EntityDamageEvent.DamageModifier.BASE, 7.0);
        Map<EntityDamageEvent.DamageModifier, Function<? super Double, Double>> functions = new EnumMap<>(EntityDamageEvent.DamageModifier.class);
        functions.put(EntityDamageEvent.DamageModifier.BASE, d -> 0.0);
        sim.call(new EntityDamageByEntityEvent(attacker.player, victim.player, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                Sim.mock(DamageSource.class), modifiers, functions, true));
    }

    @Test
    void antiKnockback() {
        Sim sim = arena();
        SimPlayer p = sim.join("AntiKB", 0.5, 64, 0.5);
        sim.idle(5);
        for (int hit = 0; hit < 5; hit++) {
            sim.velocity(p, new Vector(0.3, 0.4, 0.0)); // the client ignores it
            sim.idle(30);
        }
        assertCaught(sim, p, "Velocity / anti knockback", "Velocity");
    }

    @Test
    void crystalAuraPredict() {
        Sim sim = arena();
        SimPlayer p = sim.join("CrystalAura", 0.5, 64, 0.5);
        sim.world.set(0, 64, 3, Material.OBSIDIAN);
        sim.idle(5);
        for (int round = 0; round < 5; round++) {
            Block obsidian = sim.world.block(0, 64, 3);
            EnderCrystal crystal = sim.crystal(obsidian);
            sim.tick(pl -> {
                face(pl, new Vector(0.5, 65.5, 3.5));
                sim.placeCrystal(pl, obsidian, crystal);
                sim.attack(pl, crystal); // hits the crystal before the server even sent it
                sim.swing(pl);
            });
            sim.idle(4);
        }
        assertCaught(sim, p, "Crystal aura (ID predict / instant break)", "CrystalAura");
    }

    // ================================================================ macros

    @Test
    void silentSwap() {
        Sim sim = arena();
        SimPlayer a = sim.join("SilentSwap", 0.5, 64, 0.5);
        SimPlayer b = sim.join("VictimSw", 0.5, 64, 2.5);
        sim.idle(5);
        for (int i = 0; i < 40; i++) {
            int t = i;
            sim.clientTick(a, pl -> {
                face(pl, body(b));
                if (t % 5 == 0) {
                    sim.slot(pl, 3);           // swap to the axe / mace
                    sim.attack(pl, b.player);
                    sim.swing(pl);
                    sim.slot(pl, 0);           // and back in the same tick
                }
            }, null);
            sim.clientTick(b, null, null);
            sim.serverTick();
        }
        assertCaught(sim, a, "Silent swap (switch, hit, switch back)", "SilentSwap");
    }

    @Test
    void maceAttributeSwap() {
        Sim sim = arena();
        SimPlayer a = sim.join("MaceSwap", 0.5, 64, 0.5);
        SimPlayer b = sim.join("VictimM", 0.5, 64, 2.5);
        sim.idle(5);
        for (int i = 0; i < 40; i++) {
            int t = i;
            sim.clientTick(a, pl -> {
                face(pl, body(b));
                if (t % 6 == 0) {
                    sim.attack(pl, b.player);  // hit with the sword...
                    sim.swing(pl);
                    sim.slot(pl, t % 12 == 0 ? 4 : 0); // ...switch to the mace after the hit
                }
            }, null);
            sim.clientTick(b, null, null);
            sim.serverTick();
        }
        assertCaught(sim, a, "Attribute / mace swap after the hit", "SilentSwap");
    }

    @Test
    void postSlotSwap() {
        Sim sim = arena();
        SimPlayer p = sim.join("PostSwap", 0.5, 64, 0.5);
        sim.idle(5);
        for (int i = 0; i < 30; i++) {
            int t = i;
            sim.tick(pl -> pl.mouseTurn(10, 0), t % 5 == 0 ? pl -> sim.slot(pl, (pl.slot + 1) % 9) : null);
        }
        assertCaught(sim, p, "Slot swap after the movement packet", "SilentSwap");
    }

    @Test
    void autoClickerFast() {
        Sim sim = arena();
        SimPlayer a = sim.join("Clicker40", 0.5, 64, 0.5);
        SimPlayer b = sim.join("VictimC40", 0.5, 64, 2.5);
        sim.idle(5);
        for (int i = 0; i < 80; i++) {
            sim.clientTick(a, pl -> {
                face(pl, body(b));
                for (int c = 0; c < 2; c++) {
                    sim.attack(pl, b.player);
                    sim.swing(pl);
                }
            }, null);
            sim.clientTick(b, null, null);
            sim.serverTick();
        }
        assertCaught(sim, a, "Auto clicker 40 CPS", "AutoClicker");
    }

    @Test
    void autoClickerPattern() {
        Sim sim = arena();
        SimPlayer a = sim.join("Clicker12", 0.5, 64, 0.5);
        SimPlayer b = sim.join("VictimC12", 0.5, 64, 2.5);
        sim.idle(5);
        double[] next = {0.0};
        for (int i = 0; i < 700; i++) {
            int t = i;
            sim.clientTick(a, pl -> {
                face(pl, body(b));
                while (next[0] <= t * 50.0) {
                    next[0] += 80.0; // 12.5 CPS, fixed delay
                    sim.attack(pl, b.player);
                    sim.swing(pl);
                }
            }, null);
            sim.clientTick(b, null, null);
            sim.serverTick();
        }
        assertCaught(sim, a, "Auto clicker 12.5 CPS fixed delay", "AutoClicker");
    }

    @Test
    void autoTotem() {
        Sim sim = arena();
        SimPlayer p = sim.join("AutoTotem", 0.5, 64, 0.5);
        sim.idle(5);
        for (int pop = 0; pop < 3; pop++) {
            sim.totemPop(p);
            sim.tick(pl -> sim.click(pl, 20, ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP,
                    SimPlayer.item(Material.TOTEM_OF_UNDYING), null));
            sim.idle(40);
        }
        assertCaught(sim, p, "AutoTotem (totem back in 1 tick)", "AutoTotem");
    }

    @Test
    void inventoryWalk() {
        Sim sim = arena();
        SimPlayer p = sim.join("InvWalk", 0.5, 64, -40.5);
        sim.idle(5);
        for (int i = 0; i < 40; i++) {
            int t = i;
            sim.tick(pl -> {
                pl.forward = 1;
                pl.sprinting = true;
                if (t % 4 == 0) {
                    sim.click(pl, 10 + t % 20, ClickType.LEFT, InventoryAction.PICKUP_ALL, SimPlayer.item(Material.DIAMOND_CHESTPLATE), null);
                }
            });
        }
        assertCaught(sim, p, "InventoryMove (clicking while running)", "InventoryMove");
    }

    @Test
    void inventoryMacroDuringFight() {
        Sim sim = arena();
        SimPlayer a = sim.join("AutoArmor", 0.5, 64, 0.5);
        SimPlayer b = sim.join("VictimAA", 0.5, 64, 2.5);
        sim.idle(5);
        for (int i = 0; i < 30; i++) {
            int t = i;
            sim.clientTick(a, pl -> {
                face(pl, body(b));
                if (t % 5 == 0) {
                    sim.attack(pl, b.player);
                    sim.swing(pl);
                    // equip armour / totem with a packet in the middle of the fight
                    sim.click(pl, 6, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY, SimPlayer.item(Material.DIAMOND_HELMET), null);
                }
            }, null);
            sim.clientTick(b, null, null);
            sim.serverTick();
        }
        assertCaught(sim, a, "Inventory packets while attacking (auto armor / auto totem)", "InventoryMove");
    }

    @Test
    void chestStealer() {
        Sim sim = arena();
        SimPlayer p = sim.join("Stealer", 0.5, 64, 0.5);
        sim.idle(5);
        p.releaseKeys();
        for (int i = 0; i < 40; i++) {
            int t = i;
            sim.tick(pl -> {
                if (t % 2 == 0) {
                    sim.click(pl, t / 2, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY,
                            SimPlayer.item(Material.DIAMOND), InventoryType.CHEST);
                }
            });
        }
        assertCaught(sim, p, "Chest stealer (perfect 2 tick delay)", "ChestStealer");
    }

    @Test
    void hotbarSpam() {
        Sim sim = arena();
        SimPlayer p = sim.join("SlotSpam", 0.5, 64, 0.5);
        sim.idle(5);
        for (int i = 0; i < 10; i++) {
            sim.tick(pl -> {
                for (int s = 1; s <= 4; s++) {
                    sim.slot(pl, (pl.slot + s) % 9);
                }
            });
        }
        assertCaught(sim, p, "Hotbar spam (4 slots per tick)", "SilentSwap");
    }

    @Test
    void autoFish() {
        Sim sim = arena();
        SimPlayer p = sim.join("AutoFish", 0.5, 64, 0.5);
        FishHook hook = Sim.mock(FishHook.class);
        sim.idle(5);
        for (int fish = 0; fish < 10; fish++) {
            sim.call(new PlayerFishEvent(p.player, null, hook, EquipmentSlot.HAND, PlayerFishEvent.State.BITE));
            sim.idle(3);
            sim.call(new PlayerFishEvent(p.player, null, hook, EquipmentSlot.HAND, PlayerFishEvent.State.CAUGHT_FISH));
            sim.idle(40);
        }
        assertCaught(sim, p, "AutoFish (same reaction every bite)", "AutoFish");
    }

    @Test
    void fastBow() {
        Sim sim = arena();
        SimPlayer p = sim.join("FastBow", 0.5, 64, 0.5);
        sim.idle(5);
        for (int i = 0; i < 20; i++) {
            sim.tick(pl -> sim.call(new EntityShootBowEvent(pl.player, SimPlayer.item(Material.BOW), SimPlayer.item(Material.ARROW),
                    Sim.mock(Arrow.class), EquipmentSlot.HAND, 0.3F, true)));
        }
        assertCaught(sim, p, "FastBow (20 arrows per second)", "FastBow");
    }

    // ================================================================ world

    @Test
    void scaffoldAirPlace() {
        Sim sim = arena();
        SimPlayer p = sim.join("AirPlace", 0.5, 64, 0.5);
        sim.idle(5);
        for (int i = 0; i < 10; i++) {
            int t = i;
            sim.tick(pl -> {
                face(pl, new Vector(0.5, 66.5, 3.5));
                sim.place(pl, sim.world.block(0, 66 + t, 3), BlockFace.UP, Material.STONE);
                sim.swing(pl);
            });
        }
        assertCaught(sim, p, "AirPlace (placing against air)", "Scaffold");
    }

    /** The floor ends at z = 3: from there a bridge has to be built over the void. */
    private static Sim voidBridge() {
        Sim sim = arena();
        sim.world.fill(-60, 63, 3, 60, 63, 60, Material.AIR);
        return sim;
    }

    /** Center of the south face of the bridge block the next block is placed against. */
    private static Vector bridgeFace(int nextZ) {
        return new Vector(0.5, 63.5, nextZ);
    }

    @Test
    void scaffoldSilentRotations() {
        // Meteor / LiquidBounce style: the regular movement packet carries a rotation looking down
        // at the bridge while the player looks ahead, and the block is placed after that packet
        Sim sim = voidBridge();
        SimPlayer p = sim.join("Scaffold", 0.5, 64, 0.5);
        sim.idle(5);
        int nextZ = 3;
        for (int i = 0; i < 80 && nextZ < 12; i++) {
            p.yaw = 0;
            p.pitch = 10;
            p.forward = 1;
            p.physicsTick();
            double[] r = SimPlayer.rotationTo(p.eyeClient(), bridgeFace(nextZ));
            p.silentYaw = (float) r[0];
            p.silentPitch = (float) r[1];
            p.sendMovement();
            if (p.sz > nextZ + 0.05) {
                sim.place(p, sim.world.block(0, 63, nextZ - 1), BlockFace.SOUTH, Material.STONE);
                sim.swing(p);
                nextZ++;
            }
            sim.tickEnd(p);
            sim.serverTick();
        }
        assertTrue(nextZ >= 10, "the bridge should have been built (nextZ=" + nextZ + ")");
        assertCaughtType(sim, p, "Scaffold with silent rotations (place after the movement packet)", "Scaffold", "Post");
    }

    @Test
    void scaffoldRotateBack() {
        // "instant" rotations: an extra rotation packet, the place, then the regular movement
        // packet turns back to where the player really looks
        Sim sim = voidBridge();
        SimPlayer p = sim.join("ScaffoldRB", 0.5, 64, 0.5);
        sim.idle(5);
        int nextZ = 3;
        for (int i = 0; i < 80 && nextZ < 12; i++) {
            p.yaw = 0;
            p.pitch = 10;
            p.forward = 1;
            if (p.sz > nextZ + 0.05) {
                double[] r = SimPlayer.rotationTo(p.eyeServer(), bridgeFace(nextZ));
                p.sendRawPosition(p.sx, p.sy, p.sz, (float) r[0], (float) r[1], true);
                sim.place(p, sim.world.block(0, 63, nextZ - 1), BlockFace.SOUTH, Material.STONE);
                sim.swing(p);
                nextZ++;
            }
            p.physicsTick();
            p.sendMovement();
            sim.tickEnd(p);
            sim.serverTick();
        }
        assertTrue(nextZ >= 10, "the bridge should have been built (nextZ=" + nextZ + ")");
        assertCaughtType(sim, p, "Scaffold rotate-place-rotate back (extra rotation packet)", "BadPackets", "Multi");
    }

    @Test
    void fastPlace() {
        Sim sim = arena();
        SimPlayer p = sim.join("FastPlace", 0.5, 64, 0.5);
        sim.idle(5);
        for (int i = 0; i < 30; i++) {
            int t = i;
            sim.tick(pl -> {
                face(pl, new Vector(0.5 + (t % 5) - 2, 64.0, 2.5));
                Block against = sim.world.block((t % 5) - 2, 63 + t / 5, 2);
                sim.place(pl, against, BlockFace.UP, Material.STONE);
                sim.swing(pl);
            });
        }
        assertCaught(sim, p, "FastPlace (a block every tick)", "FastPlace", "Scaffold");
    }

    @Test
    void nuker() {
        Sim sim = arena();
        sim.world.fill(-2, 64, 2, 2, 66, 4, Material.STONE);
        SimPlayer p = sim.join("Nuker", 0.5, 64, 0.5);
        sim.idle(5);
        for (int i = 0; i < 10; i++) {
            int t = i;
            sim.tick(pl -> {
                for (int b = 0; b < 3; b++) {
                    sim.breakBlock(pl, sim.world.block(b - 1, 64 + t % 3, 2 + t / 3 % 3));
                }
            });
        }
        assertCaught(sim, p, "Nuker (3 blocks per tick)", "Nuker");
    }

    @Test
    void fastBreak() {
        Sim sim = arena();
        sim.world.fill(-2, 64, 2, 2, 64, 2, Material.STONE);
        SimPlayer p = sim.join("FastBreak", 0.5, 64, 0.5);
        sim.idle(5);
        for (int x = -2; x <= 2; x++) {
            Block block = sim.world.block(x, 64, 2);
            sim.tick(pl -> {
                face(pl, new Vector(block.getX() + 0.5, 64.5, 2.5));
                sim.startDig(pl, block);
            });
            sim.idle(5); // vanilla needs 20 ticks
            sim.tick(pl -> sim.breakBlock(pl, block));
        }
        assertCaught(sim, p, "FastBreak (4x speed mine)", "FastBreak");
    }

    @Test
    void ghostHand() {
        Sim sim = arena();
        sim.world.fill(-2, 64, 2, 2, 66, 2, Material.STONE);
        sim.world.set(0, 64, 4, Material.CHEST);
        SimPlayer p = sim.join("GhostHand", 0.5, 64, 0.5);
        sim.idle(5);
        for (int i = 0; i < 3; i++) {
            sim.world.set(0, 64, 4, Material.DIAMOND_ORE);
            sim.tick(pl -> {
                face(pl, new Vector(0.5, 64.5, 4.5));
                sim.breakBlock(pl, sim.world.block(0, 64, 4));
            });
        }
        assertCaught(sim, p, "GhostHand (mining behind a wall)", "GhostHand");
    }

    @Test
    void blockReach() {
        Sim sim = arena();
        sim.world.set(0, 64, 8, Material.STONE);
        SimPlayer p = sim.join("BlockReach", 0.5, 64, 0.5);
        sim.idle(5);
        sim.tick(pl -> {
            face(pl, new Vector(0.5, 64.5, 8.5));
            sim.breakBlock(pl, sim.world.block(0, 64, 8));
        });
        assertCaught(sim, p, "BlockReach (7 blocks)", "BlockReach");
    }

    @Test
    void badPacketsPostUse() {
        Sim sim = arena();
        SimPlayer p = sim.join("AutoPot", 0.5, 64, 0.5);
        p.mainHand = Material.SPLASH_POTION;
        sim.idle(5);
        for (int i = 0; i < 10; i++) {
            sim.tick(pl -> pl.mouseTurn(5, 0), pl -> {
                pl.sendRawPosition(pl.sx, pl.sy, pl.sz, pl.yaw, 90, true); // look down silently
                sim.useItem(pl);                                         // throw the potion
            });
        }
        assertCaught(sim, p, "Auto pot (use item after the movement packet)", "BadPackets");
    }

    @Test
    void hackedClientBrand() {
        Sim sim = arena();
        SimPlayer p = new SimPlayer(sim.world, "Meteor", 0.5, 64, 0.5, 1);
        p.brand = "fabric (meteor-client)";
        sim.players.add(p);
        sim.call(new org.bukkit.event.player.PlayerJoinEvent(p.player, net.kyori.adventure.text.Component.empty()));
        sim.call(new PlayerRegisterChannelEvent(p.player, "meteor-client:main"));
        assertCaught(sim, p, "Hacked client brand / channel", "ClientBrand");
    }

    // ================================================================ watch mode vs block mode

    @Test
    void watchModeNeverBlocks() {
        Sim sim = arena();
        sim.settings.mitigation = false;
        SimPlayer a = sim.join("WatchReach", 0.5, 64, 0.5);
        SimPlayer b = sim.join("WatchVictim", 0.5, 64, 5.0);
        sim.idle(5);
        boolean[] cancelled = {false};
        for (int i = 0; i < 20; i++) {
            sim.clientTick(a, pl -> {
                face(pl, body(b));
                PrePlayerAttackEntityEvent event = sim.call(new PrePlayerAttackEntityEvent(pl.player, b.player, true));
                cancelled[0] |= event.isCancelled();
                sim.swing(pl);
            }, null);
            sim.clientTick(b, null, null);
            sim.serverTick();
        }
        boolean flagged = sim.flagged(a, "Reach");
        Report.add("MODE", "Watch mode: reach is flagged but the hits are NOT cancelled", sim.summary(a), flagged && !cancelled[0]);
        assertTrue(flagged);
        assertFalse(cancelled[0], "watch mode must not cancel anything");
    }

    @Test
    void blockModeCancelsAndSetsBack() {
        Sim sim = arena();
        sim.settings.mitigation = true;
        SimPlayer a = sim.join("BlockReach2", 0.5, 64, 0.5);
        SimPlayer b = sim.join("BlockVictim", 0.5, 64, 5.0);
        sim.idle(5);
        boolean[] cancelled = {false};
        for (int i = 0; i < 20; i++) {
            sim.clientTick(a, pl -> {
                face(pl, body(b));
                PrePlayerAttackEntityEvent event = sim.call(new PrePlayerAttackEntityEvent(pl.player, b.player, true));
                cancelled[0] |= event.isCancelled();
                sim.swing(pl);
            }, null);
            sim.clientTick(b, null, null);
            sim.serverTick();
        }
        SimPlayer f = sim.join("BlockFly", 10.5, 64, 10.5);
        sim.idle(5);
        double highest = 0;
        for (int i = 0; i < 60; i++) {
            sim.tick(pl -> {
                if (pl == f) {
                    pl.cheatFlyY = 0.3;
                }
            });
            highest = Math.max(highest, f.sy);
        }
        boolean setBack = f.sy < 70;
        Report.add("MODE", "Block mode (mitigation: true): reach hits cancelled, flyer pulled back down", sim.summary(f),
                cancelled[0] && setBack);
        assertTrue(cancelled[0], "block mode should cancel reach hits");
        assertTrue(setBack, "block mode should set the flyer back (y=" + f.sy + ", highest " + highest + ")");
    }

    @Test
    void legitHumanBeatsAuraFight() {
        // a human vs a silent-rotation aura player in the same fight: only the aura user is flagged
        Sim sim = arena();
        SimPlayer human = sim.join("HonestPlayer", 0.5, 64, 0.5);
        SimPlayer cheater = sim.join("AuraUser", 0.5, 64, 3.5);
        Human h = new Human(human, 10, 99);
        sim.idle(5);
        for (int i = 0; i < 400; i++) {
            int t = i;
            h.record(cheater);
            sim.clientTick(human, pl -> {
                h.applyVelocity();
                h.aim(cheater);
                h.move(cheater);
                for (int c = h.clicksThisTick(); c > 0; c--) {
                    h.click(sim, cheater);
                }
            }, null);
            sim.clientTick(cheater, pl -> {
                pl.yaw = 180 + (t % 40);
                pl.forward = 0;
            }, t % 3 == 0 ? pl -> {
                double[] r = SimPlayer.rotationTo(pl.eyeServer(), body(human));
                pl.sendRawPosition(pl.sx, pl.sy, pl.sz, (float) r[0], (float) r[1], true);
                sim.attack(pl, human.player);
                sim.swing(pl);
            } : null);
            sim.serverTick();
        }
        boolean ok = sim.flags(human).isEmpty() && sim.flagged(cheater, "KillAura");
        Report.add("MIXED", "Human vs aura user in one fight: only the aura user flagged",
                "human: " + sim.summary(human) + " / aura: " + sim.summary(cheater), ok);
        assertTrue(sim.flags(human).isEmpty(), "human flagged: " + sim.summary(human));
        assertTrue(sim.flagged(cheater, "KillAura"), sim.summary(cheater));
        // keep the location import used
        assertTrue(new Location(sim.world.world, 0, 0, 0).getWorld() != null);
    }
}
