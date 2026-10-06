package de.oliver.fancynpcs.integrations;

import com.fancyinnovations.config.Config;
import com.fancyinnovations.config.ConfigField;
import de.oliver.fancynpcs.integrations.hover.EasingCurve;

import java.io.File;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    public static final String HOVER_SCALE_ENABLED_PATH = "hover_scale.enabled";
    public static final String HOVER_SCALE_FACTOR_PATH = "hover_scale.factor";
    public static final String HOVER_SCALE_DURATION_TICKS_PATH = "hover_scale.duration_ticks";
    public static final String HOVER_SCALE_MAX_DISTANCE_PATH = "hover_scale.max_distance";
    public static final String HOVER_SCALE_GROW_CURVE_PATH = "hover_scale.grow_curve";
    public static final String HOVER_SCALE_SHRINK_CURVE_PATH = "hover_scale.shrink_curve";

    private static final double DEFAULT_HOVER_SCALE_FACTOR = 1.15;
    private static final int DEFAULT_HOVER_SCALE_DURATION_TICKS = 6;
    private static final double DEFAULT_HOVER_SCALE_MAX_DISTANCE = 6.0;
    private static final EasingCurve DEFAULT_HOVER_SCALE_CURVE = EasingCurve.EASE_OUT_CUBIC;

    private static final String CONFIG_FILE_NAME = "integrations.yml";

    private final Set<String> warnedCurves = ConcurrentHashMap.newKeySet();

    private Config config;

    public void init() {
        IntegrationsManager manager = IntegrationsManager.get();
        String path = new File(manager.getPlugin().getDataFolder(), CONFIG_FILE_NAME).getPath();
        config = new Config(manager.getLogger(), path);
        addHologramFields();
        addModelFallbackFields();
        addHoverFields();
    }

    private void addHologramFields() {
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

    }

    private void addModelFallbackFields() {
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

    private void addHoverFields() {
        config.addField(new ConfigField<>(
                HOVER_SCALE_ENABLED_PATH,
                "Whether NPCs smoothly grow for the player that aims at them (per-player; NPCs with a custom model are skipped).",
                false,
                true,
                false,
                Boolean.class
        ));

        config.addField(new ConfigField<>(
                HOVER_SCALE_FACTOR_PATH,
                "Scale multiplier while an NPC is aimed at. Per-NPC override: /npc attribute <npc> set hover_scale <factor|off>.",
                false,
                DEFAULT_HOVER_SCALE_FACTOR,
                false,
                Double.class
        ));

        config.addField(new ConfigField<>(
                HOVER_SCALE_DURATION_TICKS_PATH,
                "Length of the grow/shrink animation in ticks.",
                false,
                DEFAULT_HOVER_SCALE_DURATION_TICKS,
                false,
                Integer.class
        ));

        config.addField(new ConfigField<>(
                HOVER_SCALE_MAX_DISTANCE_PATH,
                "Maximum distance in blocks at which aiming at an NPC counts.",
                false,
                DEFAULT_HOVER_SCALE_MAX_DISTANCE,
                false,
                Double.class
        ));

        config.addField(new ConfigField<>(
                HOVER_SCALE_GROW_CURVE_PATH,
                "Easing curve while an NPC grows: linear, ease_in/out/in_out_{sine,quad,cubic,quart,expo,back}, ease_out_elastic, ease_out_bounce. Per-NPC override: /npc attribute <npc> set hover_curve <curve>.",
                false,
                "ease_out_cubic",
                false,
                String.class
        ));

        config.addField(new ConfigField<>(
                HOVER_SCALE_SHRINK_CURVE_PATH,
                "Easing curve while an NPC shrinks back.",
                false,
                "ease_out_cubic",
                false,
                String.class
        ));
    }

    public void reload() {
        warnedCurves.clear();
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

    public boolean isHoverScaleEnabled() {
        return config.get(HOVER_SCALE_ENABLED_PATH);
    }

    public double getHoverScaleFactor() {
        double factor = config.get(HOVER_SCALE_FACTOR_PATH);
        return factor > 0 ? factor : DEFAULT_HOVER_SCALE_FACTOR;
    }

    public int getHoverScaleDurationTicks() {
        int ticks = config.get(HOVER_SCALE_DURATION_TICKS_PATH);
        return ticks >= 1 ? ticks : DEFAULT_HOVER_SCALE_DURATION_TICKS;
    }

    public double getHoverScaleMaxDistance() {
        double distance = config.get(HOVER_SCALE_MAX_DISTANCE_PATH);
        return distance > 0 ? distance : DEFAULT_HOVER_SCALE_MAX_DISTANCE;
    }

    public EasingCurve getHoverScaleGrowCurve() {
        return resolveCurve(HOVER_SCALE_GROW_CURVE_PATH);
    }

    public EasingCurve getHoverScaleShrinkCurve() {
        return resolveCurve(HOVER_SCALE_SHRINK_CURVE_PATH);
    }

    private EasingCurve resolveCurve(String path) {
        String raw = config.get(path);
        Optional<EasingCurve> curve = EasingCurve.byName(raw);
        if (curve.isEmpty()) {
            warnInvalidCurveOnce(path, raw);
        }
        return curve.orElse(DEFAULT_HOVER_SCALE_CURVE);
    }

    private void warnInvalidCurveOnce(String path, String raw) {
        if (warnedCurves.add(path + "=" + raw)) {
            IntegrationsManager.get().getLogger().warn("Unknown easing curve '" + raw + "' in " + path + ", using " + DEFAULT_HOVER_SCALE_CURVE.name().toLowerCase(Locale.ROOT));
        }
    }

}
