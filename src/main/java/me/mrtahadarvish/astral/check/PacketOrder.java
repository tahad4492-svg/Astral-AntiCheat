package me.mrtahadarvish.astral.check;

import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.ClientUtil;
import org.bukkit.Location;

import static me.mrtahadarvish.astral.data.PlayerData.since;

/**
 * Packet order inside one client tick. A vanilla client always sends its packets in this order:
 * <pre>
 *   slot change -> attacks / item use / placing / digging -> swing -> movement -> tick end
 * </pre>
 * Inventory clicks happen between two ticks (on the next tick, before the movement). So any
 * action that arrives <b>after</b> the movement packet of the same tick was sent by a modified
 * client: silent rotations (rotate in the movement packet, then attack / place), silent swaps
 * and packet macros all do this.
 */
public final class PacketOrder {

    /** 1.21.2: first protocol where the client sends a tick end packet itself. */
    public static final int NATIVE_TICK_END = 768;

    private PacketOrder() {
    }

    /**
     * True when the client sends real tick end packets (1.21.2+). Older clients joining through
     * ViaVersion have no tick end of their own, so the order inside a tick is not known for them.
     */
    public static boolean reliable(PlayerData data) {
        if (data.protocol < 0) {
            data.protocol = ClientUtil.protocol(data.player());
        }
        return data.protocol >= NATIVE_TICK_END;
    }

    /**
     * True while the packet order can't be trusted: client older than 1.21.2, no tick seen yet,
     * vehicles, or a server side position change (teleport, rejected move, piston, respawn, world
     * change) that makes the client answer with an extra movement packet.
     */
    public static boolean exempt(PlayerData data) {
        return !data.hasTickSnapshot || data.inVehicle || data.skipMovement || !reliable(data)
                || since(data.lastTeleport) <= 40 || since(data.lastFailMove) <= 40 || since(data.lastPiston) <= 20
                || since(data.lastVehicle) <= data.vehicleExitTicks() || since(data.lastRespawn) <= 40 || since(data.lastWorldChange) <= 40;
    }

    /**
     * True when the movement packet of the current client tick was already processed, i.e. the
     * player's position or rotation changed since the last tick end.
     */
    public static boolean isPost(PlayerData data) {
        if (exempt(data)) {
            return false;
        }
        Location location = data.player().getLocation();
        return location.getX() != data.tickEndX || location.getY() != data.tickEndY || location.getZ() != data.tickEndZ
                || location.getYaw() != data.tickEndYaw || location.getPitch() != data.tickEndPitch;
    }
}
