package net.minecraft.network.chat;

/** Minimal text component facade; Cleanroom renders its string representation. */
public class Component {
    private final String value;
    protected Component(String value) { this.value = value == null ? "" : value; }
    public static Component translatable(String key, Object... args) {
        return new Component(args.length == 0 ? key : String.format(key, args));
    }
    public static Component literal(String value) { return new Component(value); }
    public String getString() { return value; }
    @Override public String toString() { return value; }
}
