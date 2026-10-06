package de.oliver.fancynpcs.integrations.resourcepack.network;

import de.oliver.fancynpcs.integrations.resourcepack.state.PackSessionRegistry;
import de.oliver.fancynpcs.integrations.resourcepack.state.PackStatus;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Sits right before the vanilla {@code packet_handler} in a player's Netty pipeline and only observes
 * packet objects: learns the player's UUID from the login-success packet, then reports resource pack
 * offers/removals (outbound) and client answers (inbound) of every connection phase to the
 * {@link PackSessionRegistry}. It never modifies, drops or delays a packet; any failure is reported
 * once and ignored.
 */
final class PackPacketHandler extends ChannelDuplexHandler {

    private final PackSessionRegistry registry;
    private final Consumer<Throwable> errorReporter;
    private volatile @Nullable UUID playerId;

    PackPacketHandler(@NotNull PackSessionRegistry registry, @NotNull Consumer<Throwable> errorReporter) {
        this.registry = registry;
        this.errorReporter = errorReporter;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        try {
            observeOutbound(ctx, msg);
        } catch (RuntimeException | LinkageError e) {
            reportSafely(e);
        } finally {
            super.write(ctx, msg, promise);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            observeInbound(msg);
        } catch (RuntimeException | LinkageError e) {
            reportSafely(e);
        } finally {
            super.channelRead(ctx, msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try {
            UUID id = playerId;
            if (id != null) {
                registry.end(id, ctx.channel());
            }
        } catch (RuntimeException | LinkageError e) {
            reportSafely(e);
        } finally {
            super.channelInactive(ctx);
        }
    }

    private void reportSafely(Throwable e) {
        try {
            errorReporter.accept(e);
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    private void observeOutbound(ChannelHandlerContext ctx, Object msg) {
        switch (PacketShapes.classify(msg)) {
            case LOGIN_SUCCESS -> onLoginSuccess(ctx, msg);
            case PACK_PUSH -> onPush(msg);
            case PACK_POP -> onPop(msg);
            default -> {
            }
        }
    }

    private void onLoginSuccess(ChannelHandlerContext ctx, Object msg) {
        UUID id = PacketShapes.profileId(msg);
        if (id == null) {
            return;
        }
        playerId = id;
        registry.begin(id, ctx.channel());
    }

    private void onPush(Object msg) {
        UUID id = playerId;
        if (id != null) {
            registry.recordOffer(id, PacketShapes.packId(msg), PacketShapes.packHash(msg), msg);
        }
    }

    private void onPop(Object msg) {
        UUID id = playerId;
        if (id != null) {
            registry.recordPop(id, PacketShapes.poppedPackId(msg));
        }
    }

    private void observeInbound(Object msg) {
        if (PacketShapes.classify(msg) != PacketShapes.Kind.PACK_STATUS) {
            return;
        }
        UUID id = playerId;
        if (id == null) {
            return;
        }
        PackStatus status = PacketShapes.status(msg);
        if (status == null) {
            return;
        }
        registry.recordStatus(id, PacketShapes.packId(msg), status);
    }
}
