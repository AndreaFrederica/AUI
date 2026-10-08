package net.minecraft.core;

/** Version-neutral block coordinate value. */
public class BlockPos extends net.minecraft.util.math.BlockPos {
    public final int x;
    public final int y;
    public final int z;
    public BlockPos(int x, int y, int z) { super(x, y, z); this.x = x; this.y = y; this.z = z; }
    public net.minecraft.util.math.BlockPos toLegacy() { return new net.minecraft.util.math.BlockPos(x, y, z); }
}
