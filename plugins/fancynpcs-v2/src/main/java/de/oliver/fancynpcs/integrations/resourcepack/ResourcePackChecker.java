package de.oliver.fancynpcs.integrations.resourcepack;

import de.oliver.fancynpcs.integrations.IntegrationsManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Static entry point to the native resource pack detection for code without a ResourcePackService reference.
 * Without model support (no BetterModel) the fallback is inactive and every player counts as having the pack.
 */
public final class ResourcePackChecker {

    private ResourcePackChecker() {}

    /**
     * Checks if the fallback is active (BetterModel detection enabled and configured).
     */
    public static boolean isFallbackActive() {
        ResourcePackService service = service();
        return service != null && service.isFallbackActive();
    }

    /**
     * Last known decision. Cheap and safe from any thread, never queries PlaceholderAPI.
     */
    public static boolean hasResourcePack(Player player) {
        ResourcePackService service = service();
        return service == null || service.hasResourcePack(player);
    }

    /**
     * Full decision including PlaceholderAPI. Must be called from the player's scheduler.
     */
    public static boolean resolve(Player player) {
        ResourcePackService service = service();
        return service == null || service.resolve(player);
    }

    private static @Nullable ResourcePackService service() {
        IntegrationsManager plugin = IntegrationsManager.get();
        return plugin == null ? null : plugin.getResourcePackService();
    }
}
