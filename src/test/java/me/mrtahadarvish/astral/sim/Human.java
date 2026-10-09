package me.mrtahadarvish.astral.sim;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * Human-like behaviour for a simulated player: mouse aiming with whole mouse counts, clicking
 * with human timing noise, strafing, jumping, taking knockback after its ping.
 */
public final class Human {

    public final SimPlayer self;
    private final Random random;
    private double nextClickMs;
    private double timeMs;
    private int missTime;
    private int strafeTicks;
    private final Deque<Vector[]> history = new ArrayDeque<>();
    private final Deque<Object[]> pendingVelocity = new ArrayDeque<>();
    public int hurtTime;
    public double clickMean;
    public double clickDeviation;
    public int hits;
    public int misses;

    public Human(SimPlayer self, double cps, long seed) {
        this.self = self;
        this.random = new Random(seed);
        this.clickMean = 1000.0 / cps;
        this.clickDeviation = this.clickMean * 0.22;
        this.nextClickMs = random.nextDouble() * clickMean;
    }

    /** Remembers the server box of a player every tick (what others see, a few ticks late). */
    public void record(SimPlayer target) {
        history.addLast(new Vector[]{target.serverBox().getMin(), target.serverBox().getMax()});
        while (history.size() > 6) {
            history.removeFirst();
        }
    }

    /** The target as this client sees it (3 ticks old: ping + entity interpolation). */
    public BoundingBox seen(SimPlayer target) {
        Vector[] old = history.peekFirst();
        if (old == null) {
            return target.serverBox();
        }
        return BoundingBox.of(old[0], old[1]);
    }

    public void queueVelocity(Vector velocity, int delayTicks) {
        pendingVelocity.addLast(new Object[]{velocity, delayTicks});
    }

    /** Applies knockback that reached the client. */
    public void applyVelocity() {
        for (Object[] entry : pendingVelocity) {
            entry[1] = (Integer) entry[1] - 1;
        }
        while (!pendingVelocity.isEmpty() && (Integer) pendingVelocity.peekFirst()[1] <= 0) {
            Vector v = (Vector) pendingVelocity.removeFirst()[0];
            self.mx = v.getX();
            self.my = v.getY();
            self.mz = v.getZ();
        }
    }

    /** Fighting movement: keep ~2.6 blocks distance, strafe, sometimes jump. */
    public void move(SimPlayer target) {
        double dx = target.x - self.x;
        double dz = target.z - self.z;
        double distance = Math.hypot(dx, dz);
        self.forward = distance > 2.9 ? 1 : distance < 2.0 ? -1 : (random.nextDouble() < 0.5 ? 1 : 0);
        if (--strafeTicks <= 0) {
            strafeTicks = 8 + random.nextInt(25);
            self.strafe = random.nextInt(3) - 1;
        }
        self.sprinting = self.forward > 0 && !self.sneaking && !self.usingItem;
        self.jump = self.onGround && random.nextDouble() < 0.04;
    }

    /** Aims with the mouse at a point on the target's (seen) body. */
    public void aim(SimPlayer target) {
        BoundingBox box = seen(target);
        Vector point = new Vector(box.getCenterX() + (random.nextDouble() - 0.5) * 0.3,
                box.getMinY() + 1.0 + random.nextDouble() * 0.6, box.getCenterZ() + (random.nextDouble() - 0.5) * 0.3);
        self.mouseAim(point, 0.35 + random.nextDouble() * 0.35, 3);
    }

    /** True when the crosshair is on the target (what the client's pick would see). */
    public boolean crosshairOn(SimPlayer target) {
        Vector eye = self.eyeClient();
        Vector direction = me.mrtahadarvish.astral.util.MathUtil.direction(self.yaw, self.pitch);
        BoundingBox box = seen(target);
        return box.rayTrace(eye, direction, 3.0) != null || box.contains(eye);
    }

    /**
     * Number of clicks that happened since the last tick (human timing). Clicks during the miss
     * penalty are swallowed like in vanilla.
     */
    public int clicksThisTick() {
        timeMs += 50.0;
        int clicks = 0;
        while (nextClickMs <= timeMs) {
            clicks++;
            double interval = clickMean + random.nextGaussian() * clickDeviation;
            if (random.nextDouble() < 0.06) {
                interval += 150 + random.nextDouble() * 400; // short pauses
            }
            nextClickMs += Math.max(25.0, interval);
        }
        if (missTime > 0) {
            missTime--;
            return 0;
        }
        return clicks;
    }

    /** Vanilla handleKeybinds for the attack key. Returns true if the target got hit. */
    public boolean click(Sim sim, SimPlayer target) {
        if (missTime > 0) {
            return false;
        }
        if (crosshairOn(target)) {
            boolean hit = sim.attack(self, target.player);
            sim.swing(self);
            hits++;
            return hit;
        }
        sim.swing(self);
        misses++;
        missTime = 10;
        return false;
    }

    public Random random() {
        return random;
    }
}
