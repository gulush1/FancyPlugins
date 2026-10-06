package de.oliver.fancynpcs.integrations.hologram;

import de.oliver.fancynpcs.integrations.hologram.dh.DhHologramCodec;
import de.oliver.fancynpcs.integrations.hologram.dh.DhLocations;
import de.oliver.fancynpcs.integrations.IntegrationsManager;
import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import eu.decentsoftware.holograms.display.DisplaySettings;
import eu.decentsoftware.holograms.display.TextDisplay;
import eu.decentsoftware.holograms.display.attribute.DisplayAttribute;
import eu.decentsoftware.holograms.display.attribute.definition.BillboardAttributeDefinition;
import eu.decentsoftware.holograms.display.attribute.definition.TextBackgroundColorAttributeDefinition;
import eu.decentsoftware.holograms.display.attribute.value.color.RgbaValue;
import eu.decentsoftware.holograms.display.attribute.value.display.BillboardConstraintsValue;
import eu.decentsoftware.holograms.platform.api.data.DecentLocation;
import eu.decentsoftware.holograms.platform.api.data.display.DisplayBillboardConstraints;
import org.bukkit.Location;

import java.util.List;
import java.util.Locale;

/**
 * Creates new hologram entries with the full set of DecentHolograms attributes filled in,
 * so every knob is present in the YAML file right after creation.
 */
public class NpcHologramFactory {

    private static final String BACKGROUND_DEFAULT = "default";
    private static final String BACKGROUND_TRANSPARENT = "transparent";

    private final DhHologramCodec codec;
    private final NpcHologramStorage storage;

    public NpcHologramFactory(DhHologramCodec codec, NpcHologramStorage storage) {
        this.codec = codec;
        this.storage = storage;
    }

    /**
     * @return a new entry for the given NPC, measured from the top of the NPC,
     * or null if the NPC's world is not loaded
     */
    public NpcHologramEntry create(Npc npc, List<String> lines, double yOffset) {
        return create(npc, lines, yOffset, HologramOffsetBase.HEAD);
    }

    /**
     * @return a new entry for the given NPC, or null if the NPC's world is not loaded
     */
    public NpcHologramEntry create(Npc npc, List<String> lines, double yOffset, HologramOffsetBase offsetBase) {
        String npcName = npc.getData().getName();
        Location base = npc.getData().getLocation();
        DecentLocation location = DhLocations.above(base, resolveTotalOffset(npc, yOffset, offsetBase));
        if (location == null) {
            return null;
        }

        TextDisplay display = new TextDisplay(storage.getDisplayName(npcName), location,
                createSettings(npc.getData().getVisibilityDistance()));
        codec.applyDefaultAttributes(display);
        applyConfiguredBillboard(display);
        applyConfiguredBackground(display);
        display.setLines(lines);

        return new NpcHologramEntry(npcName, codec.toConfigDisplay(display), yOffset, offsetBase);
    }

    /**
     * @return how far above the NPC's own location the hologram sits
     */
    public static double resolveTotalOffset(Npc npc, double yOffset, HologramOffsetBase offsetBase) {
        if (offsetBase != HologramOffsetBase.HEAD) {
            return yOffset;
        }

        return NpcHeights.resolve(npc) + yOffset;
    }

    /**
     * @return a copy of the given entry bound to another NPC, keeping lines, offset and all attributes
     */
    public NpcHologramEntry copyFor(NpcHologramEntry source, String targetNpcName) {
        TextDisplay sourceDisplay = codec.toTextDisplay(source.getDisplay());
        if (sourceDisplay == null) {
            return null;
        }

        TextDisplay copy = new TextDisplay(storage.getDisplayName(targetNpcName), sourceDisplay.getLocation(),
                sourceDisplay.getSettings().copy());
        copy.setAttributes(sourceDisplay.getAttributesMap());
        copy.setLines(sourceDisplay.getLines());

        return new NpcHologramEntry(targetNpcName, codec.toConfigDisplay(copy), source.getYOffset(),
                source.getOffsetBase());
    }

    private DisplaySettings createSettings(int visibilityDistance) {
        DisplaySettings settings = new DisplaySettings();
        settings.setDisplayRange(resolveDisplayRange(visibilityDistance));
        return settings;
    }

    /**
     * FancyNpcs stores any value below 0 as "use the global visibility distance from its own config".
     * Passing that sentinel straight to DecentHolograms would square it into a one block radius,
     * so the display would never be rendered for anyone.
     */
    public static double resolveDisplayRange(int visibilityDistance) {
        if (visibilityDistance > -1) {
            return visibilityDistance;
        }

        return FancyNpcsPlugin.get().getFancyNpcConfig().getVisibilityDistance();
    }

    private void applyConfiguredBillboard(TextDisplay display) {
        String configured = IntegrationsManager.get().getConfig().getHologramBillboard();
        DisplayBillboardConstraints constraint = DisplayBillboardConstraints.fromString(configured);
        if (constraint == null) {
            constraint = DisplayBillboardConstraints.VERTICAL;
        }

        display.setAttribute(BillboardAttributeDefinition.KEY,
                new DisplayAttribute<>(BillboardAttributeDefinition.KEY, new BillboardConstraintsValue(constraint)));
    }

    private void applyConfiguredBackground(TextDisplay display) {
        String configured = IntegrationsManager.get().getConfig().getHologramBackground();
        String normalized = configured == null ? "" : configured.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.equals(BACKGROUND_DEFAULT)) {
            return;
        }

        RgbaValue value = normalized.equals(BACKGROUND_TRANSPARENT)
                ? new RgbaValue(0, 0, 0, 0)
                : parseHexColor(normalized);
        if (value == null) {
            IntegrationsManager.get().getLogger()
                    .warn("Invalid hologram-background value in config: " + configured);
            return;
        }

        display.setAttribute(TextBackgroundColorAttributeDefinition.KEY,
                new DisplayAttribute<>(TextBackgroundColorAttributeDefinition.KEY, value));
    }

    private RgbaValue parseHexColor(String hex) {
        String stripped = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            if (stripped.length() == 6) {
                int rgb = Integer.parseInt(stripped, 16);
                return new RgbaValue((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            }
            if (stripped.length() == 8) {
                int alpha = Integer.parseInt(stripped.substring(0, 2), 16);
                int rgb = Integer.parseInt(stripped.substring(2), 16);
                return new RgbaValue((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
            }
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
