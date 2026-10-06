package de.oliver.fancynpcs.integrations.resourcepack;

import de.oliver.fancyanalytics.logger.ExtendedFancyLogger;
import de.oliver.fancyanalytics.logger.properties.ThrowableProperty;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Persistent memory of honest "loaded" answers: once a player has verified a pack (by re-sending it and
 * seeing the client answer honestly), later proxy claims for the same player and pack are trusted right
 * away instead of being re-verified every time.
 */
public final class PackVerdictMemory {

    private static final String VERIFIED_KEY = "verified";
    private static final String FIELD_SEPARATOR = ";";
    private static final String KEY_SEPARATOR = "|";

    private final Plugin plugin;
    private final File file;
    private final ExtendedFancyLogger logger;
    private final ConcurrentHashMap<String, Long> verified = new ConcurrentHashMap<>();
    private final AtomicBoolean saveScheduled = new AtomicBoolean();

    public PackVerdictMemory(@NotNull Plugin plugin, @NotNull File file, @NotNull ExtendedFancyLogger logger) {
        this.plugin = plugin;
        this.file = file;
        this.logger = logger;
    }

    public void load(long maxAgeMillis) {
        verified.clear();
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        long cutoff = System.currentTimeMillis() - maxAgeMillis;
        for (String line : config.getStringList(VERIFIED_KEY)) {
            loadLine(line, cutoff);
        }
    }

    public boolean isVerified(@NotNull UUID playerId, @NotNull String packKey) {
        return verified.containsKey(key(playerId, packKey));
    }

    public void remember(@NotNull UUID playerId, @NotNull String packKey) {
        if (verified.put(key(playerId, packKey), System.currentTimeMillis()) == null) {
            scheduleSave();
        }
    }

    public void forget(@NotNull UUID playerId, @NotNull String packKey) {
        if (verified.remove(key(playerId, packKey)) != null) {
            scheduleSave();
        }
    }

    public synchronized void saveNow() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Long> entry : verified.entrySet()) {
            lines.add(toLine(entry));
        }
        try {
            ensureParentDirectory();
            YamlConfiguration config = new YamlConfiguration();
            config.set(VERIFIED_KEY, lines);
            config.save(file);
        } catch (IOException e) {
            logger.warn("Could not save verified resource packs", ThrowableProperty.of(e));
        }
    }

    private void loadLine(@NotNull String line, long cutoff) {
        String[] parts = line.split(FIELD_SEPARATOR, 3);
        if (parts.length != 3 || !isValidUuid(parts[0])) {
            return;
        }
        Long verifiedAt = parseEpochMillis(parts[2]);
        if (verifiedAt == null || verifiedAt < cutoff) {
            return;
        }
        verified.put(parts[0] + KEY_SEPARATOR + parts[1], verifiedAt);
    }

    private void ensureParentDirectory() throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create directory " + parent);
        }
    }

    private void scheduleSave() {
        if (saveScheduled.compareAndSet(false, true)) {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                saveScheduled.set(false);
                saveNow();
            });
        }
    }

    private static @NotNull String toLine(@NotNull Map.Entry<String, Long> entry) {
        String compositeKey = entry.getKey();
        int separator = compositeKey.indexOf(KEY_SEPARATOR);
        String playerId = separator >= 0 ? compositeKey.substring(0, separator) : compositeKey;
        String packKey = separator >= 0 ? compositeKey.substring(separator + 1) : "";
        return playerId + FIELD_SEPARATOR + packKey + FIELD_SEPARATOR + entry.getValue();
    }

    private static @NotNull String key(@NotNull UUID playerId, @NotNull String packKey) {
        return playerId + KEY_SEPARATOR + packKey;
    }

    private static @Nullable Long parseEpochMillis(@NotNull String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isValidUuid(@NotNull String raw) {
        try {
            UUID.fromString(raw);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
