package net.minecraft.core;

public final class GlobalPos {
    private final Object dimension;
    private final BlockPos pos;
    public GlobalPos(Object dimension, BlockPos pos) { this.dimension = dimension; this.pos = pos; }
    public Object dimension() { return dimension; }
    public BlockPos pos() { return pos; }
}
