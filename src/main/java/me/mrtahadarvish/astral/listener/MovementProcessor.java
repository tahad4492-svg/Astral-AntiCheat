package me.mrtahadarvish.astral.listener;

import io.papermc.paper.event.packet.ClientTickEndEvent;
import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.TickCheck;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.BlockUtil;
import me.mrtahadarvish.astral.util.MathUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;

import java.util.logging.Level;

/**
 * Heart of Astral. Since Minecraft 1.21.2 every client sends a "tick end" packet once per client
 * tick, right after its movement packet. Paper exposes it as {@link ClientTickEndEvent}, which
 * lets Astral read the exact position of every single client tick and simulate vanilla physics,
 * instead of guessing from PlayerMoveEvent.
 */
public final class MovementProcessor implements Listener {

    private final AstralAntiCheat plugin;
    private boolean errorLogged;

    public MovementProcessor(AstralAntiCheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTickEnd(ClientTickEndEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.data().get(player);
        if (data == null) {
            return;
        }
        data.clientTicks++;
        update(player, data);

        for (TickCheck check : plugin.checks().tickChecks()) {
            try {
                check.onTick(data);
            } catch (Throwable throwable) {
                if (!errorLogged) {
                    errorLogged = true;
                    plugin.getLogger().log(Level.WARNING, "Error in check " + check.getClass().getSimpleName(), throwable);
                }
            }
        }
        finish(player, data);
    }

    @SuppressWarnings("deprecation") // Player#isOnGround is client controlled - that is exactly what NoFall compares
    private void update(Player player, PlayerData data) {
        Location location = player.getLocation();
        World world = location.getWorld();
        int now = PlayerData.now();

        data.lastLastOnGround = data.lastOnGround;
        data.lastOnGround = data.onGround;
        data.lastClientOnGround = data.clientOnGround;
        data.lastLastFriction = data.lastFriction;
        data.lastFriction = data.friction;

        data.lastLastDeltaY = data.lastDeltaY;
        data.lastDeltaX = data.deltaX;
        data.lastDeltaY = data.deltaY;
        data.lastDeltaZ = data.deltaZ;
        data.lastDeltaXZ = data.deltaXZ;
        data.lastDeltaYaw = data.deltaYaw;
        data.lastDeltaPitch = data.deltaPitch;

        data.lastX = data.x;
        data.lastY = data.y;
        data.lastZ = data.z;
        data.lastYaw = data.yaw;
        data.lastPitch = data.pitch;

        data.x = location.getX();
        data.y = location.getY();
        data.z = location.getZ();
        data.yaw = location.getYaw();
        data.pitch = location.getPitch();

        boolean worldChanged = data.world == null || !data.world.equals(world.getUID());
        if (!data.hasPosition || data.resetPosition || worldChanged) {
            data.lastX = data.x;
            data.lastY = data.y;
            data.lastZ = data.z;
            data.lastYaw = data.yaw;
            data.lastPitch = data.pitch;
            data.deltaX = data.deltaY = data.deltaZ = data.deltaXZ = 0.0;
            data.lastDeltaX = data.lastDeltaY = data.lastDeltaZ = data.lastDeltaXZ = data.lastLastDeltaY = 0.0;
            data.deltaYaw = data.deltaPitch = data.lastDeltaYaw = data.lastDeltaPitch = 0.0F;
            data.hasPosition = true;
            data.resetPosition = false;
            data.world = world.getUID();
            data.skipMovement = true;
            data.fallDistance = 0.0;
            data.groundSpoofed = false;
        } else {
            data.deltaX = data.x - data.lastX;
            data.deltaY = data.y - data.lastY;
            data.deltaZ = data.z - data.lastZ;
            data.deltaXZ = Math.hypot(data.deltaX, data.deltaZ);
            data.deltaYaw = MathUtil.wrapDegrees(data.yaw - data.lastYaw);
            data.deltaPitch = data.pitch - data.lastPitch;
            data.skipMovement = false;
        }

        data.clientOnGround = player.isOnGround();
        data.gliding = player.isGliding();
        data.swimming = player.isSwimming();
        data.riptiding = player.isRiptiding();
        data.inVehicle = player.isInsideVehicle();
        data.flying = player.isFlying();
        data.sneaking = player.isSneaking();
        data.sprinting = player.isSprinting();
        data.eyeHeight = player.getEyeHeight();
        data.climbing = player.isClimbing();

        data.speedAttribute = attribute(player, Attribute.MOVEMENT_SPEED, 0.1);
        data.jumpStrength = attribute(player, Attribute.JUMP_STRENGTH, 0.42);
        data.stepHeight = attribute(player, Attribute.STEP_HEIGHT, 0.6);
        data.gravity = attribute(player, Attribute.GRAVITY, 0.08);

        BoundingBox box = player.getBoundingBox();
        data.chunkLoaded = BlockUtil.isLoaded(world, box);
        if (!data.chunkLoaded) {
            data.skipMovement = true;
            return;
        }
        scan(world, box, data);

        // ----- exemption timestamps
        if (data.inLiquid) data.lastLiquid = now;
        if (data.climbing || data.nearClimbable) data.lastClimb = now;
        if (data.inWeb) data.lastWeb = now;
        if (data.nearPowderSnow) data.lastPowderSnow = now;
        if (data.nearHoney) data.lastHoney = now;
        if (data.nearSlime) data.lastSlime = now;
        if (data.nearBed) data.lastBed = now;
        if (data.nearPiston) data.lastPiston = now;
        if (data.nearScaffolding) data.lastScaffolding = now;
        if (data.headBlocked) data.lastHeadBlocked = now;
        if (data.horizontalCollision) data.lastCollision = now;
        if (data.gliding) data.lastGlide = now;
        if (data.riptiding) data.lastRiptide = now;
        if (data.inVehicle) data.lastVehicle = now;
        GameMode mode = player.getGameMode();
        if (data.flying || mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) data.lastFlight = now;
        if (player.hasPotionEffect(PotionEffectType.LEVITATION)) data.lastLevitation = now;
        if (player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) data.lastSlowFalling = now;
        if (player.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) data.lastDolphin = now;
        if (player.hasActiveItem() && isSpear(player.getActiveItem())) data.lastSpear = now;

        if (data.onGround) {
            data.groundTicks++;
            data.airTicks = 0;
        } else {
            data.airTicks++;
            data.groundTicks = 0;
        }
    }

    private void scan(World world, BoundingBox box, PlayerData data) {
        double minX = box.getMinX();
        double minY = box.getMinY();
        double minZ = box.getMinZ();
        double maxX = box.getMaxX();
        double maxY = box.getMaxY();
        double maxZ = box.getMaxZ();

        data.onGround = BlockUtil.collides(world, minX, minY - 0.03, minZ, maxX, minY, maxZ);
        data.groundLenient = data.onGround
                || BlockUtil.collides(world, minX - 0.05, minY - 0.1, minZ - 0.05, maxX + 0.05, minY, maxZ + 0.05);
        data.headBlocked = BlockUtil.collides(world, minX, maxY, minZ, maxX, maxY + 0.1, maxZ);
        data.horizontalCollision = BlockUtil.collides(world, minX - 0.04, minY + 0.01, minZ - 0.04, maxX + 0.04, maxY - 0.01, maxZ + 0.04);

        boolean inLiquid = false;
        boolean nearLiquid = false;
        boolean liquidBelow = false;
        double liquidDepth = 0.0;
        boolean nearClimbable = false;
        boolean inWeb = false;
        boolean nearPowderSnow = false;
        boolean nearHoney = false;
        boolean nearSlime = false;
        boolean nearBed = false;
        boolean nearPiston = false;
        boolean nearScaffolding = false;

        int x0 = (int) Math.floor(minX - 0.3);
        int x1 = (int) Math.floor(maxX + 0.3);
        int y0 = (int) Math.floor(minY - 0.6);
        int y1 = (int) Math.floor(maxY + 0.2);
        int z0 = (int) Math.floor(minZ - 0.3);
        int z1 = (int) Math.floor(maxZ + 0.3);
        for (int x = x0; x <= x1; x++) {
            boolean xIn = x + 1 > minX && x < maxX;
            for (int z = z0; z <= z1; z++) {
                boolean xzIn = xIn && z + 1 > minZ && z < maxZ;
                for (int y = y0; y <= y1; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type.isAir()) {
                        continue;
                    }
                    boolean inBox = xzIn && y + 1 > minY && y < maxY;
                    double fluid = BlockUtil.fluidHeight(block);
                    if (fluid > 0.0) {
                        double surface = y + fluid;
                        nearLiquid = true;
                        if (xzIn && y < maxY && surface > minY + 0.001) {
                            inLiquid = true;
                            liquidDepth = Math.max(liquidDepth, surface - minY);
                        }
                        if (xzIn && surface <= minY + 0.05 && surface >= minY - 0.55) {
                            liquidBelow = true;
                        }
                    }
                    if (BlockUtil.isClimbable(type)) {
                        nearClimbable = true;
                        if (type == Material.SCAFFOLDING) {
                            nearScaffolding = true;
                        }
                    }
                    switch (type) {
                        case COBWEB, SWEET_BERRY_BUSH -> inWeb |= inBox;
                        case POWDER_SNOW -> nearPowderSnow = true;
                        case HONEY_BLOCK -> nearHoney = true;
                        case SLIME_BLOCK -> nearSlime = true;
                        case MOVING_PISTON, PISTON_HEAD -> nearPiston = true;
                        default -> {
                            if (Tag.BEDS.isTagged(type)) {
                                nearBed = true;
                            }
                        }
                    }
                }
            }
        }

        // friction of the block under the feet (max of the four corners - lenient)
        int frictionY = (int) Math.floor(minY - 0.5000001);
        double friction = -1.0;
        double[][] corners = {{minX, minZ}, {minX, maxZ}, {maxX, minZ}, {maxX, maxZ}};
        for (double[] corner : corners) {
            Material type = world.getBlockAt((int) Math.floor(corner[0]), frictionY, (int) Math.floor(corner[1])).getType();
            if (!type.isAir()) {
                friction = Math.max(friction, BlockUtil.friction(type));
            }
        }
        data.friction = friction < 0.0 ? 0.6 : friction;

        data.inLiquid = inLiquid;
        data.nearLiquid = nearLiquid;
        data.liquidBelow = liquidBelow && !inLiquid;
        data.liquidDepth = liquidDepth;
        data.nearClimbable = nearClimbable;
        data.inWeb = inWeb;
        data.nearPowderSnow = nearPowderSnow;
        data.nearHoney = nearHoney;
        data.nearSlime = nearSlime;
        data.nearBed = nearBed;
        data.nearPiston = nearPiston;
        data.nearScaffolding = nearScaffolding;
    }

    private void finish(Player player, PlayerData data) {
        int clientTicks = data.clientTicks;
        data.velocities.removeIf(record -> clientTicks - record.clientTick > record.window + 2);
        data.rollClickWindow();
        data.attacksThisTick = 0;
        data.swingsThisTick = 0;
        data.clicksThisTick = 0;
        data.targetsThisTick.clear();
        data.placedThisTick = 0;
        data.brokenThisTick = 0;
        data.inventoryClicksThisTick = 0;
        data.slotChangesThisTick = 0;
        data.attackedBeforeSlotChange = false;

        Location location = player.getLocation();
        data.tickEndX = location.getX();
        data.tickEndY = location.getY();
        data.tickEndZ = location.getZ();
        data.tickEndYaw = location.getYaw();
        data.tickEndPitch = location.getPitch();
        data.hasTickSnapshot = true;

        if (!data.skipMovement && !data.movementFlagged && data.onGround && data.clientOnGround
                && !data.inLiquid && !data.inVehicle && !data.gliding && data.chunkLoaded) {
            data.lastSafe = location;
        }
        data.movementFlagged = false;
    }

    private static double attribute(Player player, Attribute attribute, double def) {
        AttributeInstance instance = player.getAttribute(attribute);
        return instance == null ? def : instance.getValue();
    }

    public static boolean isSpear(ItemStack item) {
        return item != null && item.getType().name().endsWith("_SPEAR");
    }
}
