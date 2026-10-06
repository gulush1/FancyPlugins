package de.oliver.fancynpcs.integrations.hologram;

import de.oliver.fancynpcs.integrations.hologram.dh.DhHologramCodec;
import de.oliver.fancynpcs.integrations.IntegrationsManager;
import de.oliver.fancyanalytics.logger.properties.ThrowableProperty;
import eu.decentsoftware.holograms.display.config.dto.ConfigDisplay;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Stores every NPC hologram as its own YAML file in the plugin folder, using the DecentHolograms
 * display format so all attributes can be edited by hand.
 */
public class NpcHologramStorage {

    public static final String DISPLAY_NAME_PREFIX = "fnm_npc_";

    private static final String DIRECTORY = "holograms";
    private static final String FILE_EXTENSION = ".yml";
    private static final String NPC_KEY = "npc";
    private static final String Y_OFFSET_KEY = "y-offset";
    private static final String OFFSET_FROM_KEY = "offset-from";
    private static final Pattern UNSAFE_KEY_CHARS = Pattern.compile("[^a-z0-9._-]");

    private final DhHologramCodec codec;

    public NpcHologramStorage(DhHologramCodec codec) {
        this.codec = codec;
    }

    public Path getDirectory() {
        return new File(IntegrationsManager.get().getPlugin().getDataFolder(), DIRECTORY).toPath();
    }

    public String getDisplayName(String npcName) {
        return DISPLAY_NAME_PREFIX + toKey(npcName);
    }

    public List<NpcHologramEntry> loadAll() {
        Path directory = getDirectory();
        if (!Files.isDirectory(directory)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(this::isYamlFile).map(this::readSafely).filter(entry -> entry != null).toList();
        } catch (IOException e) {
            logError("Failed to list hologram files", e);
            return List.of();
        }
    }

    public NpcHologramEntry find(String npcName) {
        Path path = resolve(npcName);
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }

        return readSafely(path);
    }

    public void save(NpcHologramEntry entry) {
        Path path = resolve(entry.getNpcName());
        if (path == null) {
            return;
        }

        try {
            ensureDirectoryExists();
            YamlConfigurationLoader loader = codec.createLoader(path);
            ConfigurationNode node = loader.createNode();
            codec.writeDisplay(node, entry.getDisplay());
            node.node(NPC_KEY).set(entry.getNpcName());
            node.node(Y_OFFSET_KEY).set(entry.getYOffset());
            node.node(OFFSET_FROM_KEY).set(entry.getOffsetBase().name());
            loader.save(node);
        } catch (ConfigurateException e) {
            logError("Failed to save hologram of NPC " + entry.getNpcName(), e);
        }
    }

    public void delete(String npcName) {
        Path path = resolve(npcName);
        if (path == null) {
            return;
        }

        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            logError("Failed to delete hologram of NPC " + npcName, e);
        }
    }

    private NpcHologramEntry readSafely(Path path) {
        try {
            return read(path);
        } catch (Exception e) {
            logError("Failed to load hologram file " + path.getFileName(), e);
            return null;
        }
    }

    private NpcHologramEntry read(Path path) throws ConfigurateException {
        String key = toFileKey(path);
        ConfigurationNode node = codec.createLoader(path).load();
        ConfigDisplay display = codec.readDisplay(node, DISPLAY_NAME_PREFIX + key);
        if (display == null) {
            return null;
        }

        String npcName = node.node(NPC_KEY).getString(key);
        double yOffset = node.node(Y_OFFSET_KEY).getDouble(defaultYOffset());
        // Files written before offsets became head relative have no marker and must keep their position.
        HologramOffsetBase offsetBase = HologramOffsetBase.fromString(
                node.node(OFFSET_FROM_KEY).getString(), HologramOffsetBase.FEET);

        return new NpcHologramEntry(npcName, display, yOffset, offsetBase);
    }

    private Path resolve(String npcName) {
        String key = toKey(npcName);
        return key == null ? null : getDirectory().resolve(key + FILE_EXTENSION);
    }

    private void ensureDirectoryExists() throws ConfigurateException {
        try {
            Files.createDirectories(getDirectory());
        } catch (IOException e) {
            throw new ConfigurateException("Could not create hologram directory", e);
        }
    }

    private boolean isYamlFile(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(FILE_EXTENSION);
    }

    private String toFileKey(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.substring(0, fileName.length() - FILE_EXTENSION.length());
    }

    private String toKey(String npcName) {
        if (npcName == null || npcName.isBlank()) {
            return null;
        }

        return UNSAFE_KEY_CHARS.matcher(npcName.toLowerCase(Locale.ROOT)).replaceAll("_");
    }

    private double defaultYOffset() {
        return IntegrationsManager.get().getConfig().getHologramDefaultYOffset();
    }

    private void logError(String message, Throwable throwable) {
        IntegrationsManager.get().getLogger().error(message, ThrowableProperty.of(throwable));
    }
}
