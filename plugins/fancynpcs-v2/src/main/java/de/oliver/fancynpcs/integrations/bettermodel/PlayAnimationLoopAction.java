package de.oliver.fancynpcs.integrations.bettermodel;

import de.oliver.fancynpcs.integrations.IntegrationsManager;
import de.oliver.fancyanalytics.logger.properties.StringProperty;
import de.oliver.fancynpcs.api.actions.NpcAction;
import de.oliver.fancynpcs.api.actions.executor.ActionExecutionContext;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayAnimationLoopAction extends NpcAction {

    public PlayAnimationLoopAction() {
        super("play_animation_loop", true);
    }

    @Override
    public void execute(@NotNull ActionExecutionContext context, @Nullable String animation) {
        if (animation == null || animation.isEmpty()) {
            return;
        }

        if (context.getPlayer() == null) {
            return;
        }

        if (!CustomModelAttribute.hasAttribute(context.getNpc())) {
            IntegrationsManager.get().getLogger().error(
                    "Trying to execute the play_animation_loop action on a npc without the custom_model attribute.",
                    StringProperty.of("npc", context.getNpc().getData().getName())
            );
            return;
        }

        EntityTracker tracker = CustomModelAttribute.getEntityTracker(context.getNpc());

        if (!tracker.animate(animation)) {
            IntegrationsManager.get().getLogger().warn(
                    "Failed to play animation",
                    StringProperty.of("npc", context.getNpc().getData().getName()),
                    StringProperty.of("animation", animation)
            );
        }
    }
}
