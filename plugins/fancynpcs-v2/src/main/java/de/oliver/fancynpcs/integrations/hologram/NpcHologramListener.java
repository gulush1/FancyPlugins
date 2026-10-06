package de.oliver.fancynpcs.integrations.hologram;

import de.oliver.fancynpcs.integrations.IntegrationsManager;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.events.NpcModifyEvent;
import de.oliver.fancynpcs.api.events.NpcRemovedEvent;
import de.oliver.fancynpcs.api.events.NpcsLoadedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;


public class NpcHologramListener implements Listener {

    private static final long APPLY_DELAY_TICKS = 1L;
    private static final long SCALE_APPLY_DELAY_TICKS = 2L;

    @EventHandler
    public void onNpcRemove(NpcRemovedEvent event) {
        Npc npc = event.getNpc();
        if (event.isAsynchronous()) {
            Bukkit.getGlobalRegionScheduler().execute(IntegrationsManager.get().getPlugin(), () -> removeHologram(npc));
            return;
        }

        removeHologram(npc);
    }

    private void removeHologram(Npc npc) {
        IntegrationsManager.get().getHologramService().remove(npc);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNpcModify(NpcModifyEvent event) {
        Npc npc = event.getNpc();
        switch (event.getModification()) {
            // The event is fired before the new value is stored on the NPC, so anything that has to
            // read the NPC's own data waits a tick instead of acting on stale values.
            case VISIBILITY_DISTANCE -> schedule(NpcHologramService::refresh, npc, APPLY_DELAY_TICKS);
            case SCALE -> schedule(NpcHologramService::reposition, npc, SCALE_APPLY_DELAY_TICKS);
            case LOCATION -> handleMove(npc, event.getNewValue());
            default -> {
            }
        }
    }

    @EventHandler
    public void onNpcsLoaded(NpcsLoadedEvent event) {
        NpcHologramService service = IntegrationsManager.get().getHologramService();
        service.loadAll();
    }

    private void handleMove(Npc npc, Object newValue) {
        if (!(newValue instanceof Location location)) {
            return;
        }

        IntegrationsManager.get().getHologramService().handleNpcMoved(npc, location);
    }

    private void schedule(HologramAction action, Npc npc, long delayTicks) {
        IntegrationsManager plugin = IntegrationsManager.get();
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin.getPlugin(),
                task -> action.accept(plugin.getHologramService(), npc), delayTicks);
    }

    private interface HologramAction {
        void accept(NpcHologramService service, Npc npc);
    }

}
