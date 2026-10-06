package de.oliver.fancynpcs.integrations.bettermodel;

import de.oliver.fancynpcs.integrations.IntegrationsManager;
import de.oliver.fancynpcs.integrations.resourcepack.ResourcePackChecker;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.events.NpcSpawnEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * FancyNpcs spawns each NPC separately for every viewer, but the underlying BetterModel
 * {@link kr.toxicity.model.api.tracker.EntityTracker} is created only once, for the first viewer.
 * Every later spawn is a no-op from BetterModel's point of view, so players who start seeing the
 * NPC after the tracker already exists (joining later, walking into range after a restart, etc.)
 * never receive the model spawn packets and only see the plain NPC. This listener performs the
 * missing catch-up spawn for those late viewers.
 */
public class NpcModelSpawnListener implements Listener {

    private static final long SPAWN_DELAY_TICKS = 1L;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcSpawn(NpcSpawnEvent event) {
        Npc npc = event.getNpc();
        if (!CustomModelAttribute.hasAttribute(npc)) {
            return;
        }
        if (ResourcePackChecker.isFallbackActive()) {
            return;
        }

        Player player = event.getPlayer();
        player.getScheduler().runDelayed(IntegrationsManager.get().getPlugin(),
                task -> CustomModelAttribute.ensureSpawnedFor(npc, player), null, SPAWN_DELAY_TICKS);
    }

}
