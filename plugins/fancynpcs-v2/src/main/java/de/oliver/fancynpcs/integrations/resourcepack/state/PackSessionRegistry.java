package de.oliver.fancynpcs.integrations.resourcepack.state;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Holds one {@link PlayerPackSession} per player UUID.
 *
 * <p>Fed from two sides: the Netty packet observer (keyed by UUID, identified by a connection token) and
 * Bukkit events (keyed by the {@link Player} instance). Guards against stale sessions left over from a
 * previous connection of the same player (fast reconnects). Every mutation notifies the change listener
 * with the player's UUID; the listener decides whether the effective decision changed.
 */
public final class PackSessionRegistry {

    /**
     * Single pack of 1.20–1.20.2 servers, which have no pack ids.
     */
    public static final UUID LEGACY_PACK_ID = new UUID(0L, 0L);

    /**
     * Last status reconciled from {@link Player#getResourcePackStatus()} at join, pack id unknown.
     */
    public static final UUID UNIDENTIFIED_PACK_ID = new UUID(0L, 1L);

    private final ConcurrentHashMap<UUID, PlayerPackSession> sessions = new ConcurrentHashMap<>();
    private final Consumer<UUID> changeListener;
    private final LongSupplier clock;
    private final Supplier<PackRules> rules;
    private final StatusTrace trace;
    private final AtomicBoolean packActivity = new AtomicBoolean();

    /**
     * Reports every strict-chain status recorded: the status the client (or proxy) actually sent, and the
     * status the session recorded for it, which may differ (UNVERIFIED_LOAD, CLAIMED_LOAD).
     */
    @FunctionalInterface
    public interface StatusTrace {
        void onStatus(@NotNull UUID playerId, @NotNull UUID packId, @NotNull PackStatus received, @NotNull PackStatus recorded);
    }

    public PackSessionRegistry(@NotNull Consumer<UUID> changeListener, @NotNull LongSupplier clock,
                                @NotNull Supplier<PackRules> rules, @NotNull StatusTrace trace) {
        this.changeListener = changeListener;
        this.clock = clock;
        this.rules = rules;
        this.trace = trace;
    }

    /**
     * Starts a fresh session for a new login, replacing any previous one for this player id. A new login
     * always starts from a clean client state, since the client drops server packs on disconnect.
     */
    public void begin(@NotNull UUID playerId, @NotNull Object connectionToken) {
        sessions.put(playerId, new PlayerPackSession(connectionToken, clock.getAsLong()));
        notifyChanged(playerId);
    }

    /**
     * Removes the session for this player id, but only if it still belongs to the given connection
     * (identity compare). A fast reconnect may have already replaced it with a newer session by the time
     * the old channel becomes inactive, in which case this is a no-op.
     */
    public void end(@NotNull UUID playerId, @NotNull Object connectionToken) {
        sessions.computeIfPresent(playerId, (id, session) ->
                session.connectionToken() == connectionToken ? null : session);
    }

    public void recordOffer(@NotNull UUID playerId, @NotNull UUID packId) {
        recordOffer(playerId, packId, null, null);
    }

    public void recordOffer(@NotNull UUID playerId, @NotNull UUID packId, @Nullable String hash, @Nullable Object pushPacket) {
        packActivity.set(true);
        sessionFor(playerId).offer(packId, hash, pushPacket);
        notifyChanged(playerId);
    }

    public void recordPop(@NotNull UUID playerId, @Nullable UUID packId) {
        PlayerPackSession session = sessions.get(playerId);
        if (session == null) {
            return;
        }
        if (packId == null) {
            session.popAll();
        } else {
            session.pop(packId);
        }
        notifyChanged(playerId);
    }

    public void recordStatus(@NotNull UUID playerId, @NotNull UUID packId, @NotNull PackStatus status) {
        packActivity.set(true);
        PlayerPackSession session = sessionFor(playerId);
        PackRules r = rules.get();
        PackStatus recorded = session.status(packId, status, r.strictChain(), clock.getAsLong(), r.proxyAnswerWindowMillis());
        trace.onStatus(playerId, packId, status, recorded);
        notifyChanged(playerId);
    }

    /**
     * Binds the given player to its session, reusing an existing one only when it is unbound or already
     * bound to the same {@link Player} instance. A session bound to a different instance belongs to a
     * stale previous connection of the same player (fast reconnect) and is replaced.
     */
    public @NotNull PlayerPackSession attach(@NotNull Player player) {
        return sessions.compute(player.getUniqueId(), (id, existing) -> reuseOrCreate(existing, player));
    }

    public void recordStatus(@NotNull Player player, @NotNull UUID packId, @NotNull PackStatus status) {
        packActivity.set(true);
        PlayerPackSession session = attach(player);
        PackRules r = rules.get();
        PackStatus recorded = session.status(packId, status, r.strictChain(), clock.getAsLong(), r.proxyAnswerWindowMillis());
        trace.onStatus(player.getUniqueId(), packId, status, recorded);
        notifyChanged(player.getUniqueId());
    }

    /**
     * Records a status whose answer chain cannot be verified (Paper only keeps the last status of the
     * configuration phase), so it bypasses the strict chain.
     */
    public void recordTrustedStatus(@NotNull Player player, @NotNull UUID packId, @NotNull PackStatus status) {
        packActivity.set(true);
        attach(player).status(packId, status, false);
        notifyChanged(player.getUniqueId());
    }

    /**
     * Trusts a CLAIMED_LOAD pack of this session as SUCCESSFULLY_LOADED without further verification.
     *
     * @return true if a CLAIMED_LOAD entry was found and resolved, false otherwise.
     */
    public boolean resolveClaim(@NotNull UUID playerId, @NotNull UUID packId) {
        PlayerPackSession session = sessions.get(playerId);
        if (session == null) {
            return false;
        }
        if (session.resolveClaim(packId)) {
            notifyChanged(playerId);
            return true;
        }
        return false;
    }

    public void detach(@NotNull Player player) {
        sessions.computeIfPresent(player.getUniqueId(), (id, session) ->
                session.boundPlayer() == player ? null : session);
    }

    public @Nullable PlayerPackSession get(@NotNull UUID playerId) {
        return sessions.get(playerId);
    }

    public boolean hasObservedPackActivity() {
        return packActivity.get();
    }

    public void clear() {
        sessions.clear();
    }

    private @NotNull PlayerPackSession reuseOrCreate(@Nullable PlayerPackSession existing, @NotNull Player player) {
        if (existing != null && (existing.boundPlayer() == null || existing.boundPlayer() == player)) {
            existing.bindPlayer(player);
            return existing;
        }
        PlayerPackSession session = new PlayerPackSession(null, clock.getAsLong());
        session.bindPlayer(player);
        return session;
    }

    private @NotNull PlayerPackSession sessionFor(@NotNull UUID playerId) {
        return sessions.computeIfAbsent(playerId, id -> new PlayerPackSession(null, clock.getAsLong()));
    }

    private void notifyChanged(@NotNull UUID playerId) {
        changeListener.accept(playerId);
    }
}
