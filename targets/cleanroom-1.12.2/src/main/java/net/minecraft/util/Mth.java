package net.minecraft.util;

/** Small math facade for code shared with modern Minecraft mappings. */
public final class Mth {
    private Mth() { }
    public static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    public static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    public static float lerp(float delta, float start, float end) { return start + delta * (end - start); }
    public static double lerp(double delta, double start, double end) { return start + delta * (end - start); }
    public static float sin(float value) { return (float) Math.sin(value); }
    public static float cos(float value) { return (float) Math.cos(value); }
    public static float sqrt(float value) { return (float) Math.sqrt(value); }
    public static double sqrt(double value) { return Math.sqrt(value); }
    public static float atan2(float y, float x) { return (float) Math.atan2(y, x); }
    public static double atan2(double y, double x) { return Math.atan2(y, x); }
    public static double inverseLerp(double value, double start, double end) {
        return end == start ? 0.0D : (value - start) / (end - start);
    }
    public static double clampedLerp(double start, double end, double delta) {
        return lerp(clamp(delta, 0.0D, 1.0D), start, end);
    }
    public static float clampedLerp(float start, float end, double delta) {
        return (float) clampedLerp((double) start, (double) end, delta);
    }
}
