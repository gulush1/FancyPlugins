package de.oliver.fancynpcs.integrations.commands.lamp;

import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.integrations.bettermodel.CustomModelAttribute;
import de.oliver.fancynpcs.integrations.commands.IntegrationContext;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.SuggestWith;
import revxrsal.commands.annotation.Switch;
import revxrsal.commands.autocomplete.SuggestionProvider;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.node.ExecutionContext;

import java.util.Collection;
import java.util.List;

public final class PlayAnimationCMD extends IntegrationContext {

    public static final PlayAnimationCMD INSTANCE = new PlayAnimationCMD();

    private PlayAnimationCMD() {
    }

    @Command("npc play_animation <npc> <animation>")
    @CommandPermission("fancynpcs.command.npc.play_animation")
    public void onPlayAnimation(
            final BukkitCommandActor actor,
            final @NotNull Npc npc,
            final @NotNull @SuggestWith(AnimationSuggestion.class) String animation,
            final @Switch("loop") boolean loop
    ) {
        final CommandSender sender = actor.sender();
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

    private static final class AnimationSuggestion implements SuggestionProvider<BukkitCommandActor> {

        @Override
        public @NotNull Collection<String> getSuggestions(@NotNull ExecutionContext<BukkitCommandActor> context) {
            Npc npc = context.getResolvedArgumentOrNull("npc");
            if (npc == null) return List.of();

            EntityTracker tracker = CustomModelAttribute.getEntityTracker(npc);
            if (tracker == null) return List.of();

            return tracker.renderer().animations().keySet().stream().toList();
        }
    }
}
