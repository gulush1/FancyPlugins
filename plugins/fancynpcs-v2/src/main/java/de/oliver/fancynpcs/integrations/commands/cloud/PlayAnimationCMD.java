package de.oliver.fancynpcs.integrations.commands.cloud;

import de.oliver.fancynpcs.integrations.bettermodel.CustomModelAttribute;
import de.oliver.fancynpcs.integrations.commands.IntegrationContext;
import de.oliver.fancynpcs.api.Npc;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Flag;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PlayAnimationCMD extends IntegrationContext {

    public static final PlayAnimationCMD INSTANCE = new PlayAnimationCMD();

    private PlayAnimationCMD() {
    }

    @Command("npc play_animation <npc> <animation>")
    @Permission("fancynpcs.command.npc.play_animation")
    public void onPlayAnimation(
            final @NotNull CommandSender sender,
            final @NotNull Npc npc,
            final @NotNull @Argument(suggestions = "integrations/play_animation") String animation,
            final @Flag("loop") boolean loop
    ) {
        if (!CustomModelAttribute.hasAttribute(npc)) {
            translator.translate("npc_play_animation_no_model")
                    .withPrefix()
                    .replace("npc", npc.getData().getName())
                    .send(sender);
            return;
        }

        EntityTracker tracker = CustomModelAttribute.getEntityTracker(npc);
        AnimationModifier modifier = loop ? AnimationModifier.DEFAULT : AnimationModifier.DEFAULT_WITH_PLAY_ONCE;
        if (tracker == null || !tracker.animate(animation, modifier)) {
            sendResult("npc_play_animation_failed", npc, animation, sender);
            return;
        }

        sendResult("npc_play_animation_success", npc, animation, sender);
    }

    private void sendResult(final String key, final Npc npc, final String animation, final CommandSender sender) {
        translator.translate(key)
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .replace("animation", animation)
                .send(sender);
    }

    @Suggestions("integrations/play_animation")
    public List<String> suggestAnimations(final CommandContext<CommandSender> context, final CommandInput input) {
        Npc npc = context.<Npc>optional("npc").orElse(null);
        if (npc == null) return new ArrayList<>();

        EntityTracker tracker = CustomModelAttribute.getEntityTracker(npc);
        if (tracker == null) return new ArrayList<>();

        return tracker.renderer().animations().keySet().stream().toList();
    }
}
