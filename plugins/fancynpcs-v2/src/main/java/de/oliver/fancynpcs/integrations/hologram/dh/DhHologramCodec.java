package de.oliver.fancynpcs.integrations.hologram.dh;

import eu.decentsoftware.holograms.display.DisplayBase;
import eu.decentsoftware.holograms.display.TextDisplay;
import eu.decentsoftware.holograms.display.attribute.AttributeConfigMapper;
import eu.decentsoftware.holograms.display.attribute.DisplayAttribute;
import eu.decentsoftware.holograms.display.attribute.definition.AttributeDefinition;
import eu.decentsoftware.holograms.display.attribute.definition.AttributeDefinitionRegistry;
import eu.decentsoftware.holograms.display.attribute.value.AttributeValue;
import eu.decentsoftware.holograms.display.attribute.value.AttributeValueSerializer;
import eu.decentsoftware.holograms.display.attribute.value.AttributeValueTypeRegistry;
import eu.decentsoftware.holograms.display.attribute.value.color.ChromaValueType;
import eu.decentsoftware.holograms.display.attribute.value.color.RgbaValueType;
import eu.decentsoftware.holograms.display.attribute.value.display.BillboardConstraintsValueType;
import eu.decentsoftware.holograms.display.attribute.value.display.BrightnessValueType;
import eu.decentsoftware.holograms.display.attribute.value.display.ItemDisplayTypeValueType;
import eu.decentsoftware.holograms.display.attribute.value.display.TextAlignmentValueType;
import eu.decentsoftware.holograms.display.attribute.value.primitives.BooleanValueType;
import eu.decentsoftware.holograms.display.attribute.value.primitives.FloatValueType;
import eu.decentsoftware.holograms.display.attribute.value.primitives.IntegerValueType;
import eu.decentsoftware.holograms.display.attribute.value.primitives.Vector3fValueType;
import eu.decentsoftware.holograms.display.config.DisplayConfigMapper;
import eu.decentsoftware.holograms.display.config.dto.ConfigAttribute;
import eu.decentsoftware.holograms.display.config.dto.ConfigDisplay;
import eu.decentsoftware.holograms.display.config.serializer.ConfigAttributeSerializer;
import eu.decentsoftware.holograms.display.config.serializer.DecentLocationSerializer;
import eu.decentsoftware.holograms.display.config.serializer.DisplayBrightnessSerializer;
import eu.decentsoftware.holograms.display.config.serializer.DisplayColorSerializer;
import eu.decentsoftware.holograms.display.config.serializer.DisplayVector3fSerializer;
import eu.decentsoftware.holograms.platform.api.data.DecentColor;
import eu.decentsoftware.holograms.platform.api.data.DecentLocation;
import eu.decentsoftware.holograms.platform.api.data.DecentVector3f;
import eu.decentsoftware.holograms.platform.api.data.display.DisplayBrightness;
import eu.decentsoftware.holograms.platform.api.data.display.DisplayType;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.nio.file.Path;

/**
 * Reads and writes NPC hologram files in the exact format DecentHolograms uses for its own displays,
 * by reusing the mappers and serializers of the DH display module.
 * <p>
 * The material service of {@link DisplayConfigMapper} is only touched for item and block displays,
 * so {@code null} is safe here: NPC holograms are always {@link DisplayType#TEXT}.
 */
public class DhHologramCodec {

    private static final int YAML_INDENT = 2;

    private final AttributeDefinitionRegistry definitionRegistry;
    private final TypeSerializerCollection serializers;
    private final DisplayConfigMapper configMapper;

    public DhHologramCodec() {
        AttributeValueTypeRegistry valueTypeRegistry = createValueTypeRegistry();
        this.definitionRegistry = new AttributeDefinitionRegistry();
        this.serializers = createSerializers(new AttributeValueSerializer(valueTypeRegistry));
        this.configMapper = new DisplayConfigMapper(
                new AttributeConfigMapper(definitionRegistry, valueTypeRegistry), null);
    }

    public YamlConfigurationLoader createLoader(Path path) {
        return YamlConfigurationLoader.builder()
                .path(path)
                .indent(YAML_INDENT)
                .nodeStyle(NodeStyle.BLOCK)
                .defaultOptions(options -> options.serializers(builder -> builder.registerAll(serializers)))
                .build();
    }

    public ConfigDisplay readDisplay(ConfigurationNode node, String displayName) throws SerializationException {
        ConfigAttributeSerializer.setDisplayName(displayName);
        try {
            ConfigDisplay display = node.get(ConfigDisplay.class);
            if (display != null) {
                display.setName(displayName);
            }

            return display;
        } finally {
            ConfigAttributeSerializer.clearDisplayName();
        }
    }

    public void writeDisplay(ConfigurationNode node, ConfigDisplay display) throws SerializationException {
        ConfigAttributeSerializer.setDisplayName(display.getName());
        try {
            node.set(ConfigDisplay.class, display);
        } finally {
            ConfigAttributeSerializer.clearDisplayName();
        }
    }

    /**
     * @return the display described by the given config, or null if it is not a text display
     */
    public TextDisplay toTextDisplay(ConfigDisplay display) {
        if (display.getType() != DisplayType.TEXT) {
            return null;
        }

        DisplayBase mapped = configMapper.toDomain(display);
        return mapped instanceof TextDisplay textDisplay ? textDisplay : null;
    }

    public ConfigDisplay toConfigDisplay(DisplayBase display) {
        return configMapper.toDto(display);
    }

    /**
     * Fills in every attribute of the display's type that has a default value, so a freshly created
     * hologram file exposes all tweakable knobs instead of just the ones the plugin sets itself.
     */
    public void applyDefaultAttributes(DisplayBase display) {
        for (AttributeDefinition<?> definition : definitionRegistry.getDefinitionsByDisplayType(display.getType())) {
            applyDefaultAttribute(display, definition);
        }
    }

    private <T> void applyDefaultAttribute(DisplayBase display, AttributeDefinition<T> definition) {
        AttributeValue<T> defaultValue = definition.getDefaultValue();
        if (defaultValue == null || display.hasAttribute(definition.getKey())) {
            return;
        }

        display.setAttribute(definition.getKey(), new DisplayAttribute<>(definition.getKey(), defaultValue));
    }

    private AttributeValueTypeRegistry createValueTypeRegistry() {
        AttributeValueTypeRegistry registry = new AttributeValueTypeRegistry();
        registry.register(new FloatValueType());
        registry.register(new IntegerValueType());
        registry.register(new BooleanValueType());
        registry.register(new Vector3fValueType());
        registry.register(new RgbaValueType());
        registry.register(new ChromaValueType());
        registry.register(new BillboardConstraintsValueType());
        registry.register(new TextAlignmentValueType());
        registry.register(new BrightnessValueType());
        registry.register(new ItemDisplayTypeValueType());
        return registry;
    }

    private TypeSerializerCollection createSerializers(AttributeValueSerializer valueSerializer) {
        return TypeSerializerCollection.builder()
                .register(DecentLocation.class, new DecentLocationSerializer())
                .register(DecentVector3f.class, new DisplayVector3fSerializer())
                .register(DecentColor.class, new DisplayColorSerializer())
                .register(DisplayBrightness.class, new DisplayBrightnessSerializer())
                .register(ConfigAttribute.class, new ConfigAttributeSerializer(valueSerializer))
                .build();
    }
}
