package de.oliver.fancynpcs.integrations;

import com.fancyinnovations.config.Config;
import com.fancyinnovations.config.ConfigField;

/**
 * Configuration of the built-in BetterModel and DecentHolograms integrations (integrations.yml).
 */
public class IntegrationsConfig {

    public static final String HOLOGRAM_DEFAULT_Y_OFFSET_PATH = "hologram.default_y_offset";
    public static final String HOLOGRAM_BILLBOARD_PATH = "hologram.billboard";
    public static final String HOLOGRAM_BACKGROUND_PATH = "hologram.background";
    public static final String MODEL_FALLBACK_ENABLED_PATH = "model_fallback.enabled";
    public static final String MODEL_FALLBACK_PLACEHOLDER_PATH = "model_fallback.placeholder";
    public static final String MODEL_FALLBACK_EXPECTED_VALUE_PATH = "model_fallback.expected_value";
    public static final String MODEL_FALLBACK_DETECTION_PATH = "model_fallback.detection";
    public static final String MODEL_FALLBACK_PACK_IDS_PATH = "model_fallback.native.pack_ids";
    public static final String MODEL_FALLBACK_MATCH_PATH = "model_fallback.native.match";
    public static final String MODEL_FALLBACK_NO_OFFER_PATH = "model_fallback.native.no_offer";
    public static final String MODEL_FALLBACK_OFFER_GRACE_SECONDS_PATH = "model_fallback.native.offer_grace_seconds";
    public static final String MODEL_FALLBACK_PACKET_OBSERVER_PATH = "model_fallback.native.packet_observer";
    public static final String MODEL_FALLBACK_STRICT_STATUS_CHAIN_PATH = "model_fallback.native.strict_status_chain";
    public static final String MODEL_FALLBACK_PROXY_ANSWER_WINDOW_MS_PATH = "model_fallback.native.proxy_answer_window_ms";
    public static final String MODEL_FALLBACK_VERIFY_PROXY_ANSWERS_PATH = "model_fallback.native.verify_proxy_answers";
    public static final String MODEL_FALLBACK_REMEMBER_VERIFIED_DAYS_PATH = "model_fallback.native.remember_verified_days";
    public static final String MODEL_FALLBACK_PLACEHOLDER_REFRESH_SECONDS_PATH = "model_fallback.placeholder_refresh_seconds";

    private static final String CONFIG_FILE_PATH = "plugins/FancyNpcs/integrations.yml";

    private Config config;

    public void init() {
        config = new Config(IntegrationsManager.get().getLogger(), CONFIG_FILE_PATH);

        config.addField(new ConfigField<>(
                HOLOGRAM_DEFAULT_Y_OFFSET_PATH,
                "Default Y offset of new NPC holograms, measured from the top of the NPC "
                        + "(the height of its custom model, or the entity height).",
                false,
                0.3,
                false,
                Double.class
        ));

        config.addField(new ConfigField<>(
                HOLOGRAM_BILLBOARD_PATH,
                "Billboard mode for newly created NPC holograms (FIXED, VERTICAL, HORIZONTAL, CENTER). "
                        + "Existing holograms are edited in their own file under plugins/FancyNpcs/holograms.",
                false,
                "VERTICAL",
                false,
                String.class
        ));

        config.addField(new ConfigField<>(
                HOLOGRAM_BACKGROUND_PATH,
                "Background for newly created NPC holograms: 'transparent', 'default' (vanilla plate) "
                        + "or hex color '#RRGGBB' / '#AARRGGBB'.",
                false,
                "transparent",
                false,
                String.class
        ));

        config.addField(new ConfigField<>(
                MODEL_FALLBACK_ENABLED_PATH,
                "Whether to hide custom models from players without the resource pack (they will see the vanilla NPC instead).",
                false,
                false,
                false,
                Boolean.class
        ));

        config.addField(new ConfigField<>(
                MODEL_FALLBACK_DETECTION_PATH,
                "How to detect whether a player has the resource pack: AUTO (native detection; PlaceholderAPI is only asked when the server never offered a pack to the player), NATIVE (native detection only) or PLACEHOLDER (PlaceholderAPI only, legacy behaviour).",
                false,
                "AUTO",
                false,
                String.class
        ));

        config.addField(new ConfigField<>(
                MODEL_FALLBACK_PACK_IDS_PATH,
                "Comma-separated UUIDs of the resource packs that contain the models. Empty = every resource pack the server sends. /fancynpcs resourcepack <player> shows the ids.",
                false,
                "",
                false,
                String.class
        ));

        config.addField(new ConfigField<>(
                MODEL_FALLBACK_MATCH_PATH,
                "ALL = every considered resource pack must be loaded, ANY = one loaded pack is enough.",
                false,
                "ALL",
                false,
                String.class
        ));

        config.addField(new ConfigField<>(
                MODEL_FALLBACK_NO_OFFER_PATH,
                "What players see when the server never offered them a resource pack (e.g. the pack is sent by a proxy or installed manually): FALLBACK (vanilla NPC) or MODEL.",
                false,
                "FALLBACK",
                false,
                String.class
        ));

        config.addField(new ConfigField<>(
                MODEL_FALLBACK_OFFER_GRACE_SECONDS_PATH,
                "Seconds to wait after joining for the first resource pack offer before 'no_offer' applies. The vanilla NPC is shown while waiting.",
                false,
                10,
                false,
                Integer.class
        ));

        config.addField(new ConfigField<>(
                MODEL_FALLBACK_PACKET_OBSERVER_PATH,
                "Observe resource pack packets on the network layer: sees every pack offer, removal and answer in all connection phases (including the configuration phase on 1.21.7+). Disable only if it conflicts with another plugin; Bukkit events are used then.",
                false,
                true,
                false,
                Boolean.class
        ));

        config.addField(new ConfigField<>(
                MODEL_FALLBACK_STRICT_STATUS_CHAIN_PATH,
                "Only trust a 'successfully loaded' answer that follows the accepted/downloaded answers honest clients always send first. Clients with server resource packs disabled may answer 'loaded' without having the pack; they then see the vanilla NPC.",
                false,
                true,
                false,
                Boolean.class
        ));

        config.addField(new ConfigField<>(
                MODEL_FALLBACK_PROXY_ANSWER_WINDOW_MS_PATH,
                "A 'loaded' answer arriving faster than this after 'accepted' did not come from the client (no resource reload happened) but from a proxy that remembers the pack as applied (Velocity). Such answers are verified before they are trusted. 0 = trust them.",
                false,
                50,
                false,
                Integer.class
        ));

        config.addField(new ConfigField<>(
                MODEL_FALLBACK_VERIFY_PROXY_ANSWERS_PATH,
                "Verify proxy answers once by removing and re-sending the pack so the client itself answers. Players verified before are trusted right away; an honest player sees one pack reload the first time.",
                false,
                true,
                false,
                Boolean.class
        ));

        config.addField(new ConfigField<>(
                MODEL_FALLBACK_REMEMBER_VERIFIED_DAYS_PATH,
                "How many days an honest 'loaded' answer is remembered per player and pack (by sha1), so later proxy answers are trusted without re-sending the pack.",
                false,
                30,
                false,
                Integer.class
        ));

        config.addField(new ConfigField<>(
                MODEL_FALLBACK_PLACEHOLDER_PATH,
                "PlaceholderAPI placeholder that indicates whether a player has the resource pack. Used by detection PLACEHOLDER, and by AUTO when the server never offered a pack to the player.",
                false,
                "%rp_has%",
                false,
                String.class
        ));

        config.addField(new ConfigField<>(
                MODEL_FALLBACK_EXPECTED_VALUE_PATH,
                "Placeholder value that means the player has the resource pack.",
                false,
                "true",
                false,
                String.class
        ));

        config.addField(new ConfigField<>(
                MODEL_FALLBACK_PLACEHOLDER_REFRESH_SECONDS_PATH,
                "How often (in seconds) the placeholder is re-checked for players whose state depends on it. 0 = only on join and on resource pack events.",
                false,
                5,
                false,
                Integer.class
        ));

    }

    public void reload() {
        config.reload();
    }

    public Config getConfig() {
        return config;
    }

    public double getHologramDefaultYOffset() {
        return config.get(HOLOGRAM_DEFAULT_Y_OFFSET_PATH);
    }

    public String getHologramBillboard() {
        return config.get(HOLOGRAM_BILLBOARD_PATH);
    }

    public String getHologramBackground() {
        return config.get(HOLOGRAM_BACKGROUND_PATH);
    }

    public boolean isModelFallbackEnabled() {
        return config.get(MODEL_FALLBACK_ENABLED_PATH);
    }

    public String getModelFallbackPlaceholder() {
        return config.get(MODEL_FALLBACK_PLACEHOLDER_PATH);
    }

    public String getModelFallbackExpectedValue() {
        return config.get(MODEL_FALLBACK_EXPECTED_VALUE_PATH);
    }

    public String getModelFallbackDetection() {
        return config.get(MODEL_FALLBACK_DETECTION_PATH);
    }

    public String getModelFallbackPackIds() {
        return config.get(MODEL_FALLBACK_PACK_IDS_PATH);
    }

    public String getModelFallbackMatch() {
        return config.get(MODEL_FALLBACK_MATCH_PATH);
    }

    public String getModelFallbackNoOffer() {
        return config.get(MODEL_FALLBACK_NO_OFFER_PATH);
    }

    public int getModelFallbackOfferGraceSeconds() {
        return config.get(MODEL_FALLBACK_OFFER_GRACE_SECONDS_PATH);
    }

    public boolean isModelFallbackPacketObserverEnabled() {
        return config.get(MODEL_FALLBACK_PACKET_OBSERVER_PATH);
    }

    public boolean isModelFallbackStrictStatusChain() {
        return config.get(MODEL_FALLBACK_STRICT_STATUS_CHAIN_PATH);
    }

    public int getModelFallbackProxyAnswerWindowMs() {
        return config.get(MODEL_FALLBACK_PROXY_ANSWER_WINDOW_MS_PATH);
    }

    public boolean isModelFallbackVerifyProxyAnswers() {
        return config.get(MODEL_FALLBACK_VERIFY_PROXY_ANSWERS_PATH);
    }

    public int getModelFallbackRememberVerifiedDays() {
        return config.get(MODEL_FALLBACK_REMEMBER_VERIFIED_DAYS_PATH);
    }

    public int getModelFallbackPlaceholderRefreshSeconds() {
        return config.get(MODEL_FALLBACK_PLACEHOLDER_REFRESH_SECONDS_PATH);
    }

}
