package de.oliver.fancynpcs.integrations.bettermodel;

import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.events.NpcRemovedEvent;
import de.oliver.fancynpcs.integrations.IntegrationsManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class NpcRemoveListener implements Listener {

    @EventHandler
    public void onNpcRemoved(NpcRemovedEvent event) {
        Npc npc = event.getNpc();
        if (!CustomModelAttribute.hasAttribute(npc)) {
            return;
        }

        if (event.isAsynchronous()) {
            Bukkit.getGlobalRegionScheduler().execute(IntegrationsManager.get().getPlugin(), () -> CustomModelAttribute.closeAllTrackers(npc));
            return;
        }

        CustomModelAttribute.closeAllTrackers(npc);
    }

}
