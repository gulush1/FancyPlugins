package de.oliver.fancynpcs.integrations.resourcepack;

import de.oliver.fancynpcs.integrations.IntegrationsConfig;
import de.oliver.fancyanalytics.logger.ExtendedFancyLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable snapshot of the model-fallback settings, rebuilt on every config reload.
 */
public record ResourcePackSettings(
        @NotNull DetectionMode detection,
        @NotNull Set<UUID> packIds,
        boolean requireAll,
        boolean showModelWithoutOffer,
        long offerGraceMillis,
        boolean packetObserverEnabled,
        @NotNull String placeholder,
        @NotNull String expectedValue,
        long placeholderRefreshTicks,
        boolean strictStatusChain,
        long proxyAnswerWindowMillis,
        boolean verifyProxyAnswers,
        long rememberVerifiedMillis
) {

    private static final long MILLIS_PER_SECOND = 1000L;
    private static final long TICKS_PER_SECOND = 20L;
    private static final long MILLIS_PER_DAY = 86_400_000L;
    private static final String MATCH_ANY = "ANY";
    private static final String MATCH_ALL = "ALL";
    private static final String NO_OFFER_MODEL = "MODEL";
    private static final String NO_OFFER_FALLBACK = "FALLBACK";

    /**
     * How a player's possession of the resource pack is detected.
     */
    public enum DetectionMode {
        /** Native detection; PlaceholderAPI is only asked when the server never offered a pack to the player. */
        AUTO,
        /** Native detection only. */
        NATIVE,
        /** PlaceholderAPI only (legacy behaviour). */
        PLACEHOLDER
    }

    public static @NotNull ResourcePackSettings fromConfig(@NotNull IntegrationsConfig config,
                                                             @NotNull ExtendedFancyLogger logger) {
        return new ResourcePackSettings(
                parseDetection(config.getModelFallbackDetection(), logger),
                parsePackIds(config.getModelFallbackPackIds(), logger),
                parseRequireAll(config.getModelFallbackMatch(), logger),
                parseNoOffer(config.getModelFallbackNoOffer(), logger),
                Math.max(0, config.getModelFallbackOfferGraceSeconds()) * MILLIS_PER_SECOND,
                config.isModelFallbackPacketObserverEnabled(),
                Objects.requireNonNullElse(config.getModelFallbackPlaceholder(), ""),
                Objects.requireNonNullElse(config.getModelFallbackExpectedValue(), ""),
                Math.max(0, config.getModelFallbackPlaceholderRefreshSeconds()) * TICKS_PER_SECOND,
                config.isModelFallbackStrictStatusChain(),
                Math.max(0, config.getModelFallbackProxyAnswerWindowMs()),
                config.isModelFallbackVerifyProxyAnswers(),
                Math.max(0, config.getModelFallbackRememberVerifiedDays()) * MILLIS_PER_DAY
        );
    }

    private static @NotNull DetectionMode parseDetection(@Nullable String raw, @NotNull ExtendedFancyLogger logger) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return DetectionMode.AUTO;
        }
        for (DetectionMode mode : DetectionMode.values()) {
            if (mode.name().equalsIgnoreCase(trimmed)) {
                return mode;
            }
        }
        logger.warn("Unknown value '" + raw + "' for " + IntegrationsConfig.MODEL_FALLBACK_DETECTION_PATH + ", using AUTO");
        return DetectionMode.AUTO;
    }

    private static @NotNull Set<UUID> parsePackIds(@Nullable String raw, @NotNull ExtendedFancyLogger logger) {
        if (raw == null || raw.trim().isEmpty()) {
            return Set.of();
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            addPackId(part, ids, logger);
        }
        return Collections.unmodifiableSet(ids);
    }

    private static void addPackId(@NotNull String part, @NotNull Set<UUID> ids, @NotNull ExtendedFancyLogger logger) {
        String trimmed = part.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        try {
            ids.add(UUID.fromString(trimmed));
        } catch (IllegalArgumentException e) {
            logger.warn("Ignoring invalid resource pack id '" + trimmed + "' in " + IntegrationsConfig.MODEL_FALLBACK_PACK_IDS_PATH);
        }
    }

    private static boolean parseRequireAll(@Nullable String raw, @NotNull ExtendedFancyLogger logger) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.equalsIgnoreCase(MATCH_ANY)) {
            return false;
        }
        if (trimmed.equalsIgnoreCase(MATCH_ALL)) {
            return true;
        }
        logger.warn("Unknown value '" + raw + "' for " + IntegrationsConfig.MODEL_FALLBACK_MATCH_PATH + ", using ALL");
        return true;
    }

    private static boolean parseNoOffer(@Nullable String raw, @NotNull ExtendedFancyLogger logger) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.equalsIgnoreCase(NO_OFFER_MODEL)) {
            return true;
        }
        if (trimmed.equalsIgnoreCase(NO_OFFER_FALLBACK)) {
            return false;
        }
        logger.warn("Unknown value '" + raw + "' for " + IntegrationsConfig.MODEL_FALLBACK_NO_OFFER_PATH + ", using FALLBACK");
        return false;
    }
}
