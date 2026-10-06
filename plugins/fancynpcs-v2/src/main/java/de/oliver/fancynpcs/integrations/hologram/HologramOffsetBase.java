package de.oliver.fancynpcs.integrations.hologram;

import java.util.Locale;

/**
 * Where the y offset of a hologram is measured from.
 */
public enum HologramOffsetBase {

    /**
     * From the NPC's feet, the location FancyNpcs stores. Used by holograms created before offsets
     * became relative to the NPC's head, so their position never changes.
     */
    FEET,

    /**
     * From the top of the NPC: the rendered height of its custom model, or the entity height.
     */
    HEAD;

    public static HologramOffsetBase fromString(String value, HologramOffsetBase fallback) {
        if (value == null) {
            return fallback;
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
