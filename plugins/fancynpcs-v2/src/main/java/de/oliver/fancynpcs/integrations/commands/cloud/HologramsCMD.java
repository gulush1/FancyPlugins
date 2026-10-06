package de.oliver.fancynpcs.integrations.commands.cloud;

import de.oliver.fancynpcs.integrations.hologram.NpcHologramService;
import de.oliver.fancynpcs.integrations.commands.IntegrationContext;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.jetbrains.annotations.NotNull;

public class HologramsCMD extends IntegrationContext {

    public static final HologramsCMD INSTANCE = new HologramsCMD();

    private HologramsCMD() {
    }

    @Command("fancynpcs holograms reload")
    @Permission("fancynpcs.command.fancynpcs.holograms")
    public void reload(
            final @NotNull CommandSender sender
    ) {
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
