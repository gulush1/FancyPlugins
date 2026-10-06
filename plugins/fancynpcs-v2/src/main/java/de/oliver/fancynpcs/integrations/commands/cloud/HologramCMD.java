package de.oliver.fancynpcs.integrations.commands.cloud;

import de.oliver.fancynpcs.integrations.hologram.NpcHologramEntry;
import de.oliver.fancynpcs.integrations.hologram.NpcHologramService;
import de.oliver.fancynpcs.integrations.commands.IntegrationContext;
import de.oliver.fancynpcs.api.Npc;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class HologramCMD extends IntegrationContext {

    private static final String PERMISSION = "fancynpcs.command.npc.hologram";

    public static final HologramCMD INSTANCE = new HologramCMD();

    private HologramCMD() {
    }

    @Command("npc hologram <npc> create <offset> <text>")
    @Permission(PERMISSION)
    public void onCreate(
            final @NotNull CommandSender sender,
            final @NotNull Npc npc,
            final double offset,
            final @NotNull @Greedy String text
    ) {
        NpcHologramService service = integrations.getHologramService();
        if (!isAvailable(service, sender)) return;

        if (service.getEntry(npc) != null) {
            sendAlreadyExists(npc, sender);
            return;
        }

        createHologram(service, npc, text, offset, sender);
    }

    private void createHologram(final NpcHologramService service, final Npc npc, final String text, final double offset, final CommandSender sender) {
        List<String> lines = new ArrayList<>();
        lines.add(text);
        NpcHologramEntry entry = service.getFactory().create(npc, lines, offset);
        if (!isCreated(entry, npc, sender)) return;

        service.createOrUpdate(npc, entry);

        translator.translate("npc_hologram_created")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .replace("offset", String.valueOf(offset))
                .send(sender);
    }

    private void sendAlreadyExists(final Npc npc, final CommandSender sender) {
        translator.translate("npc_hologram_already_exists")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .send(sender);
    }

    @Command("npc hologram <npc> add_line <text>")
    @Permission(PERMISSION)
    public void onAddLine(
            final @NotNull CommandSender sender,
            final @NotNull Npc npc,
            final @NotNull @Greedy String text
    ) {
        NpcHologramService service = integrations.getHologramService();
        if (!isAvailable(service, sender)) return;

        NpcHologramEntry entry = service.getEntry(npc);
        if (entry == null) {
            entry = service.getFactory().create(npc, new ArrayList<>(), config.getHologramDefaultYOffset());
            if (!isCreated(entry, npc, sender)) return;
        }

        List<String> lines = entry.getLines();
        lines.add(text);
        entry.setLines(lines);
        service.createOrUpdate(npc, entry);

        translator.translate("npc_hologram_line_added")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .send(sender);
    }

    @Command("npc hologram <npc> set_line <line> <text>")
    @Permission(PERMISSION)
    public void onSetLine(
            final @NotNull CommandSender sender,
            final @NotNull Npc npc,
            final int line,
            final @NotNull @Greedy String text
    ) {
        NpcHologramService service = integrations.getHologramService();
        if (!isAvailable(service, sender)) return;

        NpcHologramEntry entry = service.getEntry(npc);
        if (!hasHologram(entry, npc, sender)) return;

        List<String> lines = entry.getLines();
        if (!isValidLine(lines, line, npc, sender)) return;

        lines.set(line - 1, text);
        entry.setLines(lines);
        service.createOrUpdate(npc, entry);

        translator.translate("npc_hologram_line_set")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .replace("line", String.valueOf(line))
                .send(sender);
    }

    @Command("npc hologram <npc> remove_line <line>")
    @Permission(PERMISSION)
    public void onRemoveLine(
            final @NotNull CommandSender sender,
            final @NotNull Npc npc,
            final int line
    ) {
        NpcHologramService service = integrations.getHologramService();
        if (!isAvailable(service, sender)) return;

        NpcHologramEntry entry = service.getEntry(npc);
        if (!hasHologram(entry, npc, sender)) return;

        List<String> lines = entry.getLines();
        if (!isValidLine(lines, line, npc, sender)) return;

        lines.remove(line - 1);
        applyLineRemoval(service, entry, lines, npc, line, sender);
    }

    private void applyLineRemoval(final NpcHologramService service, final NpcHologramEntry entry, final List<String> lines, final Npc npc, final int line, final CommandSender sender) {
        if (lines.isEmpty()) {
            service.remove(npc);
            sendRemoved(npc, sender);
            return;
        }

        entry.setLines(lines);
        service.createOrUpdate(npc, entry);
        translator.translate("npc_hologram_line_removed")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .replace("line", String.valueOf(line))
                .send(sender);
    }

    @Command("npc hologram <npc> set_offset <offset>")
    @Permission(PERMISSION)
    public void onSetOffset(
            final @NotNull CommandSender sender,
            final @NotNull Npc npc,
            final double offset
    ) {
        NpcHologramService service = integrations.getHologramService();
        if (!isAvailable(service, sender)) return;

        NpcHologramEntry entry = service.getEntry(npc);
        if (!hasHologram(entry, npc, sender)) return;

        entry.setYOffset(offset);
        service.createOrUpdate(npc, entry);

        translator.translate("npc_hologram_offset_set")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .replace("offset", String.valueOf(offset))
                .send(sender);
    }

    @Command("npc hologram <npc> remove")
    @Permission(PERMISSION)
    public void onRemove(
            final @NotNull CommandSender sender,
            final @NotNull Npc npc
    ) {
        NpcHologramService service = integrations.getHologramService();
        if (!isAvailable(service, sender)) return;

        NpcHologramEntry entry = service.getEntry(npc);
        if (!hasHologram(entry, npc, sender)) return;

        service.remove(npc);
        sendRemoved(npc, sender);
    }

    @Command("npc hologram <npc> info")
    @Permission(PERMISSION)
    public void onInfo(
            final @NotNull CommandSender sender,
            final @NotNull Npc npc
    ) {
        NpcHologramService service = integrations.getHologramService();
        if (!isAvailable(service, sender)) return;

        NpcHologramEntry entry = service.getEntry(npc);
        if (!hasHologram(entry, npc, sender)) return;

        translator.translate("npc_hologram_info_header")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .send(sender);

        sendLines(entry.getLines(), sender);

        translator.translate("npc_hologram_info_offset")
                .replace("offset", String.valueOf(entry.getYOffset()))
                .replace("base", entry.getOffsetBase().name())
                .send(sender);
    }

    private boolean isAvailable(final NpcHologramService service, final CommandSender sender) {
        if (service != null && service.isAvailable()) return true;

        translator.translate("npc_hologram_unavailable")
                .withPrefix()
                .send(sender);
        return false;
    }

    private boolean isCreated(final NpcHologramEntry entry, final Npc npc, final CommandSender sender) {
        if (entry != null) return true;

        translator.translate("npc_hologram_create_failed")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .send(sender);
        return false;
    }

    private boolean hasHologram(final NpcHologramEntry entry, final Npc npc, final CommandSender sender) {
        if (entry != null) return true;

        translator.translate("npc_hologram_no_hologram")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .send(sender);
        return false;
    }

    private boolean isValidLine(final List<String> lines, final int line, final Npc npc, final CommandSender sender) {
        if (line >= 1 && line <= lines.size()) return true;

        translator.translate("npc_hologram_invalid_line")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .replace("line", String.valueOf(line))
                .send(sender);
        return false;
    }

    private void sendRemoved(final Npc npc, final CommandSender sender) {
        translator.translate("npc_hologram_removed")
                .withPrefix()
                .replace("npc", npc.getData().getName())
                .send(sender);
    }

    private void sendLines(final List<String> lines, final CommandSender sender) {
        for (int i = 0; i < lines.size(); i++) {
            translator.translate("npc_hologram_info_line")
                    .replace("line", String.valueOf(i + 1))
                    .replace("text", lines.get(i))
                    .send(sender);
        }
    }
}
