package cc.sirrus.apricityui.compat.cleanroom;

import com.sighs.apricityui.element.ContainerDeclaration;
import com.sighs.apricityui.screen.ApricityScreen;
import com.sighs.apricityui.screen.ApricityContainerMenu;
import com.sighs.apricityui.screen.ApricityContainerScreen;
import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.spi.AuiBindingBuilder;
import com.sighs.apricityui.spi.AuiBindingFilter;
import net.minecraft.core.BlockPos;
import com.sighs.apricityui.spi.AuiNetworkService;
import com.sighs.apricityui.spi.AuiPendingMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

import java.util.List;
import java.util.function.Consumer;

/** Forge 1.12 SimpleNetworkWrapper bridge for AUI screen requests. */
public final class CleanroomNetworkService implements AuiNetworkService {
    public static final CleanroomNetworkService INSTANCE = new CleanroomNetworkService();
    private final SimpleNetworkWrapper channel = NetworkRegistry.INSTANCE.newSimpleChannel("apricityui");
    private boolean registered;

    private CleanroomNetworkService() { }

    public void register() {
        if (registered) return;
        registered = true;
        channel.registerMessage(OpenScreenMessage.Handler.class, OpenScreenMessage.class, 0, Side.CLIENT);
    }

    @Override
    public AuiPendingMenu pendingMenu(ServerPlayer player, String templatePath) {
        return configurator -> { if (configurator != null) configurator.accept(new CleanroomBindingBuilder()); };
    }

    @Override
    public void openScreen(ServerPlayer player, String templatePath, List<ContainerDeclaration> declarations) {
        if (player == null || player.legacy() == null) return;
        register();
        channel.sendTo(new OpenScreenMessage(templatePath, declarations), player.legacy());
    }

    private static class CleanroomBindingBuilder implements AuiBindingBuilder, AuiBindingBuilder.SlotBindingStep, AuiBindingBuilder.FilterableSlotStep {
        @Override public BindingStep player() { return this; }
        @Override public SlotBindingStep saveddata() { return this; }
        @Override public SlotBindingStep saveddata(String dataName) { return this; }
        @Override public SlotBindingStep saveddata(String dataName, int capacity) { return this; }
        @Override public SlotBindingStep blockEntity(BlockPos pos) { return this; }
        @Override public SlotBindingStep blockEntity(BlockPos pos, int capacity) { return this; }
        @Override public SlotBindingStep entity(int entityId) { return this; }
        @Override public SlotBindingStep entity(int entityId, int capacity) { return this; }
        @Override public FilterableSlotStep slot(String selector) { return this; }
        @Override public FilterableSlotStep filter(AuiBindingFilter filter) { return this; }
    }

    public static final class OpenScreenMessage implements IMessage {
        private String path = "";
        public OpenScreenMessage() { }
        private List<ContainerDeclaration> declarations = List.of();
        public OpenScreenMessage(String path) { this(path, List.of()); }
        public OpenScreenMessage(String path, List<ContainerDeclaration> declarations) {
            this.path = path == null ? "" : path;
            this.declarations = declarations == null ? List.of() : List.copyOf(declarations);
        }
        @Override public void fromBytes(ByteBuf buf) {
            path = readString(buf, 32767);
            int count = Math.min(Math.max(buf.readInt(), 0), 128);
            java.util.ArrayList<ContainerDeclaration> decoded = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String id = readString(buf, 256);
                int ordinal = Math.min(Math.max(buf.readByte(), 0), ContainerBindType.values().length - 1);
                int capacity = Math.min(Math.max(buf.readInt(), 0), 4096);
                decoded.add(new ContainerDeclaration(id, ContainerBindType.values()[ordinal], capacity, buf.readBoolean()));
            }
            declarations = List.copyOf(decoded);
        }
        @Override public void toBytes(ByteBuf buf) {
            writeString(buf, path);
            buf.writeInt(declarations.size());
            for (ContainerDeclaration declaration : declarations) {
                writeString(buf, declaration.id());
                buf.writeByte(declaration.bindType().ordinal());
                buf.writeInt(declaration.capacity());
                buf.writeBoolean(declaration.primary());
            }
        }
        private static String readString(ByteBuf buf, int max) {
            int length = Math.min(Math.max(buf.readInt(), 0), max * 4);
            byte[] bytes = new byte[length]; buf.readBytes(bytes); return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        private static void writeString(ByteBuf buf, String value) {
            byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8); buf.writeInt(bytes.length); buf.writeBytes(bytes);
        }
        public static final class Handler implements IMessageHandler<OpenScreenMessage, IMessage> {
            @Override public IMessage onMessage(OpenScreenMessage message, MessageContext context) {
                Minecraft.getMinecraft().addScheduledTask(() -> {
                    if (message.declarations.isEmpty()) {
                        Minecraft.getMinecraft().displayGuiScreen(new ApricityScreen(message.path));
                    } else {
                        ApricityContainerMenu menu = new ApricityContainerMenu(
                                Minecraft.getMinecraft().player.inventory, message.path, message.declarations);
                        Minecraft.getMinecraft().displayGuiScreen(new ApricityContainerScreen(menu, Minecraft.getMinecraft().player.inventory));
                    }
                });
                return null;
            }
        }
    }
}
