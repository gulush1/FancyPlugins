package de.oliver.fancynpcs.integrations.hologram;

import de.oliver.fancynpcs.integrations.IntegrationsManager;
import eu.decentsoftware.holograms.event.DecentHologramsReloadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class DhReloadListener implements Listener {

    @EventHandler
    public void onDhReload(DecentHologramsReloadEvent event) {
        IntegrationsManager.get().getHologramService().reload();
    }

}
