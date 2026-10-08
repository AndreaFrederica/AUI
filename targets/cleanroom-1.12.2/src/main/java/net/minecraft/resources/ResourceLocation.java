package net.minecraft.resources;

import java.util.Objects;

/** Resource identifier facade using the 1.12 namespace:path syntax. */
public class ResourceLocation extends net.minecraft.util.ResourceLocation {
    private final String namespace;
    private final String path;
    public ResourceLocation(String namespace, String path) { super(namespace, path); this.namespace = namespace; this.path = path; }
    public ResourceLocation(String value) {
        this(parseNamespace(value), parsePath(value));
    }
    private static String parseNamespace(String value) { int i = value == null ? -1 : value.indexOf(':'); return i < 0 ? "minecraft" : value.substring(0, i); }
    private static String parsePath(String value) { int i = value == null ? -1 : value.indexOf(':'); return value == null ? "" : (i < 0 ? value : value.substring(i + 1)); }
    public String getNamespace() { return namespace; }
    public String getPath() { return path; }
    @Override public String toString() { return namespace + ":" + path; }
    @Override public boolean equals(Object o) { return o instanceof ResourceLocation r && namespace.equals(r.namespace) && path.equals(r.path); }
    @Override public int hashCode() { return Objects.hash(namespace, path); }
}
