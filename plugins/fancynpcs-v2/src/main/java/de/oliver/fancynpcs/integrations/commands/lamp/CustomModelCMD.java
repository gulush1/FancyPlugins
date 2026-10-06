package de.oliver.fancynpcs.integrations.commands.lamp;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcAttribute;
import de.oliver.fancynpcs.integrations.bettermodel.CustomModelAttribute;
import de.oliver.fancynpcs.integrations.commands.IntegrationContext;
import kr.toxicity.model.api.BetterModel;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.autocomplete.SuggestionProvider;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.node.ExecutionContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class CustomModelCMD extends IntegrationContext {

    public static final CustomModelCMD INSTANCE = new CustomModelCMD();

    private CustomModelCMD() {
    }

    @Command("npc custom_model <npc> <model>")
    @CommandPermission("fancynpcs.command.npc.custom_model")
    public void onCustomModel(
            final BukkitCommandActor actor,
            final @NotNull Npc npc,
            final @NotNull @SuggestWith(ModelSuggestion.class) String model
    ) {
        final CommandSender sender = actor.sender();
        if (model.equalsIgnoreCase(CustomModelAttribute.REMOVE_MODEL_VALUE)) {
            removeModel(npc, sender);
            return;
        }

        if (BetterModel.model(model).isEmpty()) {
            translator.translate("npc_custom_model_not_found")
                    .withPrefix()
                    .replace("npc", npc.getData().getName())
                    .replace("model", model)
                    .send(sender);
            return;
        }

        applyModel(npc, model, sender);
    }

    private void removeModel(final Npc npc, final CommandSender sender) {
        npc.getData().removeAttribute(customModelAttributeOf(npc));
        CustomModelAttribute.closeAllTrackers(npc);
        npc.updateForAll();

        translator.translate("npc_custom_model_removed")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .send(sender);
    }

    private void applyModel(final Npc npc, final String model, final CommandSender sender) {
        npc.getData().addAttribute(customModelAttributeOf(npc), model);
        npc.updateForAll();

        translator.translate("npc_custom_model_applied")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .replace("model", model)
                .send(sender);
    }

    private NpcAttribute customModelAttributeOf(final Npc npc) {
        return FancyNpcsPlugin.get().getAttributeManager().getAttributeByName(npc.getData().getType(), CustomModelAttribute.ATTRIBUTE_NAME);
    }

    private static final class ModelSuggestion implements SuggestionProvider<BukkitCommandActor> {

        @Override
        public @NotNull Collection<String> getSuggestions(@NotNull ExecutionContext<BukkitCommandActor> context) {
            List<String> result = new ArrayList<>();
            result.add(CustomModelAttribute.REMOVE_MODEL_VALUE);
            result.addAll(BetterModel.modelKeys());
            return result;
        }
    }
}
