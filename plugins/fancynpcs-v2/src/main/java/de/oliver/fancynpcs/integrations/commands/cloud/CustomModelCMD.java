package de.oliver.fancynpcs.integrations.commands.cloud;

import de.oliver.fancynpcs.integrations.bettermodel.CustomModelAttribute;
import de.oliver.fancynpcs.integrations.commands.IntegrationContext;
import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcAttribute;
import kr.toxicity.model.api.BetterModel;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CustomModelCMD extends IntegrationContext {

    public static final CustomModelCMD INSTANCE = new CustomModelCMD();

    private CustomModelCMD() {
    }

    @Command("npc custom_model <npc> <model>")
    @Permission("fancynpcs.command.npc.custom_model")
    public void onCustomModel(
            final @NotNull CommandSender sender,
            final @NotNull Npc npc,
            final @NotNull @Argument(suggestions = "integrations/custom_model") String model
    ) {
        NpcAttribute customModelAttribute = FancyNpcsPlugin.get().getAttributeManager().getAttributeByName(npc.getData().getType(), CustomModelAttribute.ATTRIBUTE_NAME);

        if (model.equalsIgnoreCase("@none")) {
            npc.getData().removeAttribute(customModelAttribute);
            CustomModelAttribute.closeAllTrackers(npc);
            npc.updateForAll();

            translator.translate("npc_custom_model_removed")
                    .withPrefix()
                    .replace("npc", npc.getData().getName())
                    .send(sender);
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


        npc.getData().addAttribute(customModelAttribute, model);
        npc.updateForAll();

        translator.translate("npc_custom_model_applied")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .replace("model", model)
                .send(sender);
    }

    @Suggestions("integrations/custom_model")
    public List<String> suggestModels(final CommandContext<CommandSender> context, final CommandInput input) {
        return new ArrayList<>() {{
            add("@none");
            addAll(BetterModel.modelKeys());
        }};
    }
}
