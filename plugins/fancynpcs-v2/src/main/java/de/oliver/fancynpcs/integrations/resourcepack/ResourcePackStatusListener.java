package de.oliver.fancynpcs.integrations.resourcepack;

import de.oliver.fancynpcs.integrations.resourcepack.ResourcePackService;
import de.oliver.fancynpcs.integrations.resourcepack.state.PackSessionRegistry;
import de.oliver.fancynpcs.integrations.resourcepack.state.PackStatus;
import de.oliver.fancynpcs.api.events.NpcSpawnEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Bukkit side of the native resource pack detection: forwards pack answers, joins, quits and NPC spawns to
 * the ResourcePackService. Pack answers are recorded even while the fallback is disabled, so enabling it
 * later starts from the real state.
 */
public class ResourcePackStatusListener implements Listener {

    private static final boolean PACK_IDS_SUPPORTED = detectPackIdSupport();

    private final ResourcePackService service;

    public ResourcePackStatusListener(@NotNull ResourcePackService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        PackStatus status = PackStatus.fromName(event.getStatus().name());
        if (status == null) {
            return;
        }

        service.handleStatusEvent(event.getPlayer(), packId(event), status);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        service.handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcSpawn(NpcSpawnEvent event) {
        service.handleNpcSpawn(event.getNpc(), event.getPlayer());
    }

    /**
     * Servers before 1.20.3 have no per-pack ids (no getID()), their single pack is tracked under
     * LEGACY_PACK_ID.
     */
    private static UUID packId(PlayerResourcePackStatusEvent event) {
        if (!PACK_IDS_SUPPORTED) {
            return PackSessionRegistry.LEGACY_PACK_ID;
        }
        UUID id = event.getID();
        return id != null ? id : PackSessionRegistry.LEGACY_PACK_ID;
    }

    private static boolean detectPackIdSupport() {
        try {
            PlayerResourcePackStatusEvent.class.getMethod("getID");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
