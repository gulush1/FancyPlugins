package de.oliver.fancynpcs.integrations.resourcepack;

import de.oliver.fancynpcs.integrations.IntegrationsConfig;
import de.oliver.fancynpcs.integrations.bettermodel.CustomModelAttribute;
import de.oliver.fancynpcs.integrations.IntegrationsManager;
import de.oliver.fancynpcs.integrations.resourcepack.ResourcePackSettings.DetectionMode;
import de.oliver.fancynpcs.integrations.resourcepack.network.PackPacketObserver;
import de.oliver.fancynpcs.integrations.resourcepack.state.PackDecision;
import de.oliver.fancynpcs.integrations.resourcepack.state.PackRules;
import de.oliver.fancynpcs.integrations.resourcepack.state.PackSessionRegistry;
import de.oliver.fancynpcs.integrations.resourcepack.state.PackStatus;
import de.oliver.fancynpcs.integrations.resourcepack.state.PlayerPackSession;
import de.oliver.fancynpcs.integrations.resourcepack.state.PlayerPackSession.ClaimedPack;
import de.oliver.fancyanalytics.logger.ExtendedFancyLogger;
import de.oliver.fancynpcs.FancyNpcs;
import de.oliver.fancynpcs.api.Npc;
import io.netty.channel.Channel;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns native resource-pack detection for the model fallback: the session registry, the optional
 * packet observer and the PlaceholderAPI source. Turns them into a single "can this player see
 * custom models" decision and applies model visibility only when that decision actually changes,
 * since BetterModel's spawn call is an unconditional re-spawn and not cheap.
 */
public final class ResourcePackService {

    private static final long IMMEDIATE_DELAY_TICKS = 1L;
    private static final long SESSION_CHANGE_DELAY_TICKS = 2L;
    private static final long NPC_SPAWN_RECHECK_DELAY_TICKS = 1L;
    private static final long PLACEHOLDER_CONFIRM_DELAY_TICKS = 20L;
    private static final long GRACE_EXTRA_TICKS = 2L;
    private static final long MILLIS_PER_TICK = 50L;
    private static final int NO_OFFER_HINT_THRESHOLD = 3;
    private static final String VERIFIED_PACKS_FILE = "data/verified-packs.yml";

    private final FancyNpcs plugin;
    private final ExtendedFancyLogger logger;
    private final PackSessionRegistry registry;
    private final PlaceholderPackSource placeholderSource;
    private final PackVerdictMemory verdictMemory;
    private final ModelVisibilityApplier applier = new ModelVisibilityApplier();
    private final Map<UUID, Boolean> appliedDecisions = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> refreshTasks = new ConcurrentHashMap<>();
    private final Set<UUID> pendingReevaluations = ConcurrentHashMap.newKeySet();
    private final Set<UUID> noOfferPlayers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean noOfferHintLogged = new AtomicBoolean();
    private volatile ResourcePackSettings settings;
    private volatile @Nullable PackPacketObserver observer;

    public ResourcePackService(@NotNull FancyNpcs plugin) {
        this.plugin = plugin;
        this.logger = plugin.getFancyLogger();
        this.settings = ResourcePackSettings.fromConfig(IntegrationsManager.get().getConfig(), logger);
        this.verdictMemory = new PackVerdictMemory(plugin, new File(plugin.getDataFolder(), VERIFIED_PACKS_FILE), logger);
        this.registry = new PackSessionRegistry(this::onSessionChanged, System::currentTimeMillis,
                () -> new PackRules(settings.strictStatusChain(), settings.proxyAnswerWindowMillis()), this::onStatusTraced);
        this.placeholderSource = new PlaceholderPackSource(this::settings);
    }

    public void enable() {
        verdictMemory.load(settings.rememberVerifiedMillis());
        updateObserver();
        Bukkit.getOnlinePlayers().forEach(this::handleJoin);
    }

    public void disable() {
        verdictMemory.saveNow();
        PackPacketObserver current = observer;
        if (current != null) {
            current.uninstall();
            observer = null;
        }
        refreshTasks.values().forEach(ScheduledTask::cancel);
        refreshTasks.clear();
        registry.clear();
        placeholderSource.clear();
        appliedDecisions.clear();
        pendingReevaluations.clear();
        noOfferPlayers.clear();
    }

    public void reload() {
        settings = ResourcePackSettings.fromConfig(IntegrationsManager.get().getConfig(), logger);
        updateObserver();
        placeholderSource.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            restartPlaceholderRefresh(player);
            scheduleReevaluation(player, IMMEDIATE_DELAY_TICKS);
        }
    }

    private void updateObserver() {
        boolean wanted = IntegrationsManager.get().getConfig().isModelFallbackEnabled()
                && settings.packetObserverEnabled()
                && settings.detection() != DetectionMode.PLACEHOLDER;
        if (wanted && observer == null) {
            PackPacketObserver candidate = new PackPacketObserver(registry, logger);
            if (candidate.install()) {
                observer = candidate;
            }
        } else if (!wanted && observer != null) {
            observer.uninstall();
            observer = null;
        }
    }

    public @NotNull ResourcePackSettings settings() {
        return settings;
    }

    public @Nullable PlayerPackSession session(@NotNull Player player) {
        return registry.get(player.getUniqueId());
    }

    public boolean isObserverInstalled() {
        PackPacketObserver current = observer;
        return current != null && current.isInstalled();
    }

    public boolean isFallbackActive() {
        return IntegrationsManager.get().getConfig().isModelFallbackEnabled()
                && (settings.detection() != DetectionMode.PLACEHOLDER || placeholderSource.isAvailable());
    }

    public @NotNull PackDecision nativeDecision(@NotNull Player player) {
        ResourcePackSettings s = settings;
        return registry.attach(player).evaluate(s.packIds(), s.requireAll(), System.currentTimeMillis(), s.offerGraceMillis());
    }

    public boolean hasResourcePack(@NotNull Player player) {
        if (!isFallbackActive()) {
            return true;
        }
        Boolean applied = appliedDecisions.get(player.getUniqueId());
        return applied != null ? applied : quickDecision(player);
    }

    private boolean quickDecision(@NotNull Player player) {
        if (settings.detection() == DetectionMode.PLACEHOLDER) {
            return false;
        }
        PackDecision d = nativeDecision(player);
        return d == PackDecision.LOADED || (d == PackDecision.NO_OFFER && settings.showModelWithoutOffer());
    }

    public boolean resolve(@NotNull Player player) {
        return resolveFresh(player, false);
    }

    private boolean resolveFresh(@NotNull Player player, boolean forcePlaceholder) {
        if (!isFallbackActive()) {
            return true;
        }
        if (settings.detection() == DetectionMode.PLACEHOLDER) {
            return Boolean.TRUE.equals(placeholderSource.query(player, forcePlaceholder));
        }
        PackDecision d = nativeDecision(player);
        if (d != PackDecision.NO_OFFER) {
            return d == PackDecision.LOADED;
        }
        return resolveWithoutOffer(player, forcePlaceholder);
    }

    private boolean resolveWithoutOffer(@NotNull Player player, boolean forcePlaceholder) {
        if (settings.detection() == DetectionMode.AUTO) {
            Boolean v = placeholderSource.query(player, forcePlaceholder);
            if (v != null) {
                return v;
            }
        }
        return settings.showModelWithoutOffer();
    }

    private boolean isPlaceholderDriven(@NotNull Player player) {
        if (settings.detection() == DetectionMode.PLACEHOLDER) {
            return true;
        }
        if (settings.detection() == DetectionMode.AUTO) {
            return placeholderSource.isAvailable() && nativeDecision(player) == PackDecision.NO_OFFER;
        }
        return false;
    }

    public void handleStatusEvent(@NotNull Player player, @NotNull UUID packId, @NotNull PackStatus status) {
        registry.recordStatus(player, packId, status);
        if (settings.detection() != DetectionMode.NATIVE) {
            placeholderSource.forget(player.getUniqueId());
        }
    }

    public void handleJoin(@NotNull Player player) {
        registry.attach(player);
        reconcileLastStatus(player);
        restartPlaceholderRefresh(player);
        scheduleReevaluation(player, IMMEDIATE_DELAY_TICKS);
        scheduleGraceReevaluation(player);
    }

    public void handleQuit(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        registry.detach(player);
        appliedDecisions.remove(uuid);
        pendingReevaluations.remove(uuid);
        noOfferPlayers.remove(uuid);
        placeholderSource.forget(uuid);
        ScheduledTask task = refreshTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        if (isFallbackActive()) {
            applier.unmarkEverywhere(player);
        }
    }

    public void handleNpcSpawn(@NotNull Npc npc, @NotNull Player player) {
        if (!isFallbackActive() || !CustomModelAttribute.hasAttribute(npc)) {
            return;
        }
        CustomModelAttribute.preAuthorize(npc, player, hasResourcePack(player));
        player.getScheduler().runDelayed(plugin, task -> applySpawnedNpc(npc, player), null, NPC_SPAWN_RECHECK_DELAY_TICKS);
    }

    private void applySpawnedNpc(@NotNull Npc npc, @NotNull Player player) {
        if (!isFallbackActive()) {
            return;
        }
        boolean decision = resolve(player);
        applier.applyTo(npc, player, decision);
        if (!Objects.equals(appliedDecisions.get(player.getUniqueId()), decision)) {
            scheduleReevaluation(player, IMMEDIATE_DELAY_TICKS);
        }
    }

    /**
     * On 1.21.7+ the configuration phase fires no Bukkit event; Paper only keeps the last status.
     */
    private void reconcileLastStatus(@NotNull Player player) {
        PlayerPackSession s = registry.attach(player);
        if (s.connectionToken() != null || !s.isEmpty()) {
            return;
        }
        PackStatus last = lastKnownStatus(player);
        if (last != null) {
            registry.recordTrustedStatus(player, PackSessionRegistry.UNIDENTIFIED_PACK_ID, last);
        }
    }

    private @Nullable PackStatus lastKnownStatus(@NotNull Player player) {
        try {
            var status = player.getResourcePackStatus();
            return status == null ? null : PackStatus.fromName(status.name());
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    private void scheduleGraceReevaluation(@NotNull Player player) {
        PlayerPackSession s = registry.get(player.getUniqueId());
        if (s == null) {
            return;
        }
        long remaining = settings.offerGraceMillis() - (System.currentTimeMillis() - s.startedAtMillis());
        if (remaining <= 0) {
            return;
        }
        scheduleReevaluation(player, remaining / MILLIS_PER_TICK + GRACE_EXTRA_TICKS);
    }

    private void scheduleReevaluation(@NotNull Player player, long delayTicks) {
        player.getScheduler().runDelayed(plugin, task -> reevaluateNow(player, false), null, Math.max(1L, delayTicks));
    }

    private void onSessionChanged(@NotNull UUID playerId) {
        PlayerPackSession s = registry.get(playerId);
        Player p = s == null ? null : s.boundPlayer();
        if (p == null) {
            return;
        }
        requestReevaluation(p);
    }

    private void requestReevaluation(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (!pendingReevaluations.add(uuid)) {
            return;
        }
        ScheduledTask t = player.getScheduler().runDelayed(plugin, task -> completeReevaluation(player),
                () -> pendingReevaluations.remove(uuid), SESSION_CHANGE_DELAY_TICKS);
        if (t == null) {
            pendingReevaluations.remove(uuid);
        }
    }

    private void completeReevaluation(@NotNull Player player) {
        pendingReevaluations.remove(player.getUniqueId());
        reevaluateNow(player, false);
    }

    private void restartPlaceholderRefresh(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        ScheduledTask existing = refreshTasks.remove(uuid);
        if (existing != null) {
            existing.cancel();
        }
        long ticks = settings.placeholderRefreshTicks();
        if (ticks <= 0 || settings.detection() == DetectionMode.NATIVE || !placeholderSource.isAvailable()) {
            return;
        }
        ScheduledTask t = player.getScheduler().runAtFixedRate(plugin, task -> refreshPlaceholder(player), null, ticks, ticks);
        if (t != null) {
            refreshTasks.put(uuid, t);
        }
    }

    private void refreshPlaceholder(@NotNull Player player) {
        if (!isFallbackActive() || !isPlaceholderDriven(player)) {
            return;
        }
        reevaluateNow(player, true);
    }

    private void reevaluateNow(@NotNull Player player, boolean forcePlaceholder) {
        if (!isFallbackActive() || !player.isOnline()) {
            return;
        }
        resolveClaims(player);
        boolean decision = resolveFresh(player, forcePlaceholder);
        noteNoOffer(player);
        Boolean previous = appliedDecisions.get(player.getUniqueId());
        if (previous != null && previous == decision) {
            return;
        }
        if (Boolean.TRUE.equals(previous) && !decision && isPlaceholderDriven(player)) {
            scheduleDowngradeConfirmation(player);
            return;
        }
        applyDecision(player, decision);
    }

    private void applyDecision(@NotNull Player player, boolean decision) {
        appliedDecisions.put(player.getUniqueId(), decision);
        applier.applyToAll(player, decision);
        logger.debug("Resource pack decision for " + player.getName() + ": " + (decision ? "custom model" : "vanilla NPC"));
    }

    /**
     * Placeholders often flip to "no pack" for a moment while a pack is re-sent; only a confirmed
     * downgrade hides the model.
     */
    private void scheduleDowngradeConfirmation(@NotNull Player player) {
        player.getScheduler().runDelayed(plugin, task -> confirmDowngrade(player), null, PLACEHOLDER_CONFIRM_DELAY_TICKS);
    }

    private void confirmDowngrade(@NotNull Player player) {
        if (!isFallbackActive() || !player.isOnline()) {
            return;
        }
        boolean decision = resolveFresh(player, true);
        if (!decision && Boolean.TRUE.equals(appliedDecisions.get(player.getUniqueId()))) {
            applyDecision(player, false);
        }
    }

    /**
     * Called from any thread by the registry whenever a strict-chain status is recorded. Remembers every
     * client-verified load and forgets every failure, so later proxy claims can be trusted or verified by
     * pack.
     */
    private void onStatusTraced(@NotNull UUID playerId, @NotNull UUID packId, @NotNull PackStatus received, @NotNull PackStatus recorded) {
        logger.debug("Resource pack answer " + received + " for " + packId + " from " + playerId
                + (recorded != received ? " recorded as " + recorded : ""));
        PlayerPackSession session = registry.get(playerId);
        if (session == null) {
            return;
        }
        String key = session.packKey(packId);
        if (recorded == PackStatus.SUCCESSFULLY_LOADED) {
            verdictMemory.remember(playerId, key);
        } else if (recorded.isFailure()) {
            verdictMemory.forget(playerId, key);
        }
    }

    private void resolveClaims(@NotNull Player player) {
        PlayerPackSession session = registry.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        for (ClaimedPack claim : session.claimedPacks()) {
            resolveClaim(player, session, claim);
        }
    }

    private void resolveClaim(@NotNull Player player, @NotNull PlayerPackSession session, @NotNull ClaimedPack claim) {
        UUID uuid = player.getUniqueId();
        if (verdictMemory.isVerified(uuid, claim.packKey())) {
            registry.resolveClaim(uuid, claim.packId());
            logger.debug("Resource pack " + claim.packId() + " for " + player.getName() + ": trusted proxy answer (verified before)");
            return;
        }
        if (settings.verifyProxyAnswers() && session.markVerificationStarted(claim.packId()) && startVerification(player, session, claim)) {
            logger.debug("Resource pack " + claim.packId() + " for " + player.getName() + ": verifying proxy answer");
            return;
        }
        registry.resolveClaim(uuid, claim.packId());
    }

    /**
     * Removing the pack makes Velocity forget it as applied ({@code handler.remove(id)}); the captured push
     * packet is then re-sent on the same channel event loop, in order after the removal, so it reaches the
     * client itself and the client answers honestly.
     */
    private boolean startVerification(@NotNull Player player, @NotNull PlayerPackSession session, @NotNull ClaimedPack claim) {
        Object pushPacket = claim.pushPacket();
        if (pushPacket == null || !(session.connectionToken() instanceof Channel channel)) {
            return false;
        }
        try {
            player.removeResourcePack(claim.packId());
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
        try {
            channel.eventLoop().execute(() -> channel.writeAndFlush(pushPacket));
        } catch (RuntimeException e) {
            return false;
        }
        return true;
    }

    private void noteNoOffer(@NotNull Player player) {
        if (noOfferHintLogged.get() || settings.detection() == DetectionMode.PLACEHOLDER || registry.hasObservedPackActivity()) {
            return;
        }
        if (nativeDecision(player) != PackDecision.NO_OFFER) {
            return;
        }
        noOfferPlayers.add(player.getUniqueId());
        if (noOfferPlayers.size() >= NO_OFFER_HINT_THRESHOLD && noOfferHintLogged.compareAndSet(false, true)) {
            logger.warn("Model fallback: the server did not offer a resource pack to " + NO_OFFER_HINT_THRESHOLD
                    + " players in a row. If the pack is sent by a proxy or installed manually, set "
                    + IntegrationsConfig.MODEL_FALLBACK_NO_OFFER_PATH + " to MODEL or "
                    + IntegrationsConfig.MODEL_FALLBACK_DETECTION_PATH + " to PLACEHOLDER.");
        }
    }
}
