package de.oliver.fancynpcs.integrations.commands.lamp;

import de.oliver.fancynpcs.integrations.commands.IntegrationContext;
import de.oliver.fancynpcs.integrations.hologram.NpcHologramService;
import org.bukkit.command.CommandSender;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;

public final class HologramsCMD extends IntegrationContext {

    public static final HologramsCMD INSTANCE = new HologramsCMD();

    private HologramsCMD() {
    }

    @Command("fancynpcs holograms reload")
    @CommandPermission("fancynpcs.command.fancynpcs.holograms")
    public void reload(final BukkitCommandActor actor) {
        final CommandSender sender = actor.sender();
        NpcHologramService service = integrations.getHologramService();
        if (service == null || !service.isAvailable()) {
            translator.translate("npc_hologram_unavailable")
                    .withPrefix()
                    .send(sender);
            return;
        }

        int loaded = service.reload();

        translator.translate("fancynpcs_holograms_reload_success")
                .withPrefix()
                .replace("amount", String.valueOf(loaded))
                .send(sender);
    }
}
