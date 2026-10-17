package me.mrtahadarvish.astral;

import me.mrtahadarvish.astral.sim.Sim;
import me.mrtahadarvish.astral.sim.SimEntity;
import me.mrtahadarvish.astral.sim.SimPlayer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Shulker;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Battle royale / minigame situations where a PLUGIN moves the players: a battle bus they ride,
 * stand on, get teleported or pushed along with, then the drop (free fall, elytra, slow falling
 * parachute) and the landing. Blocking is on (the default). Legit players must get no flags and
 * no setbacks - at 50, 150 and 300 ms ping.
 */
class BattleRoyaleTest {

    private static final int BUS_Y = 150;

    private static Sim map() {
        return new Sim().floor(90);
    }

    private static void assertLegit(Sim sim, SimPlayer player, String scenario) {
        String summary = sim.summary(player);
        boolean ok = sim.flags(player).isEmpty() && player.astralTeleports == 0;
        Report.add("LEGIT", scenario + " (" + player.ping + "ms)",
                summary + (player.astralTeleports > 0 ? " setbacks=" + player.astralTeleports : ""), ok);
        assertTrue(sim.flags(player).isEmpty(), scenario + " -> " + summary);
        assertTrue(player.astralTeleports == 0, scenario + " -> Astral teleported the player " + player.astralTeleports + " times");
    }

    /** Ticks until the player is on the floor again (and a few more), gliding ends on landing. */
    private static void untilLanded(Sim sim, SimPlayer player) {
        int landed = 0;
        for (int i = 0; i < 2000 && landed < 40; i++) {
            sim.tick(pl -> {
                if (pl == player && pl.gliding && pl.onGround) {
                    sim.glide(pl, false);
                }
            });
            if (player.onGround && player.sy < 65) {
                landed++;
            }
        }
        assertTrue(landed >= 40, player.name + " should have landed (y=" + player.sy + ")");
    }

    @ParameterizedTest(name = "bus seat {0}ms")
    @ValueSource(ints = {50, 150, 300, 500, 800})
    void ridingTheBusThenFreeFall(int ping) {
        Sim sim = map();
        SimPlayer p = sim.join("BusRider" + ping, 0.5, 64, 0.5);
        p.ping = ping;
        sim.idle(5);
        // an invisible armor stand seat the plugin moves along the bus route
        SimEntity seat = new SimEntity(sim.world, ArmorStand.class, EntityType.ARMOR_STAND, -60, BUS_Y, 0, 0.5, 1.975);
        sim.teleport(p, new Location(sim.world.world, seat.x, seat.y, seat.z));
        sim.mount(p, seat);
        for (int t = 0; t < 140; t++) {
            seat.move(0.7, t < 70 ? 0.05 : -0.05, 0.1);
            p.ride(seat.x, seat.y + 0.5, seat.z);
            sim.tick(null);
        }
        // jump off: Paper puts the player next to the seat, then he falls ~85 blocks
        sim.dismount(p, new Location(sim.world.world, seat.x, seat.y, seat.z));
        untilLanded(sim, p);
        sim.tick(pl -> pl.forward = 1);
        sim.idle(20);
        assertLegit(sim, p, "riding the battle bus, jumping off, free fall");
    }

    @ParameterizedTest(name = "teleport stream {0}ms")
    @ValueSource(ints = {50, 150, 300, 500, 800})
    void teleportedAlongTheBusRouteThenElytra(int ping) {
        Sim sim = map();
        SimPlayer p = sim.join("BusTeleport" + ping, 0.5, 64, 0.5);
        p.ping = ping;
        sim.idle(5);
        // no vehicle: the plugin teleports the player every tick (Paper freezes him until he confirms)
        for (int t = 0; t < 120; t++) {
            sim.teleport(p, new Location(sim.world.world, -48 + t * 0.4, BUS_Y, 0.5));
            sim.tick(null);
        }
        // drop: falls a bit, opens the elytra the plugin gave him, glides down
        sim.idle(15);
        sim.glide(p, true);
        for (int t = 0; t < 400 && p.gliding; t++) {
            int tick = t;
            sim.tick(pl -> {
                pl.pitch = tick < 200 ? 15 : 30;
                pl.yaw += 3.0F; // circles down over the map
                if (pl.gliding && pl.onGround) {
                    sim.glide(pl, false);
                }
            });
        }
        untilLanded(sim, p);
        assertLegit(sim, p, "teleported along the bus route, elytra drop");
    }

    @ParameterizedTest(name = "velocity bus {0}ms")
    @ValueSource(ints = {50, 150, 300, 500, 800})
    void carriedByVelocityThenSlowFallingParachute(int ping) {
        Sim sim = map();
        SimPlayer p = sim.join("BusVelocity" + ping, 0.5, 64, 0.5);
        p.ping = ping;
        sim.idle(5);
        sim.teleport(p, new Location(sim.world.world, -60, BUS_Y, 0.5));
        sim.idle(3);
        // the plugin carries the player with setVelocity every tick
        for (int t = 0; t < 120; t++) {
            sim.push(p, new Vector(0.8, 0.0, 0.0));
            sim.tick(null);
        }
        // drop with a slow falling "parachute" that runs out in the air
        sim.effect(p, "slow_falling", true);
        sim.idle(150);
        sim.effect(p, "slow_falling", false);
        untilLanded(sim, p);
        assertLegit(sim, p, "carried by plugin velocity, slow falling parachute");
    }

    @ParameterizedTest(name = "no gravity {0}ms")
    @ValueSource(ints = {50, 150, 300, 500, 800})
    void floatingWithGravityOffThenFall(int ping) {
        Sim sim = map();
        SimPlayer p = sim.join("BusFloat" + ping, 0.5, 64, 0.5);
        p.ping = ping;
        sim.idle(5);
        sim.teleport(p, new Location(sim.world.world, -40, BUS_Y, 0.5));
        sim.idle(3);
        sim.gravity(p, false);
        sim.push(p, new Vector(0.6, 0.0, 0.0));
        sim.idle(100);
        sim.gravity(p, true);
        untilLanded(sim, p);
        assertLegit(sim, p, "plugin turned gravity off, then on again mid-air");
    }

    @ParameterizedTest(name = "shulker floor {0}ms")
    @ValueSource(ints = {50, 150, 300, 500, 800})
    void standingOnAMovingShulkerFloor(int ping) {
        Sim sim = map();
        SimPlayer p = sim.join("BusFloor" + ping, 0.5, 64, 0.5);
        p.ping = ping;
        sim.idle(5);
        // the bus floor is an invisible shulker the plugin moves; the player stands on it
        SimEntity floor = new SimEntity(sim.world, Shulker.class, EntityType.SHULKER, -40.0, BUS_Y, 0.5, 1.0, 1.0);
        sim.teleport(p, new Location(sim.world.world, floor.x, floor.y + 1.0, floor.z));
        for (int t = 0; t < 8; t++) {
            // waits on the floor until the teleport is confirmed
            p.physicsTick();
            p.x = floor.x;
            p.y = floor.y + 1.0;
            p.z = floor.z;
            p.my = 0.0;
            p.onGround = true;
            p.sendMovement();
            sim.tickEnd(p);
            sim.serverTick();
        }
        for (int t = 0; t < 120; t++) {
            floor.move(0.5, 0.0, 0.0);
            // the client stands on the shulker: carried along, on ground
            p.x = floor.x;
            p.y = floor.y + 1.0;
            p.z = floor.z;
            p.mx = p.my = p.mz = 0.0;
            p.onGround = true;
            p.sendMovement();
            sim.tickEnd(p);
            sim.serverTick();
        }
        // walks off the edge
        for (int t = 0; t < 10; t++) {
            sim.tick(pl -> pl.forward = 1);
        }
        untilLanded(sim, p);
        assertLegit(sim, p, "standing on a moving shulker bus floor, walking off");
    }

    @ParameterizedTest(name = "spectator {0}ms")
    @ValueSource(ints = {50, 150, 300, 500, 800})
    void spectatingTheBusThenSurvival(int ping) {
        Sim sim = map();
        SimPlayer p = sim.join("BusSpectator" + ping, 0.5, 64, 0.5);
        p.ping = ping;
        sim.idle(5);
        sim.gameMode(p, GameMode.SPECTATOR);
        for (int t = 0; t < 80; t++) {
            sim.teleport(p, new Location(sim.world.world, -40 + t, BUS_Y, 0.5));
            sim.tick(null);
        }
        sim.gameMode(p, GameMode.SURVIVAL);
        untilLanded(sim, p);
        assertLegit(sim, p, "spectating the bus, survival mid-air");
    }

    @ParameterizedTest(name = "launch {0}ms")
    @ValueSource(ints = {50, 150, 300, 500, 800})
    void cancelledDoubleJumpLaunch(int ping) {
        Sim sim = map();
        SimPlayer p = sim.join("Launcher" + ping, 0.5, 64, 0.5);
        p.ping = ping;
        p.allowFlight = true;
        sim.idle(5);
        for (int launch = 0; launch < 3; launch++) {
            // jump, then double jump: the client starts flying, the plugin cancels it and launches
            sim.tick(pl -> pl.jump = true);
            sim.tick(pl -> pl.jump = false);
            p.flying = true;
            assertFalse(sim.toggleFlight(p, true, true));
            p.later(p.halfRtt(), () -> p.flying = false);
            sim.push(p, new Vector(0.0, 1.2, 1.4));
            untilLanded(sim, p);
        }
        assertLegit(sim, p, "double jump launch pad (flight toggle cancelled by the plugin)");
    }

    @ParameterizedTest(name = "boat ride {0}ms")
    @ValueSource(ints = {50, 300, 800})
    void drivingABoatThePluginLifts(int ping) {
        Sim sim = map();
        SimPlayer p = sim.join("BoatRide" + ping, 0.5, 64, 0.5);
        p.ping = ping;
        sim.idle(5);
        SimEntity boat = new SimEntity(sim.world, Boat.class, EntityType.OAK_BOAT, 0.5, 64, 3.5, 1.375, 0.5625);
        boat.gravity = false; // a ride / bus plugin moves the boat
        sim.mount(p, boat);
        moveBoat(sim, p, boat, 60, 0.15);
        assertFalse(sim.flagged(p, "BoatFly"), "a boat a plugin moves is not BoatFly -> " + sim.summary(p));
        assertLegit(sim, p, "driving a boat a plugin lifts into the air");
    }

    // ------------------------------------------------------------------ cheaters on the bus

    private static SimPlayer dropFromBus(Sim sim, String name, int ping) {
        SimPlayer p = sim.join(name, 0.5, 64, 0.5);
        p.ping = ping;
        sim.idle(5);
        SimEntity seat = new SimEntity(sim.world, ArmorStand.class, EntityType.ARMOR_STAND, -30, BUS_Y, 0, 0.5, 1.975);
        sim.teleport(p, new Location(sim.world.world, seat.x, seat.y, seat.z));
        sim.mount(p, seat);
        for (int t = 0; t < 60; t++) {
            seat.move(0.7, 0.0, 0.0);
            p.ride(seat.x, seat.y + 0.5, seat.z);
            sim.tick(null);
        }
        sim.dismount(p, new Location(sim.world.world, seat.x, seat.y, seat.z));
        return p;
    }

    @ParameterizedTest(name = "fly after the drop {0}ms")
    @ValueSource(ints = {50, 300})
    void cheaterFlyingAfterTheDropIsCaught(int ping) {
        Sim sim = map();
        SimPlayer p = dropFromBus(sim, "DropFlyer" + ping, ping);
        sim.idle(50); // falls for a while
        double before = p.sy;
        for (int t = 0; t < 80; t++) {
            sim.tick(pl -> pl.cheatFlyY = 0.0); // hovers in the air
        }
        boolean caught = sim.flagged(p, "Flight");
        Report.add("CHEAT", "Fly / hover after jumping off the battle bus (" + ping + "ms)", sim.summary(p), caught);
        assertTrue(caught, sim.summary(p) + " y " + before + " -> " + p.sy);
    }

    @ParameterizedTest(name = "nofall after the drop {0}ms")
    @ValueSource(ints = {50, 300})
    void noFallCheaterLandingFromTheBusIsCaught(int ping) {
        Sim sim = map();
        SimPlayer p = dropFromBus(sim, "DropNoFall" + ping, ping);
        p.cheatGround = true; // always tells the server "on ground"
        untilLanded(sim, p);
        boolean caught = sim.flagged(p, "NoFall");
        Report.add("CHEAT", "NoFall landing from the battle bus (" + ping + "ms)", sim.summary(p), caught);
        assertTrue(caught, sim.summary(p));
    }

    /** The client drives the boat: Paper fires PlayerMoveEvent for the vehicle packet. */
    static void moveBoat(Sim sim, SimPlayer p, SimEntity boat, int ticks, double rise) {
        for (int t = 0; t < ticks; t++) {
            Location from = new Location(sim.world.world, boat.x, boat.y, boat.z);
            boat.move(0.2, rise, 0.0);
            Location to = new Location(sim.world.world, boat.x, boat.y, boat.z);
            p.ride(boat.x, boat.y + 0.5, boat.z);
            sim.call(new PlayerMoveEvent(p.player, from, to));
            sim.tick(null);
        }
    }
}
