package de.oliver.fancynpcs.integrations;

import de.oliver.fancyanalytics.logger.ExtendedFancyLogger;
import de.oliver.fancylib.translations.Translator;
import de.oliver.fancynpcs.FancyNpcs;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcAttribute;
import de.oliver.fancynpcs.integrations.bettermodel.CustomModelAttribute;
import de.oliver.fancynpcs.integrations.bettermodel.NpcModelSpawnListener;
import de.oliver.fancynpcs.integrations.bettermodel.NpcRemoveListener;
import de.oliver.fancynpcs.integrations.bettermodel.PlayAnimationLoopAction;
import de.oliver.fancynpcs.integrations.bettermodel.PlayAnimationOnceAction;
import de.oliver.fancynpcs.integrations.hologram.DhReloadListener;
import de.oliver.fancynpcs.integrations.hologram.NpcCopyListener;
import de.oliver.fancynpcs.integrations.hologram.NpcHologramFactory;
import de.oliver.fancynpcs.integrations.hologram.NpcHologramListener;
import de.oliver.fancynpcs.integrations.hologram.NpcHologramService;
import de.oliver.fancynpcs.integrations.hologram.NpcHologramStorage;
import de.oliver.fancynpcs.integrations.hologram.dh.DhHologramCodec;
import de.oliver.fancynpcs.integrations.resourcepack.ResourcePackService;
import de.oliver.fancynpcs.integrations.resourcepack.ResourcePackStatusListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Wires the built-in BetterModel and DecentHolograms integrations into FancyNpcs.
 */
public final class IntegrationsManager {

    private static final String BETTER_MODEL_PLUGIN_NAME = "BetterModel";
    private static final String DECENT_HOLOGRAMS_PLUGIN_NAME = "DecentHolograms";
    private static final String LEGACY_ADDON_PLUGIN_NAME = "FancyNpcsModel";

    private final FancyNpcs plugin;

    private IntegrationsConfig config;
    private NpcHologramService hologramService;
    private ResourcePackService resourcePackService;
    private boolean legacyAddonPresent;
    private boolean modelSupportEnabled;
    private boolean hologramSupportEnabled;

    public IntegrationsManager(FancyNpcs plugin) {
        this.plugin = plugin;
    }

    public static IntegrationsManager get() {
        return FancyNpcs.getInstance().getIntegrations();
    }

    public void load() {
        if (Bukkit.getPluginManager().getPlugin(LEGACY_ADDON_PLUGIN_NAME) != null) {
            legacyAddonPresent = true;
            warnAboutLegacyAddon();
            return;
        }

        new LegacyAddonMigrator(plugin.getDataFolder(), getLogger()).migrate();

        config = new IntegrationsConfig();
        config.init();
        config.reload();

        modelSupportEnabled = Bukkit.getPluginManager().isPluginEnabled(BETTER_MODEL_PLUGIN_NAME);
        if (!modelSupportEnabled) {
            warnAboutMissingBetterModel();
        }

        hologramSupportEnabled = Bukkit.getPluginManager().isPluginEnabled(DECENT_HOLOGRAMS_PLUGIN_NAME);
        if (hologramSupportEnabled) {
            setUpHologramService();
        }

        if (modelSupportEnabled) {
            resourcePackService = new ResourcePackService(plugin);
            resourcePackService.enable();
        }

        plugin.getAttributeManager().registerAttribute(createModelAttribute());
        registerActions();
        registerListeners();
    }

    public void registerCommands(boolean lamp) {
        if (legacyAddonPresent) {
            return;
        }
        registerCommand(lamp,
                () -> de.oliver.fancynpcs.integrations.commands.lamp.HologramCMD.INSTANCE,
                () -> de.oliver.fancynpcs.integrations.commands.cloud.HologramCMD.INSTANCE);
        registerCommand(lamp,
                () -> de.oliver.fancynpcs.integrations.commands.lamp.HologramsCMD.INSTANCE,
                () -> de.oliver.fancynpcs.integrations.commands.cloud.HologramsCMD.INSTANCE);
        if (modelSupportEnabled) {
            registerModelCommands(lamp);
        }
    }

    public void reload() {
        if (legacyAddonPresent) {
            return;
        }
        config.reload();
        if (resourcePackService != null) {
            resourcePackService.reload();
        }
        if (modelSupportEnabled) {
            closeModelTrackers();
        }
    }

    public void disable() {
        if (resourcePackService != null) {
            resourcePackService.disable();
            resourcePackService = null;
        }
        if (modelSupportEnabled) {
            closeModelTrackers();
        }
        if (hologramService != null) {
            hologramService.shutdown();
        }
    }

    public FancyNpcs getPlugin() {
        return plugin;
    }

    public IntegrationsConfig getConfig() {
        return config;
    }

    public ExtendedFancyLogger getLogger() {
        return plugin.getFancyLogger();
    }

    public Translator getTranslator() {
        return plugin.getTranslator();
    }

    public boolean isModelSupportEnabled() {
        return modelSupportEnabled;
    }

    public boolean isHologramSupportEnabled() {
        return hologramSupportEnabled;
    }

    public @Nullable NpcHologramService getHologramService() {
        return hologramService;
    }

    public @Nullable ResourcePackService getResourcePackService() {
        return resourcePackService;
    }

    private void registerModelCommands(boolean lamp) {
        registerCommand(lamp,
                () -> de.oliver.fancynpcs.integrations.commands.lamp.CustomModelCMD.INSTANCE,
                () -> de.oliver.fancynpcs.integrations.commands.cloud.CustomModelCMD.INSTANCE);
        registerCommand(lamp,
                () -> de.oliver.fancynpcs.integrations.commands.lamp.PlayAnimationCMD.INSTANCE,
                () -> de.oliver.fancynpcs.integrations.commands.cloud.PlayAnimationCMD.INSTANCE);
        registerCommand(lamp,
                () -> de.oliver.fancynpcs.integrations.commands.lamp.ResourcePackCMD.INSTANCE,
                () -> de.oliver.fancynpcs.integrations.commands.cloud.ResourcePackCMD.INSTANCE);
    }

    private void registerCommand(boolean lamp, Supplier<Object> lampCommand, Supplier<Object> cloudCommand) {
        plugin.registerCommand(lamp ? lampCommand.get() : cloudCommand.get());
    }

    private void closeModelTrackers() {
        for (Npc npc : plugin.getNpcManagerImpl().getAllNpcs()) {
            if (CustomModelAttribute.hasAttribute(npc)) {
                CustomModelAttribute.closeAllTrackers(npc);
            }
        }
    }

    /**
     * FancyNpcs drops unknown attributes while loading npcs.yml, so without this attribute a restart
     * without BetterModel would wipe every stored custom_model value. Without BetterModel an inert
     * attribute is registered instead; its empty possible-values list accepts any value.
     */
    private NpcAttribute createModelAttribute() {
        if (modelSupportEnabled) {
            return CustomModelAttribute.getModelAttribute();
        }
        return new NpcAttribute(
                CustomModelAttribute.ATTRIBUTE_NAME,
                List::of,
                Arrays.stream(EntityType.values()).filter(EntityType::isAlive).toList(),
                (npc, value) -> {
                }
        );
    }

    private void setUpHologramService() {
        DhHologramCodec codec = new DhHologramCodec();
        NpcHologramStorage storage = new NpcHologramStorage(codec);
        hologramService = new NpcHologramService(codec, storage, new NpcHologramFactory(codec, storage));
    }

    private void registerActions() {
        if (!modelSupportEnabled) {
            return;
        }
        plugin.getActionManager().registerAction(new PlayAnimationOnceAction());
        plugin.getActionManager().registerAction(new PlayAnimationLoopAction());
    }

    private void registerListeners() {
        if (hologramSupportEnabled) {
            registerListener(new NpcHologramListener());
            registerListener(new NpcCopyListener());
        }
        if (modelSupportEnabled) {
            registerListener(new NpcRemoveListener());
            registerListener(new ResourcePackStatusListener(resourcePackService));
            registerListener(new NpcModelSpawnListener());
        }
        if (hologramSupportEnabled) {
            registerListener(new DhReloadListener());
        }
    }

    private void registerListener(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    private void warnAboutLegacyAddon() {
        getLogger().error("""

                --------------------------------------------------
                The FancyNpcsModel addon is installed, but its features are now built into FancyNpcs.
                Remove FancyNpcsModel-*.jar and restart; integrations are disabled until then.
                --------------------------------------------------
                """);
    }

    private void warnAboutMissingBetterModel() {
        getLogger().warn("""

                --------------------------------------------------
                The BetterModel plugin was not found, so custom models are disabled.
                Holograms keep working: /npc hologram ... stays available, /npc custom_model and /npc play_animation do not.
                You can download BetterModel here: https://modrinth.com/plugin/bettermodel
                --------------------------------------------------
                """);
    }

}
