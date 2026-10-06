package de.oliver.fancynpcs.integrations.hologram;

import de.oliver.fancynpcs.integrations.hologram.dh.DhHologramCodec;
import de.oliver.fancynpcs.integrations.hologram.dh.DhLocations;
import de.oliver.fancynpcs.integrations.hologram.legacy.LegacyHologramMigrator;
import de.oliver.fancynpcs.integrations.IntegrationsManager;
import de.oliver.fancyanalytics.logger.properties.ThrowableProperty;
import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import eu.decentsoftware.holograms.api.DecentHologramsAPI;
import eu.decentsoftware.holograms.display.DisplayBase;
import eu.decentsoftware.holograms.display.DisplayService;
import eu.decentsoftware.holograms.display.TextDisplay;
import eu.decentsoftware.holograms.display.config.dto.ConfigDisplaySettings;
import eu.decentsoftware.holograms.platform.api.data.DecentLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.List;

/**
 * Keeps the DecentHolograms displays of NPCs in sync with the hologram files in the plugin folder.
 * The YAML files own lines, attributes and settings; this service only owns the NPC binding,
 * so hand made edits survive everything but an explicit command.
 */
public class NpcHologramService {

    private static final String DECENT_HOLOGRAMS_PLUGIN_NAME = "DecentHolograms";

    private final DhHologramCodec codec;
    private final NpcHologramStorage storage;
    private final NpcHologramFactory factory;
    private final LegacyHologramMigrator migrator;

    public NpcHologramService(DhHologramCodec codec, NpcHologramStorage storage, NpcHologramFactory factory) {
        this.codec = codec;
        this.storage = storage;
        this.factory = factory;
        this.migrator = new LegacyHologramMigrator(storage, factory);
    }

    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled(DECENT_HOLOGRAMS_PLUGIN_NAME) && isApiRunning();
    }

    public NpcHologramEntry getEntry(Npc npc) {
        return storage.find(npc.getData().getName());
    }

    public NpcHologramFactory getFactory() {
        return factory;
    }

    public void createOrUpdate(Npc npc, NpcHologramEntry entry) {
        storage.save(entry);
        if (isAvailable()) {
            applyEntry(npc, entry, npc.getData().getLocation());
        }
    }

    /**
     * Copies the hologram of another NPC onto the given target NPC, keeping lines, offset and attributes.
     *
     * @return whether a hologram was copied
     */
    public boolean copyHologram(String sourceNpcName, Npc target) {
        NpcHologramEntry source = storage.find(sourceNpcName);
        if (source == null) {
            return false;
        }

        NpcHologramEntry copy = factory.copyFor(source, target.getData().getName());
        if (copy == null) {
            return false;
        }

        createOrUpdate(target, copy);
        return true;
    }

    public void remove(Npc npc) {
        String npcName = npc.getData().getName();
        storage.delete(npcName);
        if (isAvailable()) {
            getDisplayService().deleteDisplay(storage.getDisplayName(npcName));
        }
    }

    public void handleNpcMoved(Npc npc, Location newLocation) {
        NpcHologramEntry entry = getEntry(npc);
        if (entry == null || !isAvailable()) {
            return;
        }

        applyEntry(npc, entry, newLocation);
        storage.save(entry);
    }

    /**
     * Pushes the NPC's visibility distance into the hologram file and applies it, so changing it on the
     * NPC keeps the hologram in sync. Any other setting stays exactly as written in the file.
     */
    public void refresh(Npc npc) {
        NpcHologramEntry entry = getEntry(npc);
        if (entry == null || !isAvailable()) {
            return;
        }

        setDisplayRange(entry, NpcHologramFactory.resolveDisplayRange(npc.getData().getVisibilityDistance()));
        storage.save(entry);
        applyEntry(npc, entry, npc.getData().getLocation());
    }

    private void setDisplayRange(NpcHologramEntry entry, double range) {
        ConfigDisplaySettings settings = entry.getDisplay().getSettings();
        if (settings == null) {
            settings = new ConfigDisplaySettings();
            entry.getDisplay().setSettings(settings);
        }

        settings.setDisplayRange(range);
    }

    public void loadAll() {
        if (!isAvailable()) {
            return;
        }

        removeOwnDisplays();
        migrator.migrate();
        for (NpcHologramEntry entry : storage.loadAll()) {
            Npc npc = FancyNpcsPlugin.get().getNpcManager().getNpc(entry.getNpcName());
            if (npc == null) {
                continue;
            }

            recreate(npc, entry, npc.getData().getLocation());
        }
    }

    /**
     * Drops every display this plugin owns and builds them again from the files on disk,
     * so edits made in the YAML files become visible.
     *
     * @return the amount of holograms that were loaded
     */
    public int reload() {
        if (!isAvailable()) {
            return 0;
        }

        removeOwnDisplays();
        int loaded = 0;
        for (NpcHologramEntry entry : storage.loadAll()) {
            Npc npc = FancyNpcsPlugin.get().getNpcManager().getNpc(entry.getNpcName());
            if (npc == null) {
                continue;
            }
            if (recreate(npc, entry, npc.getData().getLocation())) {
                loaded++;
            }
        }

        return loaded;
    }

    public void shutdown() {
        if (!isAvailable()) {
            return;
        }

        removeOwnDisplays();
    }

    private void removeOwnDisplays() {
        DisplayService displayService = getDisplayService();
        List<String> ownNames = displayService.getRegisteredDisplayNames().stream()
                .filter(name -> name.startsWith(NpcHologramStorage.DISPLAY_NAME_PREFIX))
                .toList();

        for (String name : ownNames) {
            displayService.deleteDisplay(name);
        }
    }

    /**
     * Recalculates the hologram position of the given NPC, without touching the file.
     * Called once a custom model exists, since the model's height only becomes known then.
     */
    public void reposition(Npc npc) {
        NpcHologramEntry entry = getEntry(npc);
        if (entry == null || !isAvailable()) {
            return;
        }

        applyEntry(npc, entry, npc.getData().getLocation());
    }

    private void applyEntry(Npc npc, NpcHologramEntry entry, Location baseLocation) {
        DecentLocation location = resolveLocation(npc, entry, baseLocation);
        if (location == null) {
            return;
        }

        entry.getDisplay().setLocation(DhLocations.toConfig(location));
        DisplayBase existing = getDisplayService().getDisplay(entry.getDisplay().getName());
        if (existing instanceof TextDisplay textDisplay) {
            update(textDisplay, entry, location);
            return;
        }

        recreate(npc, entry, baseLocation);
    }

    private DecentLocation resolveLocation(Npc npc, NpcHologramEntry entry, Location baseLocation) {
        double totalOffset = NpcHologramFactory.resolveTotalOffset(npc, entry.getYOffset(), entry.getOffsetBase());
        return DhLocations.above(baseLocation, totalOffset);
    }

    private void update(TextDisplay display, NpcHologramEntry entry, DecentLocation location) {
        display.setLines(entry.getLines());
        display.setLocation(location);
        applyFileSettings(display, entry);

        DisplayService displayService = getDisplayService();
        displayService.updateDisplay(display);
        // updateDisplay only refreshes viewers that already see the display, so players who come
        // into range through a bigger display range or a moved NPC need the visibility recheck.
        displayService.updateDisplayVisibility(display);
    }

    private void applyFileSettings(TextDisplay display, NpcHologramEntry entry) {
        ConfigDisplaySettings settings = entry.getDisplay().getSettings();
        if (settings == null) {
            return;
        }

        display.getSettings().setEnabled(settings.isEnabled());
        display.getSettings().setDisplayRange(settings.getDisplayRange());
        display.getSettings().setUpdateInterval(settings.getUpdateInterval());
    }

    /**
     * @return whether the display was built and registered
     */
    private boolean recreate(Npc npc, NpcHologramEntry entry, Location baseLocation) {
        DecentLocation location = resolveLocation(npc, entry, baseLocation);
        if (location == null) {
            return false;
        }

        entry.getDisplay().setLocation(DhLocations.toConfig(location));
        TextDisplay display = buildDisplay(entry);
        if (display == null) {
            return false;
        }

        DisplayService displayService = getDisplayService();
        displayService.deleteDisplay(display.getName());
        displayService.registerDisplay(display);
        displayService.updateDisplayVisibility(display);
        return true;
    }

    private TextDisplay buildDisplay(NpcHologramEntry entry) {
        try {
            return codec.toTextDisplay(entry.getDisplay());
        } catch (Exception e) {
            IntegrationsManager.get().getLogger().error(
                    "Failed to build hologram display of NPC " + entry.getNpcName(),
                    ThrowableProperty.of(e)
            );
            return null;
        }
    }

    private DisplayService getDisplayService() {
        return DecentHologramsAPI.get().getDisplayModule().getDisplayService();
    }

    private boolean isApiRunning() {
        return DecentHologramsAPI.isRunning() && DecentHologramsAPI.get().getDisplayModule() != null;
    }
}
