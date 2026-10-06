package de.oliver.fancynpcs.integrations.resourcepack;

import de.oliver.fancynpcs.integrations.bettermodel.CustomModelAttribute;
import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Applies a player's resource-pack decision to the BetterModel models of all FancyNpcs NPCs.
 * Must run on the player's scheduler thread. Models are only spawned for NPCs the player
 * currently sees; for the others the player is only whitelisted, so the model appears as soon as
 * FancyNpcs spawns the NPC for them.
 */
public final class ModelVisibilityApplier {

    public ModelVisibilityApplier() {
    }

    public void applyToAll(@NotNull Player player, boolean hasResourcePack) {
        forEachModelNpc(npc -> applyTo(npc, player, hasResourcePack));
    }

    public void applyTo(@NotNull Npc npc, @NotNull Player player, boolean hasResourcePack) {
        if (!hasResourcePack) {
            CustomModelAttribute.applyModelVisibility(npc, player, false);
            return;
        }
        if (npc.isShownFor(player)) {
            CustomModelAttribute.applyModelVisibility(npc, player, true);
            return;
        }
        CustomModelAttribute.preAuthorize(npc, player, true);
    }

    public void unmarkEverywhere(@NotNull Player player) {
        forEachModelNpc(npc -> CustomModelAttribute.unmarkPlayer(npc, player));
    }

    private void forEachModelNpc(Consumer<Npc> action) {
        for (Npc npc : FancyNpcsPlugin.get().getNpcManager().getAllNpcs()) {
            if (CustomModelAttribute.hasAttribute(npc)) {
                action.accept(npc);
            }
        }
    }
}
