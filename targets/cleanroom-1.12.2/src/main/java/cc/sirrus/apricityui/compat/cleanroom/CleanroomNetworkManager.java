package cc.sirrus.apricityui.compat.cleanroom;

import com.sighs.apricityui.network.api.CustomPacketPayload;
import com.sighs.apricityui.network.api.INetworkPacket;
import com.sighs.apricityui.network.packet.CloseContainerRequestPacket;
import com.sighs.apricityui.network.packet.OpenScreenRequestPacket;
import com.sighs.apricityui.network.spi.INetworkManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** Adapter for the common NetworkManager facade on the 1.12 target. */
public final class CleanroomNetworkManager implements INetworkManager {
    private static final CleanroomNetworkManager INSTANCE = new CleanroomNetworkManager();

    public static CleanroomNetworkManager instance() { return INSTANCE; }

    private <T extends INetworkPacket<T> & CustomPacketPayload> void dispatch(T packet, ServerPlayer player) {
        if (packet instanceof OpenScreenRequestPacket open) {
            CleanroomNetworkService.INSTANCE.openScreen(player, open.templatePath(), open.containers());
        } else if (packet instanceof CloseContainerRequestPacket) {
            if (player != null) player.closeContainer();
        }
    }

    @Override public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToPlayer(T packet, ServerPlayer player) { dispatch(packet, player); }
    @Override public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToAll(T packet) { }
    @Override public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToServer(T packet) { }
    @Override public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToWorld(T packet, ServerLevel level) { }
    @Override public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToNear(T packet, ServerLevel level, Vec3 pos, double radius) { }
    @Override public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToNearExcept(T packet, ServerLevel level, Vec3 pos, double radius, ServerPlayer excluded) { }
    @Override public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToTrackingEntity(T packet, Entity entity) { }
    @Override public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToTrackingEntityAndSelf(T packet, Entity entity) { }
    @Override public <T extends INetworkPacket<T> & CustomPacketPayload> void sendToTrackingChunk(T packet, ServerLevel level, ChunkPos chunkPos) { }
}
