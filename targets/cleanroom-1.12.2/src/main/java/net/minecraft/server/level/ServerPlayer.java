package net.minecraft.server.level;

import net.minecraft.entity.player.EntityPlayerMP;

/** Type facade for APIs shared with modern common sources. */
public class ServerPlayer {
    private final EntityPlayerMP delegate;

    public ServerPlayer() { this(null); }
    public ServerPlayer(EntityPlayerMP delegate) { this.delegate = delegate; }
    public static ServerPlayer wrap(EntityPlayerMP player) { return new ServerPlayer(player); }
    public EntityPlayerMP legacy() { return delegate; }
    public void closeContainer() {
        if (delegate != null) delegate.closeContainer();
    }
}
