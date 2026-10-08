package net.minecraft.world.phys;

/** Immutable high-version vector facade backed by the 1.12 vector semantics. */
public final class Vec3 {
    public final double x;
    public final double y;
    public final double z;

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 subtract(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    public Vec3 scale(double value) {
        return new Vec3(x * value, y * value, z * value);
    }

    public double dot(Vec3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public double distanceTo(Vec3 other) {
        return subtract(other).length();
    }

    public double distanceToSqr(Vec3 other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public net.minecraft.util.math.Vec3d toLegacy() {
        return new net.minecraft.util.math.Vec3d(x, y, z);
    }

    public static Vec3 fromLegacy(net.minecraft.util.math.Vec3d value) {
        return value == null ? null : new Vec3(value.x, value.y, value.z);
    }

    @Override
    public String toString() {
        return "Vec3{" + x + ", " + y + ", " + z + '}';
    }
}
