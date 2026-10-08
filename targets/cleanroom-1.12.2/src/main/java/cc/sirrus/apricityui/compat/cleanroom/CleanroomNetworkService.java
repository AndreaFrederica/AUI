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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
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
        return configurator -> {
            CleanroomBindingBuilder builder = new CleanroomBindingBuilder();
            if (configurator != null) configurator.accept(builder);
            openScreen(player, templatePath, builder.declarations(), builder.argsById());
        };
    }

    @Override
    public void openScreen(ServerPlayer player, String templatePath, List<ContainerDeclaration> declarations) {
        openScreen(player, templatePath, declarations, Map.of());
    }

    private void openScreen(ServerPlayer player, String templatePath,
                            List<ContainerDeclaration> declarations,
                            Map<String, Map<String, String>> argsById) {
        if (player == null || player.legacy() == null) return;
        register();
        net.minecraft.entity.player.EntityPlayerMP legacy = player.legacy();
        int windowId = -1;
        if (declarations != null && !declarations.isEmpty()) {
            ApricityContainerMenu menu = new ApricityContainerMenu(legacy, templatePath, declarations, argsById);
            declarations = menu.getDeclarations();
            legacy.getNextWindowId();
            windowId = legacy.currentWindowId;
            menu.setWindowId(windowId);
            legacy.openContainer = menu;
            legacy.sendContainerToPlayer(menu);
        }
        channel.sendTo(new OpenScreenMessage(templatePath, declarations, windowId, argsById), legacy);
    }

    private static class CleanroomBindingBuilder implements AuiBindingBuilder, AuiBindingBuilder.SlotBindingStep, AuiBindingBuilder.FilterableSlotStep {
        private final List<ContainerDeclaration> declarations = new ArrayList<>();
        private final Map<String, Map<String, String>> argsById = new LinkedHashMap<>();
        private boolean primarySet;

        List<ContainerDeclaration> declarations() { return List.copyOf(declarations); }
        Map<String, Map<String, String>> argsById() { return Map.copyOf(argsById); }
        @Override public BindingStep player() {
            declarations.add(new ContainerDeclaration("player", ContainerBindType.PLAYER, 36, false));
            return this;
        }
        @Override public SlotBindingStep saveddata() { return saveddata("apricityui_data", 9); }
        @Override public SlotBindingStep saveddata(String dataName) { return saveddata(dataName, 9); }
        @Override public SlotBindingStep saveddata(String dataName, int capacity) {
            return declare("saved_data", ContainerBindType.SAVED_DATA, capacity,
                    Map.of("data_name", dataName == null ? "apricityui_data" : dataName));
        }
        @Override public SlotBindingStep blockEntity(BlockPos pos) { return blockEntity(pos, 0); }
        @Override public SlotBindingStep blockEntity(BlockPos pos, int capacity) {
            if (pos == null) return declare("block_entity", ContainerBindType.BLOCK_ENTITY, 0, Map.of());
            return declare("block_entity", ContainerBindType.BLOCK_ENTITY, capacity,
                    Map.of("x", String.valueOf(pos.getX()), "y", String.valueOf(pos.getY()), "z", String.valueOf(pos.getZ())));
        }
        @Override public SlotBindingStep entity(int entityId) { return entity(entityId, 0); }
        @Override public SlotBindingStep entity(int entityId, int capacity) {
            return declare("entity", ContainerBindType.ENTITY, capacity, Map.of("entity_id", String.valueOf(entityId)));
        }
        private SlotBindingStep declare(String id, ContainerBindType type, int capacity, Map<String, String> args) {
            boolean primary = !primarySet;
            primarySet = true;
            declarations.add(new ContainerDeclaration(id, type, Math.max(0, capacity), primary));
            argsById.put(id, Map.copyOf(args));
            return this;
        }
        @Override public FilterableSlotStep slot(String selector) { return this; }
        @Override public FilterableSlotStep filter(AuiBindingFilter filter) { return this; }
    }

    public static final class OpenScreenMessage implements IMessage {
        private String path = "";
        private int windowId = -1;
        public OpenScreenMessage() { }
        private List<ContainerDeclaration> declarations = List.of();
        private Map<String, Map<String, String>> argsById = Map.of();
        public OpenScreenMessage(String path) { this(path, List.of(), -1); }
        public OpenScreenMessage(String path, List<ContainerDeclaration> declarations) {
            this(path, declarations, -1);
        }
        public OpenScreenMessage(String path, List<ContainerDeclaration> declarations, int windowId) {
            this(path, declarations, windowId, Map.of());
        }
        public OpenScreenMessage(String path, List<ContainerDeclaration> declarations, int windowId,
                                 Map<String, Map<String, String>> argsById) {
            this.path = path == null ? "" : path;
            this.declarations = declarations == null ? List.of() : List.copyOf(declarations);
            this.windowId = windowId;
            this.argsById = argsById == null ? Map.of() : Map.copyOf(argsById);
        }
        @Override public void fromBytes(ByteBuf buf) {
            path = readString(buf, 32767);
            windowId = buf.readInt();
            int count = Math.min(Math.max(buf.readInt(), 0), 128);
            java.util.ArrayList<ContainerDeclaration> decoded = new java.util.ArrayList<>(count);
            java.util.LinkedHashMap<String, Map<String, String>> decodedArgs = new java.util.LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                String id = readString(buf, 256);
                int ordinal = Math.min(Math.max(buf.readByte(), 0), ContainerBindType.values().length - 1);
                int capacity = Math.min(Math.max(buf.readInt(), 0), 4096);
                decoded.add(new ContainerDeclaration(id, ContainerBindType.values()[ordinal], capacity, buf.readBoolean()));
                int argCount = Math.min(Math.max(buf.readInt(), 0), 16);
                java.util.LinkedHashMap<String, String> args = new java.util.LinkedHashMap<>();
                for (int arg = 0; arg < argCount; arg++) args.put(readString(buf, 128), readString(buf, 1024));
                decodedArgs.put(id, Map.copyOf(args));
            }
            declarations = List.copyOf(decoded);
            argsById = Map.copyOf(decodedArgs);
        }
        @Override public void toBytes(ByteBuf buf) {
            writeString(buf, path);
            buf.writeInt(windowId);
            buf.writeInt(declarations.size());
            for (ContainerDeclaration declaration : declarations) {
                writeString(buf, declaration.id());
                buf.writeByte(declaration.bindType().ordinal());
                buf.writeInt(declaration.capacity());
                buf.writeBoolean(declaration.primary());
                Map<String, String> args = argsById.getOrDefault(declaration.id(), Map.of());
                buf.writeInt(args.size());
                for (Map.Entry<String, String> arg : args.entrySet()) {
                    writeString(buf, arg.getKey());
                    writeString(buf, arg.getValue());
                }
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
                        menu.setWindowId(message.windowId);
                        // Vanilla 1.12 routes subsequent SPacketSetSlot updates through the
                        // player's openContainer. The screen is opened by our custom packet,
                        // so install the same menu explicitly before displaying the GUI.
                        Minecraft.getMinecraft().player.openContainer = menu;
                        Minecraft.getMinecraft().displayGuiScreen(new ApricityContainerScreen(menu, Minecraft.getMinecraft().player.inventory));
                    }
                });
                return null;
            }
        }
    }
}
