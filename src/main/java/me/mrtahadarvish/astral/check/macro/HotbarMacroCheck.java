package me.mrtahadarvish.astral.check.macro;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.check.PacketOrder;
import me.mrtahadarvish.astral.data.Buffer;
import me.mrtahadarvish.astral.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Silent swap / swap macros (auto attribute swap, auto stun slam, mace swap, auto weapon,
 * shield breaker).
 * <p>
 * A vanilla client reads the number keys before attacks and item use, and mouse-wheel scrolling
 * happens between ticks. So within one client tick it always sends: slot change -> attacks.
 * <ul>
 *   <li>SilentSwap - a slot change after an attack / item use / block click in the same tick
 *       (switch, hit, switch back). Impossible for vanilla: the swap is cancelled and the rest of
 *       the hits of that tick do not register.</li>
 *   <li>Post - a slot change after the movement packet of the tick.</li>
 *   <li>Spam - more than two slot changes in one tick.</li>
 *   <li>AttributeSwap - "swap to the other weapon in the same tick as the hit, swap back a fixed
 *       time later", every single time. Humans land the swap in the same tick as the click about
 *       half of the time and swap back with a varying delay.</li>
 *   <li>StunSlam - axe hit that disables a shield and a swap to the mace for the smash in the same
 *       tick (impossible, blocked right away), or the mace hit exactly one tick after the axe hit
 *       for many stun slams in a row (alert only by default: very fast players get one tick now
 *       and then).</li>
 * </ul>
 * Once a player is caught, his swap hits / stun slam hits do not register for a while.
 */
public final class HotbarMacroCheck extends Check {

    private static final int NEVER = -100_000;

    private int samplesNeeded;
    private int maxSwapBackDelay;
    private int swapBackSpread;
    private double minSameTickRatio;
    private int stunSlamCount;
    private boolean blockStunSlamStreaks;
    private int blockTicks;

    public HotbarMacroCheck(AstralAntiCheat plugin) {
        super(plugin, "hotbarmacro", "SilentSwap", CheckCategory.MACRO,
                "Silent hotbar swapping (auto attribute swap, auto stun slam, mace swap, auto weapon).");
    }

    @Override
    protected void onLoad() {
        samplesNeeded = Math.max(3, option("attribute-swap.samples", 6));
        maxSwapBackDelay = Math.max(1, option("attribute-swap.max-swap-back-ticks", 10));
        swapBackSpread = Math.max(0, option("attribute-swap.max-spread-ticks", 0));
        minSameTickRatio = option("attribute-swap.min-same-tick-ratio", 0.9);
        stunSlamCount = Math.max(3, option("stun-slam.in-a-row", 8));
        blockStunSlamStreaks = option("stun-slam.block-streaks", false);
        blockTicks = Math.max(20, option("block-ticks", 600));
    }

    private static final class State {
        /** Client tick of a slot change that came after an action (rest of the tick is blocked). */
        int silentSwapTick = NEVER;
        int lastChangeTick = NEVER;
        int lastFromSlot = -1;
        boolean changeBeforeAction;

        /** A swap hit waiting for the swap back. */
        boolean pending;
        int pendingTick;
        int pendingFromSlot;
        final Deque<Integer> swapBackDelays = new ArrayDeque<>();
        int switchAttacks;
        int sameTickAttacks;
        int windows;
        int attributeSwapperUntil = NEVER;

        int axeTick = NEVER;
        int axeTarget = -1;
        int fastStunSlams;
        int stunSlammerUntil = NEVER;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        PlayerData data = data(event.getPlayer());
        if (data == null) {
            return;
        }
        data.slotChangesThisTick++;
        if (!shouldCheck(data)) {
            return;
        }
        State state = data.state(State.class, State::new);
        boolean reliable = PacketOrder.reliable(data);
        String slots = event.getPreviousSlot() + "->" + event.getNewSlot();
        boolean cancel = false;

        if (data.actionsThisTick > 0 && reliable) {
            // switch, hit, switch back in one tick: vanilla can not do this
            state.silentSwapTick = data.clientTicks;
            ItemStack next = event.getPlayer().getInventory().getItem(event.getNewSlot());
            boolean stunSlam = state.axeTick == data.clientTicks && next != null && next.getType() == Material.MACE;
            Buffer buffer = data.buffer("hotbarmacro.swap");
            if (buffer.increase() > 1.0) {
                buffer.set(0.5);
                boolean mitigated = stunSlam
                        ? flag(data, "StunSlam", "shield broken with the axe, swapped " + slots + " to the mace in the same tick")
                        : flag(data, "SilentSwap", "slot " + slots + " after " + data.actionsThisTick + " action(s) in the same tick");
                if (mitigated) {
                    plugin.checks().lockCombat(data);
                    if (stunSlam) {
                        state.stunSlammerUntil = PlayerData.now() + blockTicks;
                    }
                }
            }
            cancel = mitigate();
        } else if (PacketOrder.isPost(data)) {
            Buffer buffer = data.buffer("hotbarmacro.post");
            if (buffer.increase() > 1.0) {
                buffer.set(0.5);
                cancel = flag(data, "Post", "slot " + slots + " sent after the movement packet");
            }
        }
        if (data.slotChangesThisTick > 2) {
            Buffer buffer = data.buffer("hotbarmacro.spam");
            if (buffer.increase() > 1.0) {
                buffer.set(0.5);
                cancel |= flag(data, "Spam", "slot changes=" + data.slotChangesThisTick + " in one tick");
            }
        }

        // swap back after a swap hit (attribute swap macros)
        if (state.pending && event.getNewSlot() == state.pendingFromSlot) {
            state.pending = false;
            int delay = data.clientTicks - state.pendingTick;
            if (delay > 0 && delay <= maxSwapBackDelay) {
                addSwapBack(data, state, delay);
            }
        }

        state.lastChangeTick = data.clientTicks;
        state.lastFromSlot = event.getPreviousSlot();
        state.changeBeforeAction = data.actionsThisTick == 0;
        if (cancel) {
            event.setCancelled(true);
        }
    }

    // not ignoreCancelled: an axe hit that was already blocked still tells what the macro does next
    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        Player player = event.getPlayer();
        PlayerData data = data(player);
        if (!shouldCheck(data)) {
            return;
        }
        State state = data.state(State.class, State::new);
        int tick = data.clientTicks;
        if (event.isCancelled()) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().name().endsWith("_AXE") && event.getAttacked() instanceof Player victim && victim.isBlocking()) {
                state.axeTick = tick;
                state.axeTarget = victim.getEntityId();
            }
            return;
        }

        // hits after an impossible swap in the same tick never count
        if (mitigate() && state.silentSwapTick == tick) {
            event.setCancelled(true);
            return;
        }

        // ---- attribute swap
        boolean swappedThisTick = state.lastChangeTick == tick && state.changeBeforeAction;
        if (tick - state.lastChangeTick <= 3 && data.attacksThisTick <= 1) {
            state.switchAttacks++;
            if (swappedThisTick) {
                state.sameTickAttacks++;
            }
        }
        if (swappedThisTick && data.attacksThisTick <= 1) {
            state.pending = true;
            state.pendingTick = tick;
            state.pendingFromSlot = state.lastFromSlot;
            if (mitigate() && PlayerData.now() < state.attributeSwapperUntil) {
                event.setCancelled(true);
                return;
            }
        }

        // ---- stun slam: axe breaks the shield, the mace smashes one tick later
        ItemStack hand = player.getInventory().getItemInMainHand();
        Entity target = event.getAttacked();
        Material type = hand.getType();
        if (type.name().endsWith("_AXE") && target instanceof Player victim && victim.isBlocking()) {
            state.axeTick = tick;
            state.axeTarget = target.getEntityId();
        } else if (type == Material.MACE && target.getEntityId() == state.axeTarget && tick - state.axeTick <= 6) {
            int delay = tick - state.axeTick;
            state.axeTarget = -1;
            // humans need 2+ ticks now and then, a macro is always exactly one tick later
            state.fastStunSlams = delay <= 1 ? state.fastStunSlams + 1 : 0;
            if (state.fastStunSlams >= stunSlamCount) {
                state.fastStunSlams = 0;
                // very fast players manage one tick now and then, so this only alerts by default
                if (flag(data, "StunSlam", stunSlamCount + " stun slams in a row with the mace hit exactly "
                        + delay + " tick after the axe hit") && blockStunSlamStreaks) {
                    state.stunSlammerUntil = PlayerData.now() + blockTicks;
                    plugin.checks().lockCombat(data);
                }
            }
            if (mitigate() && PlayerData.now() < state.stunSlammerUntil) {
                event.setCancelled(true);
            }
        }
    }

    private void addSwapBack(PlayerData data, State state, int delay) {
        state.swapBackDelays.addLast(delay);
        if (state.swapBackDelays.size() < samplesNeeded) {
            return;
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int value : state.swapBackDelays) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        double ratio = state.switchAttacks == 0 ? 0.0 : (double) state.sameTickAttacks / state.switchAttacks;
        state.swapBackDelays.clear();
        state.switchAttacks = 0;
        state.sameTickAttacks = 0;
        if (max - min <= swapBackSpread && ratio >= minSameTickRatio) {
            if (++state.windows >= 2) {
                state.windows = 1;
                if (flag(data, "AttributeSwap", "swap in the same tick as the hit (" + Math.round(ratio * 100)
                        + "%), swap back after " + min + (max != min ? "-" + max : "") + " ticks every time")) {
                    state.attributeSwapperUntil = PlayerData.now() + blockTicks;
                    plugin.checks().lockCombat(data);
                }
            }
        } else {
            state.windows = 0;
        }
    }
}
