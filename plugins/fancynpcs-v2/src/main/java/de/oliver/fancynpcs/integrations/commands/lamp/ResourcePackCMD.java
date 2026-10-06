package de.oliver.fancynpcs.integrations.commands.lamp;

import de.oliver.fancynpcs.integrations.commands.IntegrationContext;
import de.oliver.fancynpcs.integrations.resourcepack.ResourcePackService;
import de.oliver.fancynpcs.integrations.resourcepack.state.PackSessionRegistry;
import de.oliver.fancynpcs.integrations.resourcepack.state.PackStatus;
import de.oliver.fancynpcs.integrations.resourcepack.state.PlayerPackSession;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.Map;
import java.util.UUID;

public final class ResourcePackCMD extends IntegrationContext {

    public static final ResourcePackCMD INSTANCE = new ResourcePackCMD();

    private static final String LEGACY_PACK_LABEL = "legacy (1.20-1.20.2)";
    private static final String UNIDENTIFIED_PACK_LABEL = "unidentified (configuration phase)";
    private static final String RESULT_MODEL = "custom model";
    private static final String RESULT_FALLBACK = "vanilla NPC";

    private ResourcePackCMD() {
    }

    @Command("fancynpcs resourcepack <player>")
    @CommandPermission("fancynpcs.command.fancynpcs.resourcepack")
    public void info(final BukkitCommandActor actor, final @NotNull Player player) {
        final CommandSender sender = actor.sender();
        ResourcePackService service = integrations.getResourcePackService();
        if (service == null) {
            translator.translate("fancynpcs_resourcepack_unavailable")
                    .withPrefix()
                    .send(sender);
            return;
        }

        sendSummary(sender, service, player);
        sendPacks(sender, service.session(player));
    }

    private void sendSummary(final @NotNull CommandSender sender, final @NotNull ResourcePackService service,
                             final @NotNull Player player) {
        translator.translate("fancynpcs_resourcepack_header")
                .withPrefix()
                .replace("player", player.getName())
                .send(sender);

        translator.translate("fancynpcs_resourcepack_mode")
                .replace("detection", service.settings().detection().name())
                .replace("observer", String.valueOf(service.isObserverInstalled()))
                .replace("active", String.valueOf(service.isFallbackActive()))
                .send(sender);

        translator.translate("fancynpcs_resourcepack_decision")
                .replace("state", service.nativeDecision(player).name())
                .replace("result", service.hasResourcePack(player) ? RESULT_MODEL : RESULT_FALLBACK)
                .send(sender);
    }

    private void sendPacks(final @NotNull CommandSender sender, final @Nullable PlayerPackSession session) {
        Map<UUID, PackStatus> packs = session == null ? Map.of() : session.snapshot();
        if (packs.isEmpty()) {
            translator.translate("fancynpcs_resourcepack_no_packs")
                    .send(sender);
            return;
        }

        for (Map.Entry<UUID, PackStatus> entry : packs.entrySet()) {
            translator.translate("fancynpcs_resourcepack_pack")
                    .replace("id", packLabel(entry.getKey()))
                    .replace("status", entry.getValue().name())
                    .send(sender);
        }
    }

    private String packLabel(final @NotNull UUID id) {
        if (id.equals(PackSessionRegistry.LEGACY_PACK_ID)) {
            return LEGACY_PACK_LABEL;
        }
        if (id.equals(PackSessionRegistry.UNIDENTIFIED_PACK_ID)) {
            return UNIDENTIFIED_PACK_LABEL;
        }
        return id.toString();
    }
}
