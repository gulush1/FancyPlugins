package de.oliver.fancynpcs.integrations.resourcepack.state;

import org.jetbrains.annotations.Nullable;

/**
 * Status of a single server resource pack on the client.
 */
public enum PackStatus {
    /**
     * The server pushed the pack, the client has not answered yet — plugin-internal.
     */
    OFFERED,
    /**
     * The client accepted the pack.
     */
    ACCEPTED,
    /**
     * The client downloaded the pack.
     */
    DOWNLOADED,
    /**
     * The pack was successfully loaded on the client.
     */
    SUCCESSFULLY_LOADED,
    /**
     * The client declined the pack.
     */
    DECLINED,
    /**
     * The client failed to download the pack.
     */
    FAILED_DOWNLOAD,
    /**
     * The pack URL is invalid.
     */
    INVALID_URL,
    /**
     * The client failed to reload the pack.
     */
    FAILED_RELOAD,
    /**
     * The client discarded the pack.
     */
    DISCARDED,
    /**
     * The client claimed SUCCESSFULLY_LOADED without the ACCEPTED/DOWNLOADED answers honest clients
     * always send first (e.g. server resource packs disabled on a 26.x client) — counts as not loaded.
     */
    UNVERIFIED_LOAD,
    /**
     * A proxy (e.g. Velocity) answered "loaded" on the client's behalf because it believes the pack is
     * already applied; not confirmed by the client yet — pending until verified.
     */
    CLAIMED_LOAD;

    /**
     * Mirrors the ordinal order of vanilla {@code ServerboundResourcePackPacket.Action}
     * (stable 1.20 → 26.x; only the first four exist before 1.20.3).
     */
    private static final PackStatus[] CLIENT_ORDER = {
        SUCCESSFULLY_LOADED,
        DECLINED,
        FAILED_DOWNLOAD,
        ACCEPTED,
        DOWNLOADED,
        INVALID_URL,
        FAILED_RELOAD,
        DISCARDED
    };

    /**
     * @return true if this status is SUCCESSFULLY_LOADED.
     */
    public boolean isLoaded() {
        return this == SUCCESSFULLY_LOADED;
    }

    /**
     * @return true if this status is OFFERED, ACCEPTED, DOWNLOADED, or CLAIMED_LOAD (client is still
     * processing, or a proxy claim is still being resolved).
     */
    public boolean isPending() {
        return this == OFFERED || this == ACCEPTED || this == DOWNLOADED || this == CLAIMED_LOAD;
    }

    /**
     * @return true if the pack is neither loaded nor pending (a failure or discarded state).
     */
    public boolean isFailure() {
        return !isLoaded() && !isPending();
    }

    /**
     * Maps Bukkit's {@code PlayerResourcePackStatusEvent.Status} by name so constants missing on older servers
     * are never referenced.
     *
     * @param name the constant name to lookup (compared case-insensitively after trim).
     * @return the matching PackStatus, or null if name is null or no constant matches.
     */
    public static @Nullable PackStatus fromName(@Nullable String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        for (PackStatus status : values()) {
            if (status.name().equalsIgnoreCase(trimmed)) {
                return status;
            }
        }
        return null;
    }

    /**
     * Looks up a PackStatus by its ordinal in the vanilla {@code ServerboundResourcePackPacket.Action} order.
     *
     * @param ordinal the action ordinal (0-7 for vanilla order).
     * @return the matching PackStatus, or null if ordinal is out of range.
     */
    public static @Nullable PackStatus fromClientOrdinal(int ordinal) {
        if (ordinal >= 0 && ordinal < CLIENT_ORDER.length) {
            return CLIENT_ORDER[ordinal];
        }
        return null;
    }
}
