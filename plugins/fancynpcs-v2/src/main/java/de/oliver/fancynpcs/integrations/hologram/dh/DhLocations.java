package de.oliver.fancynpcs.integrations.hologram.dh;

import eu.decentsoftware.holograms.display.config.dto.ConfigDecentLocation;
import eu.decentsoftware.holograms.platform.api.data.DecentLocation;
import org.bukkit.Location;
import org.bukkit.World;

public final class DhLocations {

    private DhLocations() {
    }

    /**
     * @return the hologram location above the given base location, or null if its world is not loaded
     */
    public static DecentLocation above(Location base, double yOffset) {
        World world = base.getWorld();
        if (world == null) {
            return null;
        }

        return new DecentLocation(world.getName(), base.getX(), base.getY() + yOffset, base.getZ(), 0f, 0f);
    }

    public static ConfigDecentLocation toConfig(DecentLocation location) {
        ConfigDecentLocation config = new ConfigDecentLocation();
        config.setWorld(location.getWorldName());
        config.setX(location.getX());
        config.setY(location.getY());
        config.setZ(location.getZ());
        config.setYaw(location.getYaw());
        config.setPitch(location.getPitch());
        return config;
    }
}
