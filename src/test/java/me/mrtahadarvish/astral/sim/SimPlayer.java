package me.mrtahadarvish.astral.sim;

import me.mrtahadarvish.astral.util.BlockUtil;
import me.mrtahadarvish.astral.util.MathUtil;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * A simulated player. The "client" half runs vanilla movement physics and sends its position
 * like a vanilla client does; the "server" half is a Player mock that returns what the server
 * knows from the received packets.
 */
public final class SimPlayer {

    private static int nextEntityId = 100;

    public final SimWorld world;
    public final Player player;
    public final UUID uuid = UUID.randomUUID();
    public final String name;
    public final int entityId = nextEntityId++;
    public final Random random;

    // ---------------------------------------------------------------- server side (received)
    public double sx, sy, sz;
    public float syaw, spitch;
    public boolean sOnGround = true;
    public boolean sSprinting;

    // ---------------------------------------------------------------- client side
    public double x, y, z;
    public double mx, my, mz;
    public float yaw, pitch;
    public boolean onGround = true;
    public boolean sprinting, sneaking, jump;
    public float forward, strafe;
    public boolean usingItem, blocking;
    public int useTicks;
    public int slot;
    public int ping = 50;
    public GameMode gameMode = GameMode.SURVIVAL;
    public Material mainHand = Material.DIAMOND_SWORD;
    /** Hotbar contents (null = use mainHand for that slot). */
    public final Material[] hotbar = new Material[9];
    public String brand = "vanilla";
    public boolean gliding;
    public final Set<String> permissions = new HashSet<>();

    private int jumpDelay;
    private int positionReminder;
    private double lastSentX, lastSentY, lastSentZ;
    private float lastSentYaw, lastSentPitch;
    private boolean lastSentOnGround = true;
    private boolean lastSentSprint;
    /** Last position / rotation a PlayerMoveEvent was fired for (Paper's lastPosX / lastYaw). */
    double eventX, eventY, eventZ;
    float eventYaw, eventPitch;

    /**
     * Silent rotations: when set, the regular movement packet carries this rotation instead of
     * the one the client is looking with (movement still follows the client's own yaw).
     */
    public Float silentYaw, silentPitch;

    /** Server side fall distance, added up from every movement packet like the server does. */
    public double sFall;
    /** Entity#setGravity - a plugin can turn gravity off (server side / what the client applied). */
    public boolean gravity = true;
    public boolean clientGravity = true;

    // ---------------------------------------------------------------- minigames (battle bus ...)
    /** Vehicle the player rides, null = none. The server moves passengers, the client follows. */
    public SimEntity vehicle;
    /** Potion effects ("slow_falling", "levitation") the server knows / the client already applies. */
    public final Set<String> effects = new HashSet<>();
    public final Set<String> clientEffects = new HashSet<>();
    /** flying = client side flight, sFlying = what the server allows / knows. */
    public boolean flying, sFlying, allowFlight;
    /** Teleports Astral itself did (setbacks). */
    public int astralTeleports;
    /** Paper ignores movement until the client confirms the newest teleport. */
    private Location pendingTeleport;
    private int confirmIn;
    private int clientTick;
    private final List<Object[]> scheduled = new java.util.ArrayList<>();

    // ---------------------------------------------------------------- cheats
    /** Multiplies the horizontal movement (speed hack). */
    public double cheatSpeed = 1.0;
    /** Fly: replaces gravity, vertical motion is set to this every tick. */
    public Double cheatFlyY;
    /** NoFall: always tell the server "on ground". */
    public boolean cheatGround;
    /** Step height (vanilla 0.6). */
    public double stepHeight = 0.6;
    /** NoSlow: ignore the item use slow down. */
    public boolean cheatNoSlow;
    /** Air strafe: in the air the motion instantly follows the input direction. */
    public boolean cheatStrafe;
    /** Mouse sensitivity (vanilla option, 0.5 = 100%). */
    public double sensitivity = 0.5;

    public SimPlayer(SimWorld world, String name, double x, double y, double z, long seed) {
        this.world = world;
        this.name = name;
        this.random = new Random(seed);
        this.x = this.sx = this.lastSentX = x;
        this.y = this.sy = this.lastSentY = y;
        this.z = this.sz = this.lastSentZ = z;
        this.eventX = x;
        this.eventY = y;
        this.eventZ = z;
        this.player = Sim.mock(Player.class,
                invocation -> answer(invocation.getMethod().getName(), invocation.getArguments(), invocation));
        world.players.add(this);
    }

    // ================================================================ client physics

    public double speedAttribute(boolean sprint) {
        return 0.1 * (sprint ? 1.3 : 1.0);
    }

    public BoundingBox clientBox() {
        double h = sneaking ? 1.5 : 1.8;
        return new BoundingBox(x - 0.3, y, z - 0.3, x + 0.3, y + h, z + 0.3);
    }

    /** Runs a change on the client after some client ticks (it travels through the network). */
    public void later(int ticks, Runnable change) {
        if (ticks <= 0) {
            change.run();
        } else {
            scheduled.add(new Object[]{clientTick + ticks, change});
        }
    }

    /** Item in the selected hotbar slot. */
    public Material held() {
        return slot >= 0 && slot < 9 && hotbar[slot] != null ? hotbar[slot] : mainHand;
    }

    /** One way network delay in client ticks. */
    public int halfRtt() {
        return (int) Math.ceil(ping / 100.0);
    }

    /** One vanilla client tick of movement (LivingEntity#aiStep + travel). */
    public void physicsTick() {
        clientTick++;
        for (java.util.Iterator<Object[]> it = scheduled.iterator(); it.hasNext(); ) {
            Object[] task = it.next();
            if ((int) task[0] <= clientTick) {
                it.remove();
                ((Runnable) task[1]).run();
            }
        }
        if (pendingTeleport != null && --confirmIn <= 0) {
            // the teleport arrived: snap there and confirm it (the confirm carries this position)
            x = lastSentX = pendingTeleport.getX();
            y = lastSentY = pendingTeleport.getY();
            z = lastSentZ = pendingTeleport.getZ();
            mx = my = mz = 0.0;
            pendingTeleport = null;
        }
        if (vehicle != null) {
            return; // passengers are moved by their vehicle
        }
        if (gliding) {
            glideTick();
            return;
        }
        if (flying) {
            my = jump ? 0.375 : sneaking ? -0.375 : my * 0.6;
        }
        if (Math.abs(mx) < 0.003) {
            mx = 0.0;
        }
        if (Math.abs(my) < 0.003) {
            my = 0.0;
        }
        if (Math.abs(mz) < 0.003) {
            mz = 0.0;
        }
        if (jumpDelay > 0) {
            jumpDelay--;
        }
        if (jump) {
            if (onGround && jumpDelay == 0) {
                my = Math.max(0.42F, my);
                if (sprinting) {
                    double r = Math.toRadians(yaw);
                    mx += -Math.sin(r) * 0.2;
                    mz += Math.cos(r) * 0.2;
                }
                jumpDelay = 10;
            }
        } else {
            jumpDelay = 0;
        }

        double fw = forward * 0.98;
        double st = strafe * 0.98;
        if (sneaking) {
            fw *= 0.3;
            st *= 0.3;
        }
        if (usingItem && !cheatNoSlow) {
            fw *= 0.2;
            st *= 0.2;
        }
        double friction = onGround ? BlockUtil.friction(world.type((int) Math.floor(x), (int) Math.floor(y - 0.5000001), (int) Math.floor(z))) : 1.0;
        double speed = onGround ? speedAttribute(sprinting) * (0.21600002 / (friction * friction * friction)) : (sprinting ? 0.026 : 0.02);
        double length = st * st + fw * fw;
        if (length > 1.0E-7) {
            if (length > 1.0) {
                double n = Math.sqrt(length);
                st /= n;
                fw /= n;
            }
            double r = Math.toRadians(yaw);
            double sin = Math.sin(r);
            double cos = Math.cos(r);
            if (cheatStrafe && !onGround) {
                double h = Math.hypot(mx, mz);
                mx = (st * cos - fw * sin) * h;
                mz = (fw * cos + st * sin) * h;
            } else {
                mx += (st * cos - fw * sin) * speed;
                mz += (fw * cos + st * sin) * speed;
            }
        }
        if (cheatFlyY != null) {
            my = cheatFlyY;
        }
        Vector wanted = new Vector(mx * cheatSpeed, my, mz * cheatSpeed);
        Vector moved = world.collide(clientBox(), wanted, onGround, stepHeight);
        boolean collX = moved.getX() != wanted.getX();
        boolean collZ = moved.getZ() != wanted.getZ();
        boolean collY = moved.getY() != wanted.getY();
        x += moved.getX();
        y += moved.getY();
        z += moved.getZ();
        onGround = collY && wanted.getY() < 0.0;
        if (collX) {
            mx = 0.0;
        }
        if (collZ) {
            mz = 0.0;
        }
        if (collY) {
            my = 0.0;
        }
        if (cheatFlyY == null && !flying) {
            if (clientEffects.contains("levitation")) {
                my += (0.05 - my) * 0.2;
            } else if (clientGravity) {
                my -= clientEffects.contains("slow_falling") && my <= 0.0 ? 0.01 : 0.08;
            }
            my *= 0.98;
        }
        mx *= friction * 0.91;
        mz *= friction * 0.91;
        if (usingItem) {
            useTicks++;
        }
    }

    /** LivingEntity#updateFallFlyingMovement (elytra), then the move. */
    private void glideTick() {
        double yawR = Math.toRadians(yaw);
        double pitchR = Math.toRadians(pitch);
        double lx = -Math.sin(yawR) * Math.cos(pitchR);
        double ly = -Math.sin(pitchR);
        double lz = Math.cos(yawR) * Math.cos(pitchR);
        double horizontalLook = Math.sqrt(lx * lx + lz * lz);
        double horizontalSpeed = Math.sqrt(mx * mx + mz * mz);
        double gravityNow = !clientGravity ? 0.0 : clientEffects.contains("slow_falling") && my <= 0.0 ? 0.01 : 0.08;
        double cos2 = Math.cos(pitchR) * Math.cos(pitchR);
        double length = Math.sqrt(lx * lx + ly * ly + lz * lz);
        cos2 *= Math.min(1.0, length / 0.4);
        my += gravityNow * (-1.0 + cos2 * 0.75);
        if (my < 0.0 && horizontalLook > 0.0) {
            double d = my * -0.1 * cos2;
            mx += lx * d / horizontalLook;
            my += d;
            mz += lz * d / horizontalLook;
        }
        if (pitchR < 0.0 && horizontalLook > 0.0) {
            double d = horizontalSpeed * -Math.sin(pitchR) * 0.04;
            mx += -lx * d / horizontalLook;
            my += d * 3.2;
            mz += -lz * d / horizontalLook;
        }
        if (horizontalLook > 0.0) {
            mx += (lx / horizontalLook * horizontalSpeed - mx) * 0.1;
            mz += (lz / horizontalLook * horizontalSpeed - mz) * 0.1;
        }
        mx *= 0.99;
        my *= 0.98;
        mz *= 0.99;
        if (cheatFlyY != null) {
            my = cheatFlyY; // elytra fly cheats override the glide physics
        }
        Vector wanted = new Vector(mx * cheatSpeed, my, mz * cheatSpeed);
        Vector moved = world.collide(clientBox(), wanted, false, stepHeight);
        x += moved.getX();
        y += moved.getY();
        z += moved.getZ();
        onGround = moved.getY() != wanted.getY() && wanted.getY() < 0.0;
        if (moved.getX() != wanted.getX()) {
            mx = 0.0;
        }
        if (moved.getZ() != wanted.getZ()) {
            mz = 0.0;
        }
        if (moved.getY() != wanted.getY()) {
            my = 0.0;
        }
    }

    /** The server moves a passenger with its vehicle (no movement packets). */
    public void ride(double px, double py, double pz) {
        x = sx = lastSentX = eventX = px;
        y = sy = lastSentY = eventY = py;
        z = sz = lastSentZ = eventZ = pz;
        mx = my = mz = 0.0;
    }

    /** LocalPlayer#sendPosition: sends the movement packet the server will process. */
    public void sendMovement() {
        if (vehicle != null) {
            return; // the client sends vehicle / rotation packets only
        }
        positionReminder++;
        double dx = x - lastSentX;
        double dy = y - lastSentY;
        double dz = z - lastSentZ;
        boolean position = dx * dx + dy * dy + dz * dz > 4.0E-8 || positionReminder >= 20;
        float sendYaw = silentYaw != null ? silentYaw : yaw;
        float sendPitch = silentPitch != null ? silentPitch : pitch;
        boolean rotation = sendYaw != lastSentYaw || sendPitch != lastSentPitch;
        boolean ground = cheatGround || onGround;
        if (sprinting != lastSentSprint) {
            sSprinting = sprinting;
            lastSentSprint = sprinting;
        }
        if (position && pendingTeleport != null) {
            position = false; // Paper ignores moves until the teleport is confirmed
            lastSentX = x;
            lastSentY = y;
            lastSentZ = z;
        }
        if (position) {
            serverFall(y - sy, ground);
            sx = x;
            sy = y;
            sz = z;
            lastSentX = x;
            lastSentY = y;
            lastSentZ = z;
            positionReminder = 0;
        }
        if (rotation) {
            syaw = MathUtil.wrapDegrees(sendYaw);
            spitch = Math.max(-90.0F, Math.min(90.0F, sendPitch));
            lastSentYaw = sendYaw;
            lastSentPitch = sendPitch;
        }
        if (position || rotation || ground != lastSentOnGround) {
            if (!position) {
                serverFall(0.0, ground);
            }
            sOnGround = ground;
            lastSentOnGround = ground;
        }
        if (position || rotation) {
            Sim.current().moved(this);
        }
    }

    /** ServerPlayer#doCheckFallDamage: trusts the on ground flag of the packet. */
    private void serverFall(double dy, boolean ground) {
        if (ground) {
            sFall = 0.0;
        } else if (dy < 0.0) {
            sFall -= dy;
        }
    }

    /** Server side teleport (also moves the client). */
    public void teleportTo(Location location) {
        sx = eventX = location.getX();
        sy = eventY = location.getY();
        sz = eventZ = location.getZ();
        eventYaw = syaw;
        eventPitch = spitch;
        // the client gets there half a round trip later; until it confirms, Paper ignores its moves
        pendingTeleport = location.clone();
        confirmIn = Math.max(1, halfRtt());
    }

    /** Sends a raw position packet (cheat clients can send anything). */
    public void sendRawPosition(double px, double py, double pz, float pyaw, float ppitch, boolean ground) {
        if (pendingTeleport != null) {
            return;
        }
        serverFall(py - sy, ground);
        sx = px;
        sy = py;
        sz = pz;
        syaw = MathUtil.wrapDegrees(pyaw);
        spitch = ppitch;
        sOnGround = ground;
        // the client's network state now holds these values (it re-sends its real rotation next tick)
        lastSentX = px;
        lastSentY = py;
        lastSentZ = pz;
        lastSentYaw = pyaw;
        lastSentPitch = ppitch;
        lastSentOnGround = ground;
        Sim.current().moved(this);
    }

    // ================================================================ mouse

    /** One mouse count in degrees for the current sensitivity. */
    public double mouseUnit() {
        double f = sensitivity * 0.6 + 0.2;
        return f * f * f * 8.0 * 0.15;
    }

    /** Turns like a mouse: whole mouse counts times the sensitivity step. */
    public void mouseTurn(int countsX, int countsY) {
        double f = sensitivity * 0.6 + 0.2;
        double scale = f * f * f * 8.0;
        float dyaw = (float) (countsX * scale) * 0.15F;
        float dpitch = (float) (countsY * scale) * 0.15F;
        yaw = yaw + dyaw;
        pitch = Math.max(-90.0F, Math.min(90.0F, pitch + dpitch));
    }

    /** Human like aim at a point: moves part of the way with whole mouse counts plus noise. */
    public void mouseAim(Vector point, double gain, int noise) {
        double[] target = rotationTo(eyeClient(), point);
        double unit = mouseUnit();
        double dyaw = MathUtil.wrapDegrees((float) (target[0] - yaw));
        double dpitch = target[1] - pitch;
        int cx = (int) Math.round(dyaw * gain / unit) + (noise > 0 ? random.nextInt(noise * 2 + 1) - noise : 0);
        int cy = (int) Math.round(dpitch * gain / unit) + (noise > 0 ? random.nextInt(noise * 2 + 1) - noise : 0);
        mouseTurn(cx, cy);
    }

    public Vector eyeClient() {
        return new Vector(x, y + (sneaking ? 1.27 : 1.62), z);
    }

    public Vector eyeServer() {
        return new Vector(sx, sy + (sneaking ? 1.27 : 1.62), sz);
    }

    public static double[] rotationTo(Vector from, Vector to) {
        Vector d = to.clone().subtract(from);
        double yaw = Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
        double pitch = Math.toDegrees(-Math.atan2(d.getY(), Math.hypot(d.getX(), d.getZ())));
        return new double[]{yaw, pitch};
    }

    // ================================================================ the Player mock

    public BoundingBox serverBox() {
        double h = sneaking ? 1.5 : 1.8;
        return new BoundingBox(sx - 0.3, sy, sz - 0.3, sx + 0.3, sy + h, sz + 0.3);
    }

    private Object answer(String name, Object[] a, org.mockito.invocation.InvocationOnMock invocation) throws Throwable {
        switch (name) {
            case "getUniqueId": return uuid;
            case "getName": return this.name;
            case "getEntityId": return entityId;
            case "getType": return EntityType.PLAYER;
            case "getWorld": return world.world;
            case "getLocation":
                if (a.length == 0) {
                    return new Location(world.world, sx, sy, sz, syaw, spitch);
                }
                Location target = (Location) a[0];
                target.setWorld(world.world);
                target.setX(sx);
                target.setY(sy);
                target.setZ(sz);
                target.setYaw(syaw);
                target.setPitch(spitch);
                return target;
            case "getBoundingBox": return serverBox();
            case "getEyeLocation": return new Location(world.world, sx, sy + (sneaking ? 1.27 : 1.62), sz, syaw, spitch);
            case "getEyeHeight": return sneaking ? 1.27 : 1.62;
            case "isOnGround": return sOnGround;
            case "isSneaking": return sneaking;
            case "isSprinting": return sSprinting;
            case "isClimbing": return BlockUtil.isClimbable(world.type((int) Math.floor(sx), (int) Math.floor(sy), (int) Math.floor(sz)));
            case "getAttribute": return attribute((Attribute) a[0]);
            case "getGameMode": return gameMode;
            case "hasActiveItem", "isHandRaised": return usingItem;
            case "getActiveItem": return item(usingItem ? held() : Material.AIR);
            case "getActiveItemUsedTime": return usingItem ? useTicks : 0;
            case "getActiveItemRemainingTime": return usingItem ? Math.max(0, 72000 - useTicks) : 0;
            case "isBlocking": return blocking;
            case "getPing": return ping;
            case "getCurrentInput": return input();
            case "getInventory": return inventory();
            case "getNearbyEntities": return world.nearby(new Vector(sx, sy + 0.9, sz), (Double) a[0], (Double) a[1], (Double) a[2], player);
            case "getVelocity": return new Vector();
            case "getFoodLevel": return 20;
            case "getFallDistance": return (float) sFall;
            case "setFallDistance": sFall = ((Number) a[0]).doubleValue(); return null;
            case "hasGravity": return gravity;
            case "isInsideVehicle": return vehicle != null;
            case "getVehicle": return vehicle == null ? null : vehicle.entity;
            case "hasPotionEffect": return a[0] instanceof org.bukkit.potion.PotionEffectType type && effects.contains(type.getKey().getKey());
            case "isFlying": return sFlying;
            case "getAllowFlight": return allowFlight;
            case "isInWater": return world.type((int) Math.floor(sx), (int) Math.floor(sy), (int) Math.floor(sz)) == Material.WATER;
            case "isInLava": return world.type((int) Math.floor(sx), (int) Math.floor(sy), (int) Math.floor(sz)) == Material.LAVA;
            case "hasPermission": return a[0] instanceof String s && permissions.contains(s);
            case "isOnline", "isValid": return true;
            case "getClientBrandName": return brand;
            case "isGliding": return gliding;
            case "getProtocolVersion": return 774;
            case "getHealth": return 20.0;
            case "getTargetBlockExact": {
                Vector eye = eyeServer();
                RayTraceResult hit = world.rayTrace(eye, MathUtil.direction(syaw, spitch), ((Number) a[0]).doubleValue());
                return hit == null ? null : hit.getHitBlock();
            }
            case "teleport", "teleportAsync": {
                astralTeleports++;
                Location location = (Location) a[0];
                Sim.current().teleport(this, location);
                return true;
            }
            case "hashCode": return System.identityHashCode(invocation.getMock());
            case "toString": return "SimPlayer[" + this.name + "]";
            default: return Mockito.RETURNS_DEFAULTS.answer(invocation);
        }
    }

    private final java.util.Map<String, AttributeInstance> attributes = new java.util.HashMap<>();
    private static final java.util.Map<Material, ItemStack> ITEMS = new java.util.EnumMap<>(Material.class);
    private PlayerInventory inventory;

    private AttributeInstance attribute(Attribute attribute) {
        String key = attribute.getKey().getKey();
        return attributes.computeIfAbsent(key, k -> Sim.mock(AttributeInstance.class,
                invocation -> switch (invocation.getMethod().getName()) {
                    case "getValue", "getBaseValue" -> attributeValue(k);
                    default -> Mockito.RETURNS_DEFAULTS.answer(invocation);
                }));
    }

    private double attributeValue(String key) {
        return switch (key) {
            case "movement_speed" -> speedAttribute(sSprinting);
            case "jump_strength" -> 0.42F;
            case "step_height" -> 0.6;
            case "gravity" -> 0.08;
            case "entity_interaction_range" -> gameMode == GameMode.CREATIVE ? 5.0 : 3.0;
            case "block_interaction_range" -> gameMode == GameMode.CREATIVE ? 5.0 : 4.5;
            case "safe_fall_distance" -> 3.0;
            case "fall_damage_multiplier" -> 1.0;
            case "sneaking_speed" -> 0.3;
            default -> 0.0;
        };
    }

    public static synchronized ItemStack item(Material material) {
        return ITEMS.computeIfAbsent(material, m -> Mockito.mock(ItemStack.class, Mockito.withSettings().stubOnly()
                .defaultAnswer((Answer<Object>) invocation -> switch (invocation.getMethod().getName()) {
                    case "getType" -> m;
                    case "getAmount" -> 1;
                    case "isEmpty" -> m.isAir();
                    default -> Mockito.RETURNS_DEFAULTS.answer(invocation);
                })));
    }

    private PlayerInventory inventory() {
        if (inventory == null) {
            inventory = Sim.mock(PlayerInventory.class, invocation -> switch (invocation.getMethod().getName()) {
                case "getItemInMainHand" -> item(held());
                case "getItem" -> invocation.getArguments().length == 1 && invocation.getArguments()[0] instanceof Integer i
                        ? item(i >= 0 && i < 9 && hotbar[i] != null ? hotbar[i] : Material.AIR) : null;
                case "getItemInOffHand" -> item(Material.AIR);
                case "getHeldItemSlot" -> slot;
                default -> Mockito.RETURNS_DEFAULTS.answer(invocation);
            });
        }
        return inventory;
    }

    private Input input() {
        final boolean f = forward > 0;
        final boolean b = forward < 0;
        final boolean l = strafe > 0;
        final boolean r = strafe < 0;
        final boolean j = jump;
        final boolean s = sneaking;
        final boolean sp = sprinting;
        return new Input() {
            public boolean isForward() { return f; }
            public boolean isBackward() { return b; }
            public boolean isLeft() { return l; }
            public boolean isRight() { return r; }
            public boolean isJump() { return j; }
            public boolean isSneak() { return s; }
            public boolean isSprint() { return sp; }
        };
    }

    /** Releases every key (what vanilla does when a screen opens). */
    public void releaseKeys() {
        forward = 0;
        strafe = 0;
        jump = false;
        sprinting = false;
    }

    public Block blockBelow() {
        return world.block((int) Math.floor(x), (int) Math.floor(y - 0.5), (int) Math.floor(z));
    }

    public static List<String> names(List<SimPlayer> players) {
        return players.stream().map(p -> p.name).toList();
    }
}
