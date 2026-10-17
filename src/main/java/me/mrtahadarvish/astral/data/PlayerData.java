package me.mrtahadarvish.astral.data;

import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.manager.AlertDigest;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Everything Astral knows about one online player. All fields are only touched on the main
 * thread (chat checks keep their own thread safe state).
 */
public final class PlayerData {

    private static final int NEVER = -100_000;

    private final UUID uuid;
    private final Player player;
    public final int joinTick;
    public final long joinTime = System.currentTimeMillis();

    // ------------------------------------------------------------------ bypass / staff
    public volatile boolean bypassPermission;
    public volatile Set<String> bypassChecks = Collections.emptySet();
    public boolean bedrock;
    public boolean alerts;
    public boolean verbose;
    public final Set<String> debugChecks = new HashSet<>();
    public UUID cpsTarget;

    // ------------------------------------------------------------------ violations
    private final Map<String, Double> violations = new HashMap<>();
    public final Map<String, Long> lastAlert = new HashMap<>();
    /** Flags waiting for the next compact staff alert. */
    public final AlertDigest alertDigest = new AlertDigest();
    public final Set<String> punished = new HashSet<>();
    public final Deque<FlagRecord> flags = new ArrayDeque<>();
    public int totalFlags;

    private final Map<String, Buffer> buffers = new HashMap<>();
    private final Map<Class<?>, Object> states = new HashMap<>();

    // ------------------------------------------------------------------ client ticks
    /** Number of client ticks received (ClientTickEndEvent). */
    public int clientTicks;

    // ------------------------------------------------------------------ movement (per client tick)
    public boolean hasPosition;
    public boolean resetPosition = true;
    public UUID world;
    public double x, y, z, lastX, lastY, lastZ;
    public float yaw, pitch, lastYaw, lastPitch;
    public double deltaX, deltaY, deltaZ, deltaXZ;
    public double lastDeltaX, lastDeltaY, lastDeltaZ, lastDeltaXZ, lastLastDeltaY;
    public float deltaYaw, deltaPitch, lastDeltaYaw, lastDeltaPitch;
    public double eyeHeight = 1.62;

    /** Ground calculated by Astral from real block collisions. */
    public boolean onGround, lastOnGround, lastLastOnGround;
    /** Ground with a small margin (edges, rounding). */
    public boolean groundLenient;
    /** Ground state the client claims in its movement packets. */
    public boolean clientOnGround, lastClientOnGround;
    public int airTicks, groundTicks;
    public double friction = 0.6, lastFriction = 0.6, lastLastFriction = 0.6;

    public boolean inLiquid, nearLiquid, liquidBelow;
    /** How deep the feet are inside a fluid (0 when not in a fluid). */
    public double liquidDepth;
    public boolean climbing, nearClimbable, inWeb, nearPowderSnow, nearHoney, nearSlime, nearBed, nearPiston, nearScaffolding;
    public boolean headBlocked, horizontalCollision;
    public boolean gliding, swimming, riptiding, inVehicle, flying, sneaking, sprinting;
    public boolean chunkLoaded = true;
    /** True when the movement of this tick must not be checked (teleport, first tick ...). */
    public boolean skipMovement = true;

    public double speedAttribute = 0.1;
    public double jumpStrength = 0.42;
    public double stepHeight = 0.6;
    /** Gravity of the player: the gravity attribute, or 0 while a plugin turned gravity off. */
    public double gravity = 0.08;
    public boolean slowFalling;
    /**
     * Gravity / slow falling before the last change, and when it changed. The client applies such
     * a change about half a round trip later, so for a short while both are possible.
     */
    public double oldGravity = 0.08;
    public boolean oldSlowFalling;
    public int gravityChange = NEVER;

    // ------------------------------------------------------------------ exemption timestamps (server ticks)
    public int lastTeleport = NEVER, lastVelocity = NEVER, lastFlight = NEVER, lastGlide = NEVER, lastRiptide = NEVER;
    public int lastVehicle = NEVER, lastLiquid = NEVER, lastClimb = NEVER, lastWeb = NEVER, lastPowderSnow = NEVER;
    public int lastHoney = NEVER, lastSlime = NEVER, lastBed = NEVER, lastPiston = NEVER, lastLevitation = NEVER;
    public int lastGameMode = NEVER, lastRespawn = NEVER, lastWorldChange = NEVER, lastGhostBlock = NEVER;
    public int lastFirework = NEVER, lastSpear = NEVER, lastSetback = NEVER, lastHeadBlocked = NEVER;
    public int lastCollision = NEVER, lastScaffolding = NEVER, lastDolphin = NEVER, lastSlowFalling = NEVER;
    public int lastDamage = NEVER, lastPush = NEVER, lastFailMove = NEVER, lastExplosion = NEVER;
    /** Standing on / touching a boat, minecart, shulker or happy ghast (moving platforms). */
    public int lastEntityPlatform = NEVER;
    /** A plugin turned gravity off for the player (Entity#setGravity(false)). */
    public int lastNoGravity = NEVER;
    public boolean nearEntityPlatform;
    /** A fall damage event was cancelled by another plugin (minigames cancel landing damage). */
    public int lastFallCancelled = NEVER;
    /**
     * Paper ignores movement until the client confirms a teleport, so the position stays at the
     * teleport target for about one round trip (a teleport every tick = frozen the whole time).
     */
    public boolean teleportPending;
    public double teleportX, teleportY, teleportZ;
    public int teleportTick = NEVER;
    /** Left a vehicle: the client may still answer once with its old seat position. */
    public boolean dismountPending;

    // ------------------------------------------------------------------ lag compensation
    /** Bounding boxes of this player for the last 40 server ticks. */
    public final PositionHistory history = new PositionHistory(40);

    // ------------------------------------------------------------------ velocity
    public final Deque<VelocityRecord> velocities = new ArrayDeque<>();
    public double lastVelocityHorizontal, lastVelocityVertical;

    // ------------------------------------------------------------------ combat
    public int attacksThisTick, swingsThisTick;
    public final Set<Integer> targetsThisTick = new HashSet<>();
    public int lastAttackTick = NEVER;
    public int lastAttackClientTick = NEVER;
    public int combatLockUntil = NEVER;
    public boolean hasTickSnapshot;
    public double tickEndX, tickEndY, tickEndZ;
    public float tickEndYaw, tickEndPitch;

    // ------------------------------------------------------------------ clicks / interactions
    public int clicksThisTick;
    private final int[] clickWindow = new int[20];
    private int clickWindowIndex;
    public int placedThisTick, brokenThisTick, inventoryClicksThisTick, slotChangesThisTick;
    /** Attacks, item uses, block / entity interactions and dig starts in the current client tick. */
    public int actionsThisTick;
    /** PlayerMoveEvents (movement packets) in the current client tick. Vanilla sends one at most. */
    public int movesThisTick;
    /** Real client protocol (ViaVersion aware), looked up once. -1 = not looked up yet. */
    public int protocol = -1;
    public boolean attackedBeforeSlotChange;
    public int placeLockUntil = NEVER;
    public boolean digging;
    /** Set when a movement check flagged during the current client tick. */
    public boolean movementFlagged;
    /** Set when a movement check grew a suspicion buffer this tick (the position is not trusted). */
    public boolean movementSuspicious;
    /** Counts every Buffer#increase of this player. */
    public int bufferIncreases;

    // ------------------------------------------------------------------ setback / nofall
    /** Last position on real ground that passed every movement check. */
    public Location lastSafe;
    /** Last position (anywhere, also in the air) that passed every movement check. */
    public boolean hasLegit;
    public double legitX, legitY, legitZ;
    public UUID legitWorld;
    public double fallDistance;
    /**
     * Highest fall distance the server can legitimately have for this player (sum of the downward
     * moves since the client last said "on ground" or touched water). Used by the mace check.
     */
    public double legitFall;
    public boolean groundSpoofed;
    public double fallDamageTaken;

    public PlayerData(Player player) {
        this.player = player;
        this.uuid = player.getUniqueId();
        this.joinTick = Bukkit.getCurrentTick();
    }

    public UUID uuid() {
        return uuid;
    }

    public Player player() {
        return player;
    }

    public String name() {
        return player.getName();
    }

    // ------------------------------------------------------------------ helpers

    public static int now() {
        return Bukkit.getCurrentTick();
    }

    /** Server ticks since a stored timestamp. */
    public static int since(int tick) {
        return now() - tick;
    }

    public boolean isBypassing(String checkId) {
        return bypassPermission || bypassChecks.contains(checkId);
    }

    /** Temporary exemptions from /astral exempt (null key = every category). Server tick it ends at. */
    private final Map<CheckCategory, Integer> exemptUntil = new HashMap<>();
    private int exemptAllUntil = NEVER;

    /** Exempts the player for some server ticks. category null = from every check. */
    public void exempt(CheckCategory category, int ticks) {
        int until = now() + ticks;
        if (category == null) {
            exemptAllUntil = until;
        } else {
            exemptUntil.put(category, until);
        }
    }

    public void clearExemptions() {
        exemptAllUntil = NEVER;
        exemptUntil.clear();
    }

    public boolean isExempt(CheckCategory category) {
        int now = now();
        if (now < exemptAllUntil) {
            return true;
        }
        Integer until = exemptUntil.get(category);
        return until != null && now < until;
    }

    /** Server ticks left of the longest running exemption (0 = none). */
    public int exemptTicksLeft() {
        int now = now();
        int max = exemptAllUntil - now;
        for (int until : exemptUntil.values()) {
            max = Math.max(max, until - now);
        }
        return Math.max(0, max);
    }

    public Buffer buffer(String key) {
        return buffers.computeIfAbsent(key, k -> new Buffer(this));
    }

    @SuppressWarnings("unchecked")
    public <T> T state(Class<T> type, Supplier<T> creator) {
        return (T) states.computeIfAbsent(type, k -> creator.get());
    }

    public double violation(String check) {
        return violations.getOrDefault(check, 0.0);
    }

    public double addViolation(String check, double amount) {
        double value = violation(check) + amount;
        violations.put(check, value);
        return value;
    }

    public void setViolation(String check, double value) {
        if (value <= 0.0) {
            violations.remove(check);
            punished.remove(check);
        } else {
            violations.put(check, value);
        }
    }

    public Map<String, Double> violations() {
        return violations;
    }

    public double totalViolations() {
        double sum = 0.0;
        for (double v : violations.values()) {
            sum += v;
        }
        return sum;
    }

    public void resetViolations() {
        violations.clear();
        punished.clear();
        buffers.clear();
    }

    public void resetViolations(String check) {
        violations.remove(check);
        punished.remove(check);
    }

    /** Called once per client tick to roll the CPS window. */
    public void rollClickWindow() {
        clickWindowIndex = (clickWindowIndex + 1) % clickWindow.length;
        clickWindow[clickWindowIndex] = clicksThisTick;
    }

    /** Clicks in the last 20 client ticks (= one second). */
    public int cps() {
        int sum = 0;
        for (int c : clickWindow) {
            sum += c;
        }
        return sum;
    }

    public int ping() {
        return player.getPing();
    }

    /** Ping converted to ticks (rounded up). */
    public int pingTicks() {
        return (int) Math.ceil(Math.max(0, ping()) / 50.0);
    }

    public boolean inCombat() {
        return since(lastAttackTick) < 60;
    }

    /** Server ticks after leaving a vehicle during which the client can still be on the seat. */
    public int vehicleExitTicks() {
        return 20 + pingTicks();
    }

    /** Riding, or just got off (the client sees its vehicle late, so its own eyes trail behind). */
    public boolean recentlyRode() {
        return inVehicle || since(lastVehicle) < vehicleExitTicks();
    }
}
