package de.oliver.fancynpcs.integrations.hologram;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.events.NpcCreateEvent;
import de.oliver.fancynpcs.integrations.IntegrationsManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * FancyNpcs copies only its own NPC data, so the hologram of the source NPC has to be copied separately.
 * The create event fires before the copy is registered, so the hologram is copied one tick later.
 */
public class NpcCopyListener implements Listener {

    private static final long COPY_DELAY_TICKS = 1L;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcCreate(NpcCreateEvent event) {
        Npc source = event.getSource();
        if (source == null) {
            return;
        }

        String sourceName = source.getData().getName();
        String copyId = event.getNpc().getData().getId();
        Bukkit.getGlobalRegionScheduler().runDelayed(IntegrationsManager.get().getPlugin(),
                task -> copyHologram(sourceName, copyId), COPY_DELAY_TICKS);
    }

    private void copyHologram(String sourceName, String copyId) {
        Npc copy = FancyNpcsPlugin.get().getNpcManager().getNpcById(copyId);
        if (copy == null) {
            return;
        }

        NpcHologramService service = IntegrationsManager.get().getHologramService();
        if (service == null || !service.isAvailable()) {
            return;
        }
        if (!service.copyHologram(sourceName, copy)) {
            return;
        }

        IntegrationsManager.get().getLogger().info(
                "Copied hologram from NPC %s to NPC %s".formatted(sourceName, copy.getData().getName())
        );
    }
}
