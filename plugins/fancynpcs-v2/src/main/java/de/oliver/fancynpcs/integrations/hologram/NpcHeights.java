package de.oliver.fancynpcs.integrations.hologram;

import de.oliver.fancynpcs.integrations.bettermodel.CustomModelAttribute;
import de.oliver.fancynpcs.integrations.IntegrationsManager;
import de.oliver.fancynpcs.api.Npc;
import org.bukkit.entity.Entity;

/**
 * Resolves how far above its own location an NPC ends, so holograms can sit on top of it
 * instead of on top of its feet.
 */
public final class NpcHeights {

    private static final double FALLBACK_HEIGHT = 1.8;

    private NpcHeights() {
    }

    /**
     * @return the height of the NPC: the rendered height of its custom model when one is applied,
     * otherwise the height of the underlying entity
     */
    public static double resolve(Npc npc) {
        double modelHeight = resolveModelHeight(npc);
        if (modelHeight > 0) {
            return modelHeight;
        }

        Entity entity = npc.getBukkitEntity();
        if (entity != null) {
            return entity.getHeight();
        }

        return resolveApiHeight(npc);
    }

    /**
     * FancyNpcs only exposes the eye height, which sits a bit below the top of the entity.
     * Used when the backing entity cannot be resolved at all.
     */
    private static double resolveApiHeight(Npc npc) {
        float eyeHeight = npc.getEyeHeight();
        if (eyeHeight > 0) {
            return eyeHeight;
        }

        return FALLBACK_HEIGHT * npc.getData().getScale();
    }

    private static double resolveModelHeight(Npc npc) {
        if (!IntegrationsManager.get().isModelSupportEnabled() || !CustomModelAttribute.hasAttribute(npc)) {
            return 0;
        }

        return CustomModelAttribute.getModelHeight(npc);
    }
}
