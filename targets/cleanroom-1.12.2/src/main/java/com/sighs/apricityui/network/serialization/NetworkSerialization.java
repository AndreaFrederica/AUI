package com.sighs.apricityui.network.serialization;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.element.ContainerDeclaration;
import com.sighs.apricityui.network.codec.StreamCodec;
import com.sighs.apricityui.network.packet.OpenScreenRequestPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Compact Cleanroom codec replacing the 1.20 Mojang Codec graph.  It covers
 * AUI's packet records and primitive record components while retaining the
 * same StreamCodec entry point used by generic chunk assembly.
 */
public final class NetworkSerialization {
    private NetworkSerialization() { }

    @SuppressWarnings("unchecked")
    public static <T> StreamCodec<FriendlyByteBuf, T> autoCodec(Class<T> type) {
        if (!type.isRecord()) throw new IllegalArgumentException("codec requires a record: " + type.getName());
        return new StreamCodec<>() {
            @Override public T decode(FriendlyByteBuf buf) {
                try {
                    RecordComponent[] components = type.getRecordComponents();
                    Object[] values = new Object[components.length];
                    for (int i = 0; i < components.length; i++) values[i] = read(buf, components[i].getType());
                    Class<?>[] signature = new Class<?>[components.length];
                    for (int i = 0; i < components.length; i++) signature[i] = components[i].getType();
                    Constructor<T> constructor = type.getDeclaredConstructor(signature);
                    constructor.setAccessible(true);
                    return constructor.newInstance(values);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Unable to decode " + type.getName(), exception);
                }
            }
            @Override public void encode(FriendlyByteBuf buf, T value) {
                try {
                    for (RecordComponent component : type.getRecordComponents()) {
                        write(buf, component.getType(), component.getAccessor().invoke(value));
                    }
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Unable to encode " + type.getName(), exception);
                }
            }
        };
    }

    private static Object read(FriendlyByteBuf buf, Class<?> type) {
        if (type == String.class) return buf.readUtf();
        if (type == int.class || type == Integer.class) return buf.readVarInt();
        if (type == short.class || type == Short.class) return buf.readShort();
        if (type == long.class || type == Long.class) return buf.readLong();
        if (type == boolean.class || type == Boolean.class) return buf.readBoolean();
        if (type == UUID.class) return buf.readUUID();
        if (type == ResourceLocation.class) return buf.readResourceLocation();
        if (type == byte[].class) return buf.readByteArray();
        if (type == List.class) {
            int count = buf.readVarInt();
            ArrayList<ContainerDeclaration> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String id = buf.readUtf(256);
                int ordinal = Math.max(0, Math.min(buf.readVarInt(), ContainerBindType.values().length - 1));
                result.add(new ContainerDeclaration(id, ContainerBindType.values()[ordinal], buf.readVarInt(), buf.readBoolean()));
            }
            return List.copyOf(result);
        }
        throw new IllegalArgumentException("Unsupported Cleanroom packet field: " + type.getName());
    }

    @SuppressWarnings("unchecked")
    private static void write(FriendlyByteBuf buf, Class<?> type, Object value) {
        if (type == String.class) { buf.writeUtf(value == null ? "" : (String) value); return; }
        if (type == int.class || type == Integer.class) { buf.writeVarInt((Integer) value); return; }
        if (type == short.class || type == Short.class) { buf.writeShort((Short) value); return; }
        if (type == long.class || type == Long.class) { buf.writeLong((Long) value); return; }
        if (type == boolean.class || type == Boolean.class) { buf.writeBoolean((Boolean) value); return; }
        if (type == UUID.class) { buf.writeUUID((UUID) value); return; }
        if (type == ResourceLocation.class) { buf.writeResourceLocation((ResourceLocation) value); return; }
        if (type == byte[].class) { buf.writeByteArray((byte[]) value); return; }
        if (type == List.class) {
            List<ContainerDeclaration> declarations = (List<ContainerDeclaration>) value;
            buf.writeVarInt(declarations.size());
            for (ContainerDeclaration declaration : declarations) {
                buf.writeUtf(declaration.id());
                buf.writeVarInt(declaration.bindType().ordinal());
                buf.writeVarInt(declaration.capacity());
                buf.writeBoolean(declaration.primary());
            }
            return;
        }
        throw new IllegalArgumentException("Unsupported Cleanroom packet field: " + type.getName());
    }
}
