package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/** 1.20 FriendlyByteBuf method names over a Netty buffer. */
public class FriendlyByteBuf extends PacketBuffer {
    public FriendlyByteBuf(ByteBuf delegate) { super(delegate); }
    public String readUtf() { return readString(32767); }
    public String readUtf(int maxLength) { return readString(maxLength); }
    public FriendlyByteBuf writeUtf(String value) { writeString(value); return this; }
    public FriendlyByteBuf writeUtf(String value, int maxLength) { writeString(value); return this; }
    public UUID readUUID() { return new UUID(readLong(), readLong()); }
    public FriendlyByteBuf writeUUID(UUID value) { writeLong(value.getMostSignificantBits()); writeLong(value.getLeastSignificantBits()); return this; }
    public byte[] readByteArray() { return readByteArray(16 * 1024 * 1024); }
    public byte[] readByteArray(int max) { int length = readVarInt(); if (length < 0 || length > max) throw new IllegalArgumentException("Invalid byte array length " + length); byte[] value = new byte[length]; readBytes(value); return value; }
    public FriendlyByteBuf writeByteArray(byte[] value) { writeVarInt(value.length); writeBytes(value); return this; }
    public int[] readVarIntArray() { int[] values = new int[readVarInt()]; for (int i = 0; i < values.length; i++) values[i] = readVarInt(); return values; }
    public FriendlyByteBuf writeVarIntArray(int[] values) { writeVarInt(values.length); for (int value : values) writeVarInt(value); return this; }
    public long[] readLongArray() { long[] values = new long[readVarInt()]; for (int i = 0; i < values.length; i++) values[i] = readLong(); return values; }
    public FriendlyByteBuf writeLongArray(long[] values) { writeVarInt(values.length); for (long value : values) writeLong(value); return this; }
    public int[] readIntIdList() { return readVarIntArray(); }
    public FriendlyByteBuf writeIntIdList(int[] values) { return writeVarIntArray(values); }
    public Date readDate() { return new Date(readLong()); }
    public FriendlyByteBuf writeDate(Date value) { writeLong(value.getTime()); return this; }
    public Instant readInstant() { return Instant.ofEpochMilli(readLong()); }
    public FriendlyByteBuf writeInstant(Instant value) { writeLong(value.toEpochMilli()); return this; }
    public BitSet readBitSet() { return BitSet.valueOf(readLongArray()); }
    public FriendlyByteBuf writeBitSet(BitSet value) { return writeLongArray(value.toLongArray()); }
    public <T extends Enum<T>> T readEnum(Class<T> type) { return type.getEnumConstants()[readVarInt()]; }
    public FriendlyByteBuf writeEnum(Enum<?> value) { writeVarInt(value.ordinal()); return this; }
    public BlockPos readBlockPos() { return new BlockPos(readInt(), readInt(), readInt()); }
    public FriendlyByteBuf writeBlockPos(BlockPos value) { writeInt(value.x); writeInt(value.y); writeInt(value.z); return this; }
    public ChunkPos readChunkPos() { return new ChunkPos(readInt(), readInt()); }
    public FriendlyByteBuf writeChunkPos(ChunkPos value) { writeInt(value.x); writeInt(value.z); return this; }
    public SectionPos readSectionPos() { return new SectionPos(readInt(), readInt(), readInt()); }
    public FriendlyByteBuf writeSectionPos(SectionPos value) { writeInt(value.x); writeInt(value.y); writeInt(value.z); return this; }
    public GlobalPos readGlobalPos() { return new GlobalPos(readResourceLocation(), readBlockPos()); }
    public FriendlyByteBuf writeGlobalPos(GlobalPos value) { writeResourceLocation((ResourceLocation) value.dimension()); writeBlockPos(value.pos()); return this; }
    public ResourceLocation readResourceLocation() { return new ResourceLocation(readUtf()); }
    public FriendlyByteBuf writeResourceLocation(ResourceLocation value) { return writeUtf(value.toString()); }
    public Vector3f readVector3f() { return new Vector3f(readFloat(), readFloat(), readFloat()); }
    public FriendlyByteBuf writeVector3f(Vector3f value) { writeFloat(value.x()); writeFloat(value.y()); writeFloat(value.z()); return this; }
    public Quaternionf readQuaternion() { return new Quaternionf(readFloat(), readFloat(), readFloat(), readFloat()); }
    public FriendlyByteBuf writeQuaternion(Quaternionf value) { writeFloat(value.x); writeFloat(value.y); writeFloat(value.z); writeFloat(value.w); return this; }
    public CompoundTag readNbt() { return new CompoundTag(); }
    public FriendlyByteBuf writeNbt(CompoundTag value) { return this; }
    public ItemStack readItem() { return ItemStack.EMPTY; }
    public FriendlyByteBuf writeItem(ItemStack value) { return this; }
    public Component readComponent() { return Component.literal(readUtf()); }
    public FriendlyByteBuf writeComponent(Component value) { return writeUtf(value.getString()); }
    public <T> ResourceKey<T> readResourceKey(ResourceKey<? extends Registry<T>> key) { return ResourceKey.createRegistryKey(readResourceLocation()); }
    public FriendlyByteBuf writeResourceKey(ResourceKey<?> key) { return writeResourceLocation(key.location()); }
    public BlockHitResult readBlockHitResult() { return new BlockHitResult(net.minecraft.world.phys.HitResult.Type.MISS, new net.minecraft.world.phys.Vec3(0, 0, 0)); }
    public FriendlyByteBuf writeBlockHitResult(BlockHitResult value) { return this; }
    public <T> List<T> readList(Function<FriendlyByteBuf, T> reader) { int n = readVarInt(); List<T> result = new ArrayList<>(n); for (int i = 0; i < n; i++) result.add(reader.apply(this)); return result; }
    public <T> FriendlyByteBuf writeCollection(Collection<T> value, java.util.function.BiConsumer<FriendlyByteBuf, T> writer) { writeVarInt(value.size()); value.forEach(v -> writer.accept(this, v)); return this; }
    public <T> Collection<T> readCollection(Supplier<Collection<T>> factory, Function<FriendlyByteBuf, T> reader) { Collection<T> result = factory.get(); int n = readVarInt(); for (int i = 0; i < n; i++) result.add(reader.apply(this)); return result; }
    public <K, V> Map<K, V> readMap(Supplier<Map<K, V>> factory, Function<FriendlyByteBuf, K> keyReader, Function<FriendlyByteBuf, V> valueReader) { Map<K, V> result = factory.get(); int n = readVarInt(); for (int i = 0; i < n; i++) result.put(keyReader.apply(this), valueReader.apply(this)); return result; }
    public <K, V> FriendlyByteBuf writeMap(Map<K, V> value, java.util.function.BiConsumer<FriendlyByteBuf, K> keyWriter, java.util.function.BiConsumer<FriendlyByteBuf, V> valueWriter) { writeVarInt(value.size()); value.forEach((k, v) -> { keyWriter.accept(this, k); valueWriter.accept(this, v); }); return this; }
}
