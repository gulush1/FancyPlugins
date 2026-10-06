package de.oliver.fancynpcs.integrations;

import de.oliver.fancyanalytics.logger.ExtendedFancyLogger;
import de.oliver.fancyanalytics.logger.properties.StringProperty;
import de.oliver.fancyanalytics.logger.properties.ThrowableProperty;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/**
 * One-time move of the data of the former FancyNpcsModel addon into the FancyNpcs data folder.
 */
public class LegacyAddonMigrator {

    private static final String LEGACY_DIRECTORY = "FancyNpcsModel";
    private static final String MIGRATED_SUFFIX = ".migrated";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final String LEGACY_CONFIG = "config.yml";
    private static final String NEW_CONFIG = "integrations.yml";
    private static final String HOLOGRAMS_DIRECTORY = "holograms";
    private static final String DATA_DIRECTORY = "data";
    private static final String SETTINGS_PREFIX = "settings.";
    private static final List<String> KEPT_SECTIONS = List.of("settings.hologram.", "settings.model_fallback.");

    private final File fancyNpcsDataFolder;
    private final ExtendedFancyLogger logger;
    private int moved;
    private int failed;

    public LegacyAddonMigrator(File fancyNpcsDataFolder, ExtendedFancyLogger logger) {
        this.fancyNpcsDataFolder = fancyNpcsDataFolder;
        this.logger = logger;
    }

    public void migrate() {
        File parent = fancyNpcsDataFolder.getParentFile();
        if (parent == null) {
            return;
        }
        Path legacy = parent.toPath().resolve(LEGACY_DIRECTORY);
        if (!Files.isDirectory(legacy)) {
            return;
        }
        logger.info("Found data of the former FancyNpcsModel addon, migrating it into FancyNpcs...");
        moved = 0;
        failed = 0;
        migrateConfig(legacy.resolve(LEGACY_CONFIG));
        moveChildren(legacy.resolve(HOLOGRAMS_DIRECTORY), fancyNpcsDataFolder.toPath().resolve(HOLOGRAMS_DIRECTORY));
        moveChildren(legacy.resolve(DATA_DIRECTORY), fancyNpcsDataFolder.toPath().resolve(DATA_DIRECTORY));
        finish(legacy);
        logger.info("FancyNpcsModel migration finished, moved %d item(s).".formatted(moved));
    }

    private void migrateConfig(Path legacyConfig) {
        Path target = fancyNpcsDataFolder.toPath().resolve(NEW_CONFIG);
        if (!Files.isRegularFile(legacyConfig)) {
            return;
        }
        if (Files.exists(target)) {
            logger.info("Skipped migrating " + LEGACY_CONFIG + ", " + NEW_CONFIG + " already exists; the old file stays in the archived directory");
            return;
        }
        YamlConfiguration result = convertConfig(legacyConfig);
        if (result == null || result.getKeys(true).isEmpty()) {
            return;
        }
        try {
            writeAtomically(result, target);
            moved++;
            logger.info("Migrated " + LEGACY_CONFIG + " to " + NEW_CONFIG);
        } catch (IOException e) {
            failed++;
            logger.error("Failed to migrate the FancyNpcsModel config", ThrowableProperty.of(e));
        }
    }

    private YamlConfiguration convertConfig(Path legacyConfig) {
        YamlConfiguration source = new YamlConfiguration();
        try {
            source.load(legacyConfig.toFile());
        } catch (IOException | InvalidConfigurationException e) {
            logger.warn("Could not read the FancyNpcsModel config, skipping its migration", ThrowableProperty.of(e));
            return null;
        }
        YamlConfiguration result = new YamlConfiguration();
        for (String key : source.getKeys(true)) {
            if (!source.isConfigurationSection(key) && isKept(key)) {
                result.set(key.substring(SETTINGS_PREFIX.length()), source.get(key));
            }
        }
        return result;
    }

    private void writeAtomically(YamlConfiguration config, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
        config.save(temp.toFile());
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean isKept(String key) {
        return KEPT_SECTIONS.stream().anyMatch(key::startsWith);
    }

    private void moveChildren(Path sourceDirectory, Path targetDirectory) {
        if (!Files.isDirectory(sourceDirectory)) {
            return;
        }
        try (Stream<Path> children = Files.list(sourceDirectory)) {
            Files.createDirectories(targetDirectory);
            children.toList().forEach(child -> moveChild(child, targetDirectory.resolve(child.getFileName().toString())));
        } catch (IOException e) {
            failed++;
            logger.error("Failed to migrate " + sourceDirectory, ThrowableProperty.of(e));
        }
    }

    private void moveChild(Path source, Path target) {
        if (Files.exists(target)) {
            logger.warn("Skipped migration, target already exists", StringProperty.of("target", target.toString()));
            return;
        }
        try {
            Files.move(source, target);
            moved++;
            logger.info("Migrated " + source.getFileName() + " to " + target);
        } catch (IOException e) {
            failed++;
            logger.error("Failed to migrate " + source, ThrowableProperty.of(e));
        }
    }

    private void finish(Path legacy) {
        if (failed == 0) {
            archive(legacy);
            return;
        }
        logger.warn("Some FancyNpcsModel data could not be migrated and is still in plugins/FancyNpcsModel; it will be retried on the next start.");
    }

    private void archive(Path legacy) {
        Path archived = legacy.resolveSibling(LEGACY_DIRECTORY + MIGRATED_SUFFIX);
        if (Files.exists(archived)) {
            archived = legacy.resolveSibling(LEGACY_DIRECTORY + MIGRATED_SUFFIX + "-" + System.currentTimeMillis());
        }
        try {
            Files.move(legacy, archived);
            logger.info("Renamed the old addon directory to " + archived.getFileName());
        } catch (IOException e) {
            logger.error("Failed to rename the old addon directory", ThrowableProperty.of(e));
        }
    }

}
