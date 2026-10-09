package me.mrtahadarvish.astral.manager;

import me.mrtahadarvish.astral.AstralAntiCheat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** Writes every flag and punishment to plugins/MrTahaDarvishAntiCheat/logs/&lt;date&gt;.log (async). */
public final class LogManager {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AstralAntiCheat plugin;
    private final File folder;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Astral-Logger");
        thread.setDaemon(true);
        return thread;
    });

    public LogManager(AstralAntiCheat plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "logs");
    }

    public void log(String line) {
        if (!plugin.settings().logFile) {
            return;
        }
        String entry = "[" + LocalTime.now().format(TIME) + "] " + line + System.lineSeparator();
        LocalDate date = LocalDate.now();
        executor.execute(() -> write(date, entry));
    }

    private void write(LocalDate date, String entry) {
        try {
            if (!folder.exists() && !folder.mkdirs()) {
                return;
            }
            File file = new File(folder, date + ".log");
            try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(entry);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not write the violation log", e);
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
