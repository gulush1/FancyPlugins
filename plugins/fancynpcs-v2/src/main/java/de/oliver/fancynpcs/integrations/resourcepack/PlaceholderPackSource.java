package de.oliver.fancynpcs.integrations.resourcepack;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Optional PlaceholderAPI-based source ("does this player have the pack?") with a per-player cache.
 * An unresolved placeholder (PlaceholderAPI returns the raw placeholder or an empty string) means
 * "no answer", never "no pack". Must be queried from the player's own scheduler thread.
 */
public final class PlaceholderPackSource {

    private static final String PLACEHOLDER_API = "PlaceholderAPI";
    private static final long MILLIS_PER_TICK = 50L;

    private final Supplier<ResourcePackSettings> settings;
    private final Map<UUID, CachedAnswer> answers = new ConcurrentHashMap<>();

    private record CachedAnswer(@Nullable Boolean value, long atMillis) {
    }

    public PlaceholderPackSource(@NotNull Supplier<ResourcePackSettings> settings) {
        this.settings = settings;
    }

    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled(PLACEHOLDER_API);
    }

    public @Nullable Boolean query(@NotNull Player player, boolean forceFresh) {
        if (!isAvailable()) {
            return null;
        }

        ResourcePackSettings current = settings.get();
        if (current.placeholder().isBlank()) {
            return null;
        }

        CachedAnswer cached = answers.get(player.getUniqueId());
        if (!forceFresh && isFresh(cached, current)) {
            return cached.value();
        }

        Boolean value = evaluate(player, current);
        answers.put(player.getUniqueId(), new CachedAnswer(value, System.currentTimeMillis()));
        return value;
    }

    private boolean isFresh(@Nullable CachedAnswer cached, @NotNull ResourcePackSettings current) {
        if (cached == null) {
            return false;
        }

        long refreshMillis = current.placeholderRefreshTicks() * MILLIS_PER_TICK;
        if (refreshMillis <= 0) {
            return true;
        }

        return System.currentTimeMillis() - cached.atMillis() < refreshMillis;
    }

    private @Nullable Boolean evaluate(@NotNull Player player, @NotNull ResourcePackSettings current) {
        String raw = current.placeholder().trim();
        String resolved = PlaceholderAPI.setPlaceholders(player, raw);
        if (resolved == null) {
            return null;
        }

        String trimmed = resolved.trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase(raw)) {
            return null;
        }

        return trimmed.equalsIgnoreCase(current.expectedValue().trim());
    }

    public void forget(@NotNull UUID playerId) {
        answers.remove(playerId);
    }

    public void clear() {
        answers.clear();
    }

}
