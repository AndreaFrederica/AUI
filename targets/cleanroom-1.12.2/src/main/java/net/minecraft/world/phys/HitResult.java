package net.minecraft.world.phys;

/** Minimal hit result facade used by world-window visibility checks. */
public class HitResult {
    public enum Type { MISS, BLOCK, ENTITY }

    private final Type type;
    private final Vec3 location;

    protected HitResult(Type type, Vec3 location) {
        this.type = type;
        this.location = location;
    }

    public Type getType() {
        return type;
    }

    public Vec3 getLocation() {
        return location;
    }
}
