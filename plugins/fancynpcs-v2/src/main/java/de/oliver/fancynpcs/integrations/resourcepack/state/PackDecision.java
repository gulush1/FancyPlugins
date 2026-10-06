package de.oliver.fancynpcs.integrations.resourcepack.state;

/**
 * Native verdict about whether a player can render the custom models, derived from the resource packs
 * offered to and reported by the client.
 */
public enum PackDecision {
    /**
     * Every considered resource pack is applied on the client.
     */
    LOADED,

    /**
     * A considered pack was offered and the client is still deciding, downloading or applying it.
     */
    PENDING,

    /**
     * The considered packs were declined, failed or discarded.
     */
    MISSING,

    /**
     * Nothing was offered yet and the offer grace period is still running.
     */
    AWAITING_OFFER,

    /**
     * Nothing was offered and the offer grace period has elapsed.
     */
    NO_OFFER
}
