package de.oliver.fancynpcs.integrations.resourcepack.state;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Resource pack state of ONE client connection. Created when the connection logs in (packet
 * observer) or when a Bukkit event/join first sees the player. Tracks every pack by id and turns
 * it into a {@link PackDecision}. Thread-safe: mutated from Netty threads and Bukkit threads.
 */
public final class PlayerPackSession {

    private final @Nullable Object connectionToken;
    private final long startedAtMillis;
    private volatile @Nullable Player boundPlayer;
    private final Map<UUID, PackEntry> packs = new LinkedHashMap<>();
    private final Set<UUID> verificationsStarted = new HashSet<>();

    public PlayerPackSession(@Nullable Object connectionToken, long startedAtMillis) {
        this.connectionToken = connectionToken;
        this.startedAtMillis = startedAtMillis;
    }

    public @Nullable Object connectionToken() {
        return connectionToken;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }

    public @Nullable Player boundPlayer() {
        return boundPlayer;
    }

    public void bindPlayer(@NotNull Player player) {
        this.boundPlayer = player;
    }

    /**
     * Marks the pack as offered again. If the previous entry was still effectively loaded (a
     * successful load, or a pending re-send of a pack that was loaded before), the new entry stays
     * sticky-loaded until a terminal status (loaded or failure) arrives, so the client keeps
     * showing the old content while the re-send is pending.
     */
    public synchronized void offer(@NotNull UUID packId) {
        offer(packId, null, null);
    }

    /**
     * Same as {@link #offer(UUID)}, additionally remembering the pack's sha1 hash and the raw push
     * packet (when known), so a later proxy claim can be verified or remembered by hash.
     */
    public synchronized void offer(@NotNull UUID packId, @Nullable String hash, @Nullable Object pushPacket) {
        PackEntry prev = packs.get(packId);
        boolean sticky = prev != null && prev.effectivelyLoaded();
        String prevHash = prev != null ? prev.hash() : null;
        Object prevPushPacket = prev != null ? prev.pushPacket() : null;
        packs.put(packId, new PackEntry(PackStatus.OFFERED, sticky, 0L,
                hash != null ? hash : prevHash, pushPacket != null ? pushPacket : prevPushPacket));
    }

    public synchronized void status(@NotNull UUID packId, @NotNull PackStatus status) {
        status(packId, status, false, 0L, 0L);
    }

    /**
     * With {@code strictChain}, a SUCCESSFULLY_LOADED is only trusted right after ACCEPTED/DOWNLOADED
     * (or as a replay of an already trusted load) of the same pack; otherwise it is recorded as
     * UNVERIFIED_LOAD instead.
     */
    public synchronized void status(@NotNull UUID packId, @NotNull PackStatus status, boolean strictChain) {
        status(packId, status, strictChain, 0L, 0L);
    }

    /**
     * Full strict-chain classification: a SUCCESSFULLY_LOADED that follows ACCEPTED/DOWNLOADED too
     * quickly (faster than {@code proxyWindowMillis} after the last ACCEPTED) did not come from an
     * actual client resource reload but from a proxy answering on the client's behalf, and is recorded
     * as CLAIMED_LOAD (pending) instead — unless it is a replay of an already effectively loaded pack.
     *
     * @return the status actually recorded for this pack.
     */
    public synchronized @NotNull PackStatus status(@NotNull UUID packId, @NotNull PackStatus status,
                                                     boolean strictChain, long nowMillis, long proxyWindowMillis) {
        PackEntry prev = packs.get(packId);
        PackStatus recorded = classify(prev, status, strictChain, nowMillis, proxyWindowMillis);
        boolean sticky = recorded.isPending() && prev != null && prev.effectivelyLoaded();
        long acceptedAt = recorded == PackStatus.ACCEPTED ? nowMillis : prev != null ? prev.acceptedAtMillis() : 0L;
        packs.put(packId, new PackEntry(recorded, sticky, acceptedAt,
                prev != null ? prev.hash() : null, prev != null ? prev.pushPacket() : null));
        return recorded;
    }

    private static @NotNull PackStatus classify(@Nullable PackEntry prev, @NotNull PackStatus status,
                                                  boolean strict, long now, long window) {
        if (!strict || !status.isLoaded()) {
            return status;
        }
        if (!allowsLoad(prev)) {
            return PackStatus.UNVERIFIED_LOAD;
        }
        if (window > 0 && prev.status() != PackStatus.SUCCESSFULLY_LOADED
                && now - prev.acceptedAtMillis() < window && !prev.stickyLoaded()) {
            return PackStatus.CLAIMED_LOAD;
        }
        return status;
    }

    private static boolean allowsLoad(@Nullable PackEntry prev) {
        return prev != null && (prev.status() == PackStatus.ACCEPTED
                || prev.status() == PackStatus.DOWNLOADED
                || prev.status() == PackStatus.SUCCESSFULLY_LOADED);
    }

    /**
     * @return every pack currently recorded as CLAIMED_LOAD (proxy claim awaiting resolution), in
     * insertion order.
     */
    public synchronized @NotNull List<ClaimedPack> claimedPacks() {
        List<ClaimedPack> claims = new ArrayList<>();
        for (Map.Entry<UUID, PackEntry> entry : packs.entrySet()) {
            if (entry.getValue().status() == PackStatus.CLAIMED_LOAD) {
                claims.add(new ClaimedPack(entry.getKey(), packKey(entry.getKey()), entry.getValue().pushPacket()));
            }
        }
        return claims;
    }

    /**
     * Trusts a CLAIMED_LOAD pack as SUCCESSFULLY_LOADED without further verification.
     *
     * @return true if a CLAIMED_LOAD entry was found and resolved, false otherwise.
     */
    public synchronized boolean resolveClaim(@NotNull UUID packId) {
        PackEntry entry = packs.get(packId);
        if (entry == null || entry.status() != PackStatus.CLAIMED_LOAD) {
            return false;
        }
        packs.put(packId, new PackEntry(PackStatus.SUCCESSFULLY_LOADED, false,
                entry.acceptedAtMillis(), entry.hash(), entry.pushPacket()));
        return true;
    }

    /**
     * @return true if verification of this pack was not started yet for this session (and marks it as
     * started).
     */
    public synchronized boolean markVerificationStarted(@NotNull UUID packId) {
        return verificationsStarted.add(packId);
    }

    /**
     * @return the pack's lower-case sha1 hash when known, else {@code "id:" + packId}.
     */
    public synchronized @NotNull String packKey(@NotNull UUID packId) {
        PackEntry entry = packs.get(packId);
        String hash = entry == null ? null : entry.hash();
        if (hash != null) {
            String trimmed = hash.trim();
            if (!trimmed.isEmpty()) {
                return trimmed.toLowerCase(Locale.ROOT);
            }
        }
        return "id:" + packId;
    }

    public synchronized void pop(@NotNull UUID packId) {
        packs.remove(packId);
    }

    public synchronized void popAll() {
        packs.clear();
    }

    public synchronized boolean isEmpty() {
        return packs.isEmpty();
    }

    public synchronized @NotNull Map<UUID, PackStatus> snapshot() {
        Map<UUID, PackStatus> copy = new LinkedHashMap<>();
        for (Map.Entry<UUID, PackEntry> entry : packs.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().status());
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Decides whether the required packs are loaded. LOADED when enough considered packs are
     * effectively loaded (all of them if {@code requireAll}, otherwise any); PENDING when none are
     * loaded yet but at least one is still in flight; AWAITING_OFFER/NO_OFFER when no offer (or not
     * enough offers) arrived and the session is still (or no longer) inside its startup grace
     * window; MISSING otherwise.
     */
    public synchronized @NotNull PackDecision evaluate(@NotNull Set<UUID> requiredPackIds, boolean requireAll,
                                                        long nowMillis, long graceMillis) {
        Map<UUID, PackEntry> considered = considered(requiredPackIds);
        int expected = requiredPackIds.isEmpty() ? considered.size() : requiredPackIds.size();
        long loaded = considered.values().stream().filter(PackEntry::effectivelyLoaded).count();
        if (isSatisfied(requireAll, expected, loaded)) {
            return PackDecision.LOADED;
        }
        if (considered.values().stream().anyMatch(entry -> entry.status().isPending())) {
            return PackDecision.PENDING;
        }
        return undecided(considered, expected, nowMillis, graceMillis);
    }

    private @NotNull Map<UUID, PackEntry> considered(@NotNull Set<UUID> requiredPackIds) {
        if (requiredPackIds.isEmpty()) {
            return new LinkedHashMap<>(packs);
        }
        Map<UUID, PackEntry> result = new LinkedHashMap<>();
        for (UUID id : requiredPackIds) {
            PackEntry entry = packs.get(id);
            if (entry != null) {
                result.put(id, entry);
            }
        }
        return result;
    }

    private boolean isSatisfied(boolean requireAll, int expected, long loaded) {
        return requireAll ? (expected > 0 && loaded == expected) : loaded > 0;
    }

    private @NotNull PackDecision undecided(@NotNull Map<UUID, PackEntry> considered, int expected,
                                             long nowMillis, long graceMillis) {
        boolean inGrace = nowMillis - startedAtMillis < graceMillis;
        if (considered.isEmpty()) {
            return inGrace ? PackDecision.AWAITING_OFFER : PackDecision.NO_OFFER;
        }
        if (considered.size() < expected && inGrace) {
            return PackDecision.AWAITING_OFFER;
        }
        return PackDecision.MISSING;
    }

    private record PackEntry(PackStatus status, boolean stickyLoaded, long acceptedAtMillis,
                              @Nullable String hash, @Nullable Object pushPacket) {

        boolean effectivelyLoaded() {
            return status.isLoaded() || (stickyLoaded && status.isPending());
        }
    }

    /**
     * A pack recorded as CLAIMED_LOAD: a proxy's "loaded" answer on the client's behalf, awaiting
     * resolution.
     */
    public record ClaimedPack(UUID packId, String packKey, @Nullable Object pushPacket) {
    }
}
