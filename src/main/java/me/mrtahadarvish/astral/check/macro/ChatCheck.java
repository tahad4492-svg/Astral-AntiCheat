package me.mrtahadarvish.astral.check.macro;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.mrtahadarvish.astral.AstralAntiCheat;
import me.mrtahadarvish.astral.check.Check;
import me.mrtahadarvish.astral.check.CheckCategory;
import me.mrtahadarvish.astral.data.PlayerData;
import me.mrtahadarvish.astral.util.Clock;
import me.mrtahadarvish.astral.util.Text;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat spam, duplicate messages, chat macros (messages sent with a robotic interval) and
 * command spam. Chat is asynchronous, so this check keeps its own thread safe state.
 */
public final class ChatCheck extends Check {

    private int maxMessages;
    private long windowMs;
    private long duplicateMs;
    private int maxCommands;

    private final Map<UUID, ChatState> states = new ConcurrentHashMap<>();

    public ChatCheck(AstralAntiCheat plugin) {
        super(plugin, "chat", "ChatSpam", CheckCategory.MACRO, "Chat spam, duplicate messages, chat macros and command spam.");
    }

    @Override
    protected void onLoad() {
        maxMessages = option("max-messages", 5);
        windowMs = (long) (option("window-seconds", 6.0) * 1000L);
        duplicateMs = (long) (option("duplicate-seconds", 20.0) * 1000L);
        maxCommands = option("max-commands-per-second", 5);
    }

    private static final class ChatState {
        final Deque<Long> messages = new ArrayDeque<>();
        final Deque<Long> intervals = new ArrayDeque<>();
        final Deque<Long> commands = new ArrayDeque<>();
        String lastMessage = "";
        long lastTime;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        PlayerData data = data(event.getPlayer());
        if (!shouldCheck(data)) {
            return;
        }
        String message = Text.plain(event.message()).trim().toLowerCase(Locale.ROOT);
        long now = Clock.millis();
        ChatState state = states.computeIfAbsent(event.getPlayer().getUniqueId(), uuid -> new ChatState());
        String type = null;
        String debug = "";
        synchronized (state) {
            state.messages.addLast(now);
            while (!state.messages.isEmpty() && now - state.messages.peekFirst() > windowMs) {
                state.messages.removeFirst();
            }
            if (state.lastTime > 0L && now - state.lastTime < 60_000L) {
                state.intervals.addLast(now - state.lastTime);
                while (state.intervals.size() > 5) {
                    state.intervals.removeFirst();
                }
            }
            if (state.messages.size() > maxMessages) {
                type = "Spam";
                debug = state.messages.size() + " messages in " + (windowMs / 1000) + "s";
            } else if (duplicateMs > 0L && message.length() > 3 && message.equals(state.lastMessage) && now - state.lastTime < duplicateMs) {
                type = "Duplicate";
                debug = "same message twice";
            } else if (state.intervals.size() == 5) {
                long min = Long.MAX_VALUE;
                long max = Long.MIN_VALUE;
                for (long interval : state.intervals) {
                    min = Math.min(min, interval);
                    max = Math.max(max, interval);
                }
                if (max - min < 120L && max < 30_000L) {
                    type = "Macro";
                    debug = "5 messages exactly ~" + (max / 1000.0) + "s apart";
                    state.intervals.clear();
                }
            }
            state.lastMessage = message;
            state.lastTime = now;
        }
        if (type != null && flag(data, type, debug)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        PlayerData data = data(event.getPlayer());
        if (!shouldCheck(data)) {
            return;
        }
        long now = Clock.millis();
        ChatState state = states.computeIfAbsent(event.getPlayer().getUniqueId(), uuid -> new ChatState());
        int count;
        synchronized (state) {
            state.commands.addLast(now);
            while (!state.commands.isEmpty() && now - state.commands.peekFirst() > 1000L) {
                state.commands.removeFirst();
            }
            count = state.commands.size();
        }
        if (count > maxCommands && flag(data, "Commands", count + " commands in 1s")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }
}
