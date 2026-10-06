package de.oliver.fancynpcs.integrations.hologram.legacy;

import de.oliver.fancynpcs.integrations.hologram.HologramOffsetBase;
import de.oliver.fancynpcs.integrations.hologram.NpcHologramEntry;
import de.oliver.fancynpcs.integrations.hologram.NpcHologramFactory;
import de.oliver.fancynpcs.integrations.hologram.NpcHologramStorage;
import de.oliver.fancynpcs.integrations.IntegrationsManager;
import de.oliver.fancyanalytics.logger.ExtendedFancyLogger;
import de.oliver.fancyanalytics.logger.properties.ThrowableProperty;
import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.jdb.JDB;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Moves holograms from the old JDB storage to one DecentHolograms styled YAML file per NPC.
 * Runs once: the old directory is renamed as soon as every record has been converted.
 */
public class LegacyHologramMigrator {

    private static final String LEGACY_BASE_DIRECTORY = "data";
    private static final String LEGACY_COLLECTION = "holograms";
    private static final String MIGRATED_DIRECTORY = "holograms-migrated";

    private final NpcHologramStorage storage;
    private final NpcHologramFactory factory;

    public LegacyHologramMigrator(NpcHologramStorage storage, NpcHologramFactory factory) {
        this.storage = storage;
        this.factory = factory;
    }

    public void migrate() {
        Path legacyDirectory = Paths.get(legacyBasePath(), LEGACY_COLLECTION);
        if (!Files.isDirectory(legacyDirectory)) {
            return;
        }

        List<LegacyHologramData> records = loadLegacyRecords();
        if (records.isEmpty()) {
            return;
        }

        int migrated = 0;
        int skipped = 0;
        for (LegacyHologramData record : records) {
            switch (migrateRecord(record)) {
                case MIGRATED -> migrated++;
                case FAILED -> skipped++;
                case ALREADY_PRESENT -> {
                }
            }
        }

        finish(legacyDirectory, migrated, skipped);
    }

    private Result migrateRecord(LegacyHologramData record) {
        Npc npc = resolveNpc(record);
        if (npc == null) {
            logger().warn("Could not migrate hologram of NPC %s: NPC not found"
                    .formatted(record.getNpcName() != null ? record.getNpcName() : record.getNpcId()));
            return Result.FAILED;
        }

        String npcName = npc.getData().getName();
        if (storage.find(npcName) != null) {
            return Result.ALREADY_PRESENT;
        }

        // The old offsets were measured from the NPC's feet, so migrated holograms keep that base
        // and stay exactly where they were.
        NpcHologramEntry entry = factory.create(npc, record.getLines(), record.getYOffset(),
                HologramOffsetBase.FEET);
        if (entry == null) {
            logger().warn("Could not migrate hologram of NPC %s: world not loaded".formatted(npcName));
            return Result.FAILED;
        }

        storage.save(entry);
        return Result.MIGRATED;
    }

    private Npc resolveNpc(LegacyHologramData record) {
        if (record.getNpcName() != null) {
            return FancyNpcsPlugin.get().getNpcManager().getNpc(record.getNpcName());
        }
        if (record.getNpcId() != null) {
            return FancyNpcsPlugin.get().getNpcManager().getNpcById(record.getNpcId());
        }

        return null;
    }

    private void finish(Path legacyDirectory, int migrated, int skipped) {
        if (migrated > 0) {
            logger().info("Migrated %d hologram(s) to %s".formatted(migrated, storage.getDirectory()));
        }
        if (skipped > 0) {
            logger().warn("%d hologram(s) could not be migrated, the old data was kept".formatted(skipped));
            return;
        }

        archive(legacyDirectory);
    }

    private void archive(Path legacyDirectory) {
        Path target = legacyDirectory.resolveSibling(MIGRATED_DIRECTORY);
        try {
            Files.move(legacyDirectory, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger().error("Failed to archive the old hologram storage", ThrowableProperty.of(e));
        }
    }

    private List<LegacyHologramData> loadLegacyRecords() {
        try {
            List<LegacyHologramData> records = new JDB(legacyBasePath())
                    .getAll(LEGACY_COLLECTION, LegacyHologramData.class);
            return records == null ? List.of() : records;
        } catch (IOException e) {
            logger().error("Failed to read the old hologram storage", ThrowableProperty.of(e));
            return List.of();
        }
    }

    private String legacyBasePath() {
        return new File(IntegrationsManager.get().getPlugin().getDataFolder(), LEGACY_BASE_DIRECTORY).getPath();
    }

    private ExtendedFancyLogger logger() {
        return IntegrationsManager.get().getLogger();
    }

    private enum Result {
        MIGRATED,
        ALREADY_PRESENT,
        FAILED
    }
}
