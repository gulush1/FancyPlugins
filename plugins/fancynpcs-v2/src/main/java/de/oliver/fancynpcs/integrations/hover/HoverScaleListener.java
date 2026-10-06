package de.oliver.fancynpcs.integrations.hover;

import de.oliver.fancynpcs.api.events.NpcRemovedEvent;
import de.oliver.fancynpcs.api.events.NpcSpawnEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Keeps {@link HoverScaleTracker} state in sync with players leaving and NPCs being removed or respawned.
 */
public class HoverScaleListener implements Listener {

    private final HoverScaleTracker tracker;

    public HoverScaleListener(HoverScaleTracker tracker) {
        this.tracker = tracker;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        tracker.forgetPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onNpcRemoved(NpcRemovedEvent event) {
        tracker.forgetNpc(event.getNpc().getData().getId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcSpawn(NpcSpawnEvent event) {
        tracker.forgetPair(event.getPlayer().getUniqueId(), event.getNpc().getData().getId());
    }
}
