package de.oliver.fancynpcs.integrations.hologram.legacy;

import java.util.List;

/**
 * Shape of the hologram records the plugin stored in JDB before holograms moved to DecentHolograms
 * styled YAML files. Only read during migration.
 */
public class LegacyHologramData {

    private String npcName;
    private String npcId;
    private List<String> lines;
    private double yOffset;

    public String getNpcName() {
        return npcName;
    }

    public String getNpcId() {
        return npcId;
    }

    public List<String> getLines() {
        return lines == null ? List.of() : lines;
    }

    public double getYOffset() {
        return yOffset;
    }
}
