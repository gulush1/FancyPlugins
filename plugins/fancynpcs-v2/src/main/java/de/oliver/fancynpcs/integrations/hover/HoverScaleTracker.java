package de.oliver.fancynpcs.integrations.hover;

import de.oliver.fancynpcs.FancyNpcs;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcAttribute;
import de.oliver.fancynpcs.integrations.IntegrationsConfig;
import de.oliver.fancynpcs.integrations.bettermodel.CustomModelAttribute;
import de.oliver.fancysitula.api.entities.FS_RealPlayer;
import de.oliver.fancysitula.api.packets.FS_ClientboundUpdateAttributesPacket.AttributeSnapshot;
import de.oliver.fancysitula.factories.FancySitula;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Smoothly scales the NPC a player aims at, for that player only. All state lives on the FancyNpcs npc thread.
 */
public class HoverScaleTracker implements Runnable {

    public static final String ATTRIBUTE_NAME = "hover_scale";
    public static final String CURVE_ATTRIBUTE_NAME = "hover_curve";

    private static final String SCALE_ATTRIBUTE_ID = "minecraft:scale";
    private static final long TICK_MILLIS = 50L;
    private static final int RESEND_INTERVAL_TICKS = 10;
    private static final float SEND_THRESHOLD = 0.001f;
    private static final float MIN_SENT_SCALE = 0.01f;
    private static final double RANGE_MARGIN = 3.0;
    private static final double EPSILON = 1.0E-9;
    private static final int AXES = 3;
    private static final Set<String> DISABLED_VALUES = Set.of("off", "false", "0");

    private final FancyNpcs plugin;
    private final IntegrationsConfig config;
    private final Map<UUID, PlayerHoverState> states = new HashMap<>();
    private final Set<String> loggedFailures = new HashSet<>();

    private ScheduledFuture<?> future;

    public HoverScaleTracker(FancyNpcs plugin, IntegrationsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        if (future != null) {
            return;
        }
        future = plugin.getNpcThread().scheduleAtFixedRate(this, 0, TICK_MILLIS, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (future != null) {
            future.cancel(false);
            future = null;
        }
        plugin.getNpcThread().execute(states::clear);
    }

    public void forgetPlayer(UUID playerId) {
        plugin.getNpcThread().execute(() -> states.remove(playerId));
    }

    public void forgetNpc(String npcId) {
        plugin.getNpcThread().execute(() -> states.values().forEach(state -> state.forget(npcId)));
    }

    public void forgetPair(UUID playerId, String npcId) {
        plugin.getNpcThread().execute(() -> {
            PlayerHoverState state = states.get(playerId);
            if (state != null) {
                state.forget(npcId);
            }
        });
    }

    @Override
    public void run() {
        try {
            Collection<? extends Player> players = Bukkit.getOnlinePlayers();
            if (players.isEmpty()) {
                states.clear();
                return;
            }
            Map<String, Npc> npcs = new HashMap<>();
            for (Npc npc : plugin.getNpcManagerImpl().getAllNpcs()) {
                npcs.put(npc.getData().getId(), npc);
            }
            for (Player player : players) {
                tickPlayerSafely(player, npcs);
            }
            states.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        } catch (Throwable t) {
            logOnce(t);
        }
    }

    private void tickPlayerSafely(Player player, Map<String, Npc> npcs) {
        try {
            tickPlayer(player, npcs);
        } catch (Throwable t) {
            logOnce(t);
        }
    }

    private void tickPlayer(Player player, Map<String, Npc> npcs) {
        PlayerHoverState state = states.computeIfAbsent(player.getUniqueId(), id -> new PlayerHoverState());
        Npc hovered = findHovered(player, state, npcs.values());
        updateHover(state, hovered);
        advance(player, state, npcs);
        state.tick++;
    }

    private void updateHover(PlayerHoverState state, Npc hovered) {
        String newId = hovered == null ? null : hovered.getData().getId();
        if (Objects.equals(newId, state.hoveredId)) {
            return;
        }
        state.hoveredId = newId;
        if (hovered != null) {
            float base = hovered.getData().getScale();
            state.animations.computeIfAbsent(newId, id -> new ScaleAnimation(base, config.getHoverScaleDurationTicks()));
        }
    }

    private float resolveFactor(Npc npc) {
        String raw = hoverAttribute(npc);
        Double parsed = raw == null ? null : parsePositive(raw);
        return parsed != null ? parsed.floatValue() : (float) config.getHoverScaleFactor();
    }

    private Double parsePositive(String raw) {
        try {
            double value = Double.parseDouble(raw.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private EasingCurve resolveCurve(Npc npc, EasingCurve fallback) {
        String raw = attribute(npc, CURVE_ATTRIBUTE_NAME);
        return EasingCurve.byName(raw).orElse(fallback);
    }

    private String hoverAttribute(Npc npc) {
        return attribute(npc, ATTRIBUTE_NAME);
    }

    private String attribute(Npc npc, String name) {
        for (Map.Entry<NpcAttribute, String> entry : npc.getData().getAttributes().entrySet()) {
            if (entry.getKey().getName().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean isEligible(Npc npc) {
        if (!npc.getData().getType().isAlive()) {
            return false;
        }
        for (NpcAttribute attribute : npc.getData().getAttributes().keySet()) {
            if (attribute.getName().equalsIgnoreCase(CustomModelAttribute.ATTRIBUTE_NAME)) {
                return false;
            }
        }
        String raw = hoverAttribute(npc);
        return raw == null || !DISABLED_VALUES.contains(raw.trim().toLowerCase(Locale.ROOT));
    }

    private boolean isCandidate(Player player, Npc npc, Location eye, double rangeSquared) {
        Location location = npc.getData().getLocation();
        return location != null
                && location.getWorld() == eye.getWorld()
                && location.distanceSquared(eye) <= rangeSquared
                && npc.isShownFor(player)
                && isEligible(npc);
    }

    private Npc findHovered(Player player, PlayerHoverState state, Iterable<Npc> npcs) {
        Location eye = player.getEyeLocation();
        double maxDistance = config.getHoverScaleMaxDistance();
        double rangeSquared = (maxDistance + RANGE_MARGIN) * (maxDistance + RANGE_MARGIN);
        Npc best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Npc npc : npcs) {
            if (!isCandidate(player, npc, eye, rangeSquared)) {
                continue;
            }
            double distance = rayDistance(eye, npc, state, maxDistance);
            if (distance >= 0 && distance < bestDistance) {
                bestDistance = distance;
                best = npc;
            }
        }
        return best;
    }

    private double rayDistance(Location eye, Npc npc, PlayerHoverState state, double maxDistance) {
        Entity entity = npc.getBukkitEntity();
        if (entity == null) {
            return -1;
        }
        Location location = npc.getData().getLocation();
        ScaleAnimation animation = state.animations.get(npc.getData().getId());
        double scale = animation != null ? animation.current() : npc.getData().getScale();
        double halfWidth = entity.getWidth() * scale / 2.0;
        double height = entity.getHeight() * scale;
        double[] min = {location.getX() - halfWidth, location.getY(), location.getZ() - halfWidth};
        double[] max = {location.getX() + halfWidth, location.getY() + height, location.getZ() + halfWidth};
        return slab(eye, min, max, maxDistance);
    }

    private double slab(Location eye, double[] min, double[] max, double maxDistance) {
        Vector direction = eye.getDirection();
        double[] origin = {eye.getX(), eye.getY(), eye.getZ()};
        double[] dir = {direction.getX(), direction.getY(), direction.getZ()};
        double[] span = {-Double.MAX_VALUE, Double.MAX_VALUE};
        for (int axis = 0; axis < AXES; axis++) {
            if (!clipAxis(origin[axis], dir[axis], min[axis], max[axis], span)) {
                return -1;
            }
        }
        if (span[0] > span[1] || span[1] < 0) {
            return -1;
        }
        double distance = Math.max(span[0], 0);
        return distance <= maxDistance ? distance : -1;
    }

    private boolean clipAxis(double origin, double dir, double min, double max, double[] span) {
        if (Math.abs(dir) < EPSILON) {
            return origin >= min && origin <= max;
        }
        double t1 = (min - origin) / dir;
        double t2 = (max - origin) / dir;
        span[0] = Math.max(span[0], Math.min(t1, t2));
        span[1] = Math.min(span[1], Math.max(t1, t2));
        return true;
    }

    private void advance(Player player, PlayerHoverState state, Map<String, Npc> npcs) {
        Iterator<Map.Entry<String, ScaleAnimation>> iterator = state.animations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ScaleAnimation> entry = iterator.next();
            Npc npc = npcs.get(entry.getKey());
            if (npc == null || !npc.isShownFor(player)) {
                iterator.remove();
                continue;
            }
            if (step(player, state, npc, entry.getValue(), entry.getKey().equals(state.hoveredId))) {
                iterator.remove();
            }
        }
    }

    private void retarget(Npc npc, ScaleAnimation animation, float expected) {
        EasingCurve fallback = expected > animation.current()
                ? config.getHoverScaleGrowCurve()
                : config.getHoverScaleShrinkCurve();
        animation.retarget(expected, resolveCurve(npc, fallback));
    }

    private boolean step(Player player, PlayerHoverState state, Npc npc, ScaleAnimation animation, boolean hoveredNpc) {
        float base = npc.getData().getScale();
        float expected = hoveredNpc ? base * resolveFactor(npc) : base;
        boolean hovered = Math.abs(expected - base) > SEND_THRESHOLD;
        retarget(npc, animation, expected);
        animation.tick();
        float current = animation.current();
        boolean changed = Math.abs(current - animation.getLastSent()) > SEND_THRESHOLD;
        boolean resend = hovered && animation.isFinished() && state.tick % RESEND_INTERVAL_TICKS == 0;
        if (changed || resend) {
            sendScale(player, npc, current);
            animation.markSent(current);
        }
        return animation.isFinished() && !hovered
                && Math.abs(current - npc.getData().getScale()) <= SEND_THRESHOLD;
    }

    private void sendScale(Player player, Npc npc, float scale) {
        List<AttributeSnapshot> snapshots = List.of(new AttributeSnapshot(SCALE_ATTRIBUTE_ID, Math.max(scale, MIN_SENT_SCALE)));
        FancySitula.PACKET_FACTORY.createUpdateAttributesPacket(npc.getEntityId(), snapshots)
                .send(new FS_RealPlayer(player));
    }

    private void logOnce(Throwable throwable) {
        String key = throwable.getClass().getName() + ": " + throwable.getMessage();
        if (loggedFailures.add(key)) {
            plugin.getFancyLogger().error("Hover scale tick failed: " + key);
        }
    }

    private static final class PlayerHoverState {

        private final Map<String, ScaleAnimation> animations = new HashMap<>();
        private String hoveredId;
        private int tick;

        private void forget(String npcId) {
            animations.remove(npcId);
            if (npcId.equals(hoveredId)) {
                hoveredId = null;
            }
        }
    }
}
