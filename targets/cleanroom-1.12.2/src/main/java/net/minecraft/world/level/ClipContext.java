package net.minecraft.world.level;

import net.minecraft.world.phys.Vec3;

/** Ray trace request facade mapped to World.rayTraceBlocks in the 1.12 backend. */
public final class ClipContext {
    public enum Block { VISUAL }
    public enum Fluid { NONE }

    public final Vec3 from;
    public final Vec3 to;

    public ClipContext(Vec3 from, Vec3 to, Block block, Fluid fluid, Object entity) {
        this.from = from;
        this.to = to;
    }
}
