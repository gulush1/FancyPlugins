package de.oliver.fancynpcs.integrations.bettermodel;

import de.oliver.fancynpcs.integrations.hologram.NpcHologramService;
import de.oliver.fancynpcs.integrations.IntegrationsManager;
import de.oliver.fancynpcs.integrations.resourcepack.ResourcePackChecker;
import de.oliver.fancyanalytics.logger.properties.StringProperty;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcAttribute;
import de.oliver.fancynpcs.api.actions.ActionTrigger;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.event.hitbox.HitBoxDamagedEvent;
import kr.toxicity.model.api.event.hitbox.HitBoxInteractAtEvent;
import kr.toxicity.model.api.platform.PlatformEntity;
import kr.toxicity.model.api.platform.PlatformPlayer;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.EntityTrackerRegistry;
import kr.toxicity.model.api.tracker.ModelRotation;
import kr.toxicity.model.api.tracker.TrackerModifier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class CustomModelAttribute {

    public static final String ATTRIBUTE_NAME = "custom_model";

    /** Command argument value that removes the custom model from an NPC. */
    public static final String REMOVE_MODEL_VALUE = "@none";

    private static final UUID FALLBACK_SENTINEL = new UUID(0L, 0L);
    private static final float MODEL_PITCH = 0f;

    /**
     * FancyNpcs drops attribute values that are not in the possible-values list while loading npcs.yml,
     * which would silently wipe custom_model whenever a model is renamed or BetterModel loads its models
     * late. An empty list accepts any value; the model name is validated by /npc custom_model instead.
     */
    public static NpcAttribute getModelAttribute() {
        return new NpcAttribute(
                ATTRIBUTE_NAME,
                List::of,
                Arrays.stream(EntityType.values()).filter(EntityType::isAlive).toList(),
                CustomModelAttribute::setModel
        );
    }

    private static void setModel(Npc npc, String modelName) {
        Entity bukkitEntity = getBukkitEntity(npc);
        if (bukkitEntity == null) {
            return;
        }
        bukkitEntity.customName(Component.empty());

        // FancyNpcs re-applies attributes every time the NPC is spawned for a player.
        // Recreating an already matching tracker would respawn the model for every viewer.
        if (isModelApplied(bukkitEntity, modelName)) {
            return;
        }

        // Close all existing trackers
        closeAllTrackers(bukkitEntity);

        // remove model if model name is REMOVE_MODEL_VALUE
        if (modelName.equalsIgnoreCase(REMOVE_MODEL_VALUE)) {
            return;
        }

        // Gets or creates entity tracker
        EntityTracker tracker = BetterModel.model(modelName)
                .map(r -> r.getOrCreate(BukkitAdapter.adapt(bukkitEntity), TrackerModifier.DEFAULT, CustomModelAttribute::seedSpawnWhitelist))
                .orElse(null);
        if (tracker == null) {
            IntegrationsManager.get().getLogger().error(
                    "Failed to get model with name " + modelName,
                    StringProperty.of("model_name", modelName),
                    StringProperty.of("npc_name", npc.getData().getName())
            );
            return;
        }

        bindRotationToNpc(npc, tracker);

        // Scale
        if (npc.getData().getScale() != 1) {
            tracker.scaler(tracker.scaler().multiply(npc.getData().getScale()));
        }

        // Right click on hitbox
        tracker.listenHitBox(HitBoxInteractAtEvent.class, event -> {
            Player player = Bukkit.getPlayer(event.getWho().uuid());
            if (player == null) return;

            npc.interact(player, ActionTrigger.RIGHT_CLICK);
        });

        // Left click on hitbox
        tracker.listenHitBox(HitBoxDamagedEvent.class, event -> {
            PlatformEntity causingEntity = event.getSource().getCausingEntity();
            if (causingEntity == null) return;
            Player player = Bukkit.getPlayer(causingEntity.uuid());
            if (player == null) return;

            npc.interact(player, ActionTrigger.LEFT_CLICK);
        });

        spawnForViewers(npc, tracker, tracker.registry());
        repositionHologram(npc);
    }

    /**
     * The hologram of an NPC sits above its model, whose height is only known once the tracker exists.
     */
    private static void repositionHologram(Npc npc) {
        NpcHologramService hologramService = IntegrationsManager.get().getHologramService();
        if (hologramService == null) {
            return;
        }

        hologramService.reposition(npc);
    }

    /**
     * @return whether the entity already has a living tracker for exactly this model
     */
    private static boolean isModelApplied(Entity bukkitEntity, String modelName) {
        if (modelName.equalsIgnoreCase(REMOVE_MODEL_VALUE)) {
            return false;
        }

        return BetterModel.registry(BukkitAdapter.adapt(bukkitEntity))
                .map(registry -> registry.tracker(modelName))
                .filter(tracker -> !tracker.isClosed())
                .isPresent();
    }

    /**
     * FancyNpcs entities are never added to the level, so they are never valid and BetterModel treats
     * them as dead, freezing the pipeline rotation. The model is therefore driven by the NPC's own
     * yaw: the supplier covers the live path, the tick handler covers the frozen one (the pipeline
     * ignores unchanged rotations, so this is free when the NPC does not turn).
     */
    private static void bindRotationToNpc(Npc npc, EntityTracker tracker) {
        tracker.rotation(() -> modelRotationOf(npc));
        tracker.tick((t, bundlers) -> t.getPipeline().rotate(modelRotationOf(npc), bundlers.getTickBundler()));
    }

    private static ModelRotation modelRotationOf(Npc npc) {
        return new ModelRotation(MODEL_PITCH, npc.getData().getLocation().getYaw());
    }

    private static void spawnForViewers(Npc npc, EntityTracker tracker, EntityTrackerRegistry registry) {
        boolean fallback = ResourcePackChecker.isFallbackActive();
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Spawning the model for players that do not see the NPC yet would register them as
            // viewers, making the catch-up spawn skip them once they actually walk into range.
            if (!npc.isShownFor(player)) {
                continue;
            }

            PlatformPlayer platformPlayer = BukkitAdapter.adapt(player);
            if (fallback && !ResourcePackChecker.hasResourcePack(player)) {
                registry.remove(platformPlayer);
                continue;
            }
            if (fallback) {
                tracker.markPlayerForSpawn(platformPlayer);
            }
            registry.spawn(platformPlayer);
        }
    }

    private static void seedSpawnWhitelist(EntityTracker tracker) {
        if (!ResourcePackChecker.isFallbackActive()) {
            return;
        }
        tracker.markPlayerForSpawn(Set.of(FALLBACK_SENTINEL));
    }

    private static Entity getBukkitEntity(Npc npc) {
        return npc.getBukkitEntity();
    }

    /**
     * @return the rendered height of the NPC's model, or 0 if it has no living tracker
     */
    public static double getModelHeight(Npc npc) {
        EntityTracker tracker = getEntityTracker(npc);
        return tracker == null ? 0 : tracker.height();
    }

    /**
     * Closes all model trackers for the given NPC's entity.
     * This is necessary to prevent old trackers still existing in the world.
     */
    public static void closeAllTrackers(Npc npc) {
        Entity bukkitEntity = getBukkitEntity(npc);
        if (bukkitEntity == null) {
            return;
        }

        closeAllTrackers(bukkitEntity);
    }

    private static void closeAllTrackers(Entity bukkitEntity) {
        BetterModel.registry(BukkitAdapter.adapt(bukkitEntity)).ifPresent(reg -> {
            for (EntityTracker tracker : reg.trackers()) {
                tracker.close();
            }
        });
    }

    /**
     * @return whether the given NPC has the model attribute
     */
    public static boolean hasAttribute(Npc npc) {
        for (Map.Entry<NpcAttribute, String> entry : npc.getData().getAttributes().entrySet()) {
            if (entry.getKey().getName().equalsIgnoreCase(ATTRIBUTE_NAME)) {
                return true;
            }
        }

        return false;
    }

    public static EntityTracker getEntityTracker(Npc npc) {
        Entity bukkitEntity = getBukkitEntity(npc);
        if (bukkitEntity == null) {
            return null;
        }

        Optional<EntityTrackerRegistry> trackersOpt = BetterModel.registry(BukkitAdapter.adapt(bukkitEntity));
        if (trackersOpt.isEmpty()) return null;

        Collection<EntityTracker> trackers = trackersOpt.get().trackers();
        if (trackers.isEmpty()) return null;

        return trackers.iterator().next();
    }

    /**
     * Whitelists the player before FancyNpcs sends the NPC spawn packets, so the model can be rendered
     * in the same tick the NPC appears instead of one recheck later.
     */
    public static void preAuthorize(Npc npc, Player player, boolean hasResourcePack) {
        if (!ResourcePackChecker.isFallbackActive() || !hasResourcePack) {
            return;
        }

        EntityTracker tracker = getEntityTracker(npc);
        if (tracker == null) {
            return;
        }

        tracker.markPlayerForSpawn(Set.of(FALLBACK_SENTINEL));
        tracker.markPlayerForSpawn(BukkitAdapter.adapt(player));
    }

    public static void applyModelVisibility(Npc npc, Player player, boolean hasResourcePack) {
        if (!ResourcePackChecker.isFallbackActive()) return;

        EntityTracker tracker = getEntityTracker(npc);
        if (tracker == null) {
            return;
        }
        tracker.markPlayerForSpawn(Set.of(FALLBACK_SENTINEL));

        PlatformPlayer platformPlayer = BukkitAdapter.adapt(player);
        if (hasResourcePack) {
            showModel(tracker, platformPlayer, player.getUniqueId());
        } else {
            hideModel(tracker, platformPlayer);
        }
    }

    private static void showModel(EntityTracker tracker, PlatformPlayer platformPlayer, UUID playerId) {
        tracker.markPlayerForSpawn(platformPlayer);
        // A stale "already spawned" state would leave the player without a model until the NPC is
        // despawned and spawned again, so the spawn is sent unconditionally. It only ever targets
        // this single player and never recreates the tracker.
        tracker.registry().spawn(platformPlayer);
    }

    private static void hideModel(EntityTracker tracker, PlatformPlayer platformPlayer) {
        tracker.unmarkPlayerForSpawn(platformPlayer);
        tracker.registry().remove(platformPlayer);
    }

    /**
     * Spawns the model for a viewer who did not receive it yet.
     * FancyNpcs spawns the NPC separately for every player, but the underlying BetterModel
     * {@link EntityTracker} is only created once, for the first viewer, and {@link #setModel}
     * returns early on every later spawn because a matching tracker already exists. Without this
     * catch-up call, players who start seeing the NPC after the tracker was created (joining later,
     * walking into range after a restart, etc.) would never receive the model spawn packets.
     * Skipped when the resource-pack fallback is active, since ResourcePackStatusListener already
     * owns per-player visibility (whitelist + spawn) for that mode.
     */
    public static void ensureSpawnedFor(Npc npc, Player player) {
        if (ResourcePackChecker.isFallbackActive()) {
            return;
        }

        EntityTracker tracker = getEntityTracker(npc);
        if (tracker == null) {
            return;
        }

        tracker.registry().spawn(BukkitAdapter.adapt(player));
    }

    public static void unmarkPlayer(Npc npc, Player player) {
        EntityTracker tracker = getEntityTracker(npc);
        if (tracker == null) {
            return;
        }

        tracker.unmarkPlayerForSpawn(BukkitAdapter.adapt(player));
    }

    public static void reapplyModel(Npc npc) {
        for (Map.Entry<NpcAttribute, String> entry : npc.getData().getAttributes().entrySet()) {
            if (entry.getKey().getName().equalsIgnoreCase(ATTRIBUTE_NAME)) {
                setModel(npc, entry.getValue());
                return;
            }
        }
    }
}
