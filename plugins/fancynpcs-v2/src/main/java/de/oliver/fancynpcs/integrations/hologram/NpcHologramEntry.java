package de.oliver.fancynpcs.integrations.hologram;

import eu.decentsoftware.holograms.display.config.dto.ConfigDisplay;
import eu.decentsoftware.holograms.display.config.dto.ConfigTextPage;

import java.util.ArrayList;
import java.util.List;

/**
 * One hologram file: the DecentHolograms display config plus the NPC binding this plugin adds on top.
 */
public class NpcHologramEntry {

    private final String npcName;
    private final ConfigDisplay display;
    private final HologramOffsetBase offsetBase;
    private double yOffset;

    public NpcHologramEntry(String npcName, ConfigDisplay display, double yOffset, HologramOffsetBase offsetBase) {
        this.npcName = npcName;
        this.display = display;
        this.yOffset = yOffset;
        this.offsetBase = offsetBase;
    }

    public HologramOffsetBase getOffsetBase() {
        return offsetBase;
    }

    public String getNpcName() {
        return npcName;
    }

    public ConfigDisplay getDisplay() {
        return display;
    }

    public double getYOffset() {
        return yOffset;
    }

    public void setYOffset(double yOffset) {
        this.yOffset = yOffset;
    }

    public List<String> getLines() {
        List<ConfigTextPage> pages = display.getPages();
        if (pages == null || pages.isEmpty() || pages.get(0).getLines() == null) {
            return new ArrayList<>();
        }

        return new ArrayList<>(pages.get(0).getLines());
    }

    public void setLines(List<String> lines) {
        ConfigTextPage page = new ConfigTextPage();
        page.setLines(new ArrayList<>(lines));

        List<ConfigTextPage> pages = new ArrayList<>();
        pages.add(page);
        display.setPages(pages);
    }
}
