package de.oliver.fancynpcs.integrations.resourcepack.network;

import de.oliver.fancynpcs.integrations.resourcepack.state.PackSessionRegistry;
import de.oliver.fancyanalytics.logger.ExtendedFancyLogger;
import de.oliver.fancyanalytics.logger.properties.ThrowableProperty;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Installs a {@link PackPacketHandler} into every new player connection through Paper's internal
 * {@code ChannelInitializeListenerHolder} (present from 1.17 to 26.x, accessed purely by reflection because
 * it is not part of the public API). Connections that already existed when the observer was installed are
 * not observed; Bukkit events cover them. If the hook is missing, {@link #install()} returns {@code false}
 * and detection falls back to Bukkit events.
 */
public final class PackPacketObserver {

    public static final String HANDLER_NAME = "fancynpcs_pack_observer";

    private static final String HOLDER_CLASS = "io.papermc.paper.network.ChannelInitializeListenerHolder";
    private static final String LISTENER_CLASS = "io.papermc.paper.network.ChannelInitializeListener";
    private static final String LISTENER_METHOD = "afterInitChannel";
    private static final String PACKET_HANDLER_NAME = "packet_handler";
    private static final Key LISTENER_KEY = Key.key("fancynpcs", "pack_observer");

    private final PackSessionRegistry registry;
    private final ExtendedFancyLogger logger;
    private final Set<Channel> channels = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean errorReported = new AtomicBoolean();
    private volatile boolean installed;

    public PackPacketObserver(@NotNull PackSessionRegistry registry, @NotNull ExtendedFancyLogger logger) {
        this.registry = registry;
        this.logger = logger;
    }

    public boolean install() {
        if (installed) {
            return true;
        }
        try {
            Class<?> holder = Class.forName(HOLDER_CLASS);
            Class<?> listenerType = Class.forName(LISTENER_CLASS);
            installed = true;
            holder.getMethod("addListener", Key.class, listenerType).invoke(null, LISTENER_KEY, createListener(listenerType));
            logger.info("Resource pack packet observer installed");
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            installed = false;
            logger.warn("Resource pack packet observer is unavailable on this server, falling back to Bukkit events",
                    ThrowableProperty.of(e));
            return false;
        }
    }

    public void uninstall() {
        if (!installed) {
            return;
        }
        installed = false;
        try {
            Class.forName(HOLDER_CLASS).getMethod("removeListener", Key.class).invoke(null, LISTENER_KEY);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            logger.debug("Could not unregister the resource pack packet observer", ThrowableProperty.of(e));
        }
        channels.forEach(this::eject);
        channels.clear();
    }

    public boolean isInstalled() {
        return installed;
    }

    private Object createListener(Class<?> listenerType) {
        return Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[]{listenerType}, this::invokeListener);
    }

    private @Nullable Object invokeListener(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case LISTENER_METHOD -> {
                inject(args[0]);
                return null;
            }
            case "hashCode" -> {
                return System.identityHashCode(proxy);
            }
            case "equals" -> {
                return proxy == args[0];
            }
            case "toString" -> {
                return "FancyNpcs resource pack observer";
            }
            default -> {
                return null;
            }
        }
    }

    private void inject(Object channelArg) {
        try {
            injectHandler((Channel) channelArg);
        } catch (RuntimeException | LinkageError e) {
            reportError(e);
        }
    }

    private void injectHandler(Channel channel) {
        if (!installed) {
            return;
        }
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(HANDLER_NAME) != null) {
            return;
        }
        addHandler(pipeline);
        channels.add(channel);
        channel.closeFuture().addListener(future -> channels.remove(channel));
        if (!installed) {
            eject(channel);
        }
    }

    private void addHandler(ChannelPipeline pipeline) {
        PackPacketHandler handler = new PackPacketHandler(registry, this::reportError);
        if (pipeline.get(PACKET_HANDLER_NAME) != null) {
            pipeline.addBefore(PACKET_HANDLER_NAME, HANDLER_NAME, handler);
        } else {
            pipeline.addLast(HANDLER_NAME, handler);
        }
    }

    private void eject(Channel channel) {
        try {
            channel.eventLoop().execute(() -> removeHandler(channel));
        } catch (RuntimeException | LinkageError e) {
            logger.debug("Could not schedule removal of the resource pack packet observer", ThrowableProperty.of(e));
        }
    }

    private void removeHandler(Channel channel) {
        try {
            if (channel.pipeline().get(HANDLER_NAME) != null) {
                channel.pipeline().remove(HANDLER_NAME);
            }
        } catch (RuntimeException | LinkageError e) {
            logger.debug("Could not remove the resource pack packet observer from the pipeline", ThrowableProperty.of(e));
        }
    }

    private void reportError(Throwable error) {
        if (errorReported.compareAndSet(false, true)) {
            logger.warn("Resource pack packet observer failed to read a packet; further errors are suppressed",
                    ThrowableProperty.of(error));
        }
    }
}
