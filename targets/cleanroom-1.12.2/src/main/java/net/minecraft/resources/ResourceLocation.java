package net.minecraft.resources;

/**
 * Resource identifier facade using the 1.12 {@link net.minecraft.util.ResourceLocation}
 * base class directly so {@code equals}/{@code hashCode} stay 100% interoperable with
 * Minecraft 1.12's {@code TextureManager} and {@code IResourceManager}.
 */
public class ResourceLocation extends net.minecraft.util.ResourceLocation {
    public ResourceLocation(String namespace, String path) {
        super(namespace == null || namespace.isBlank() ? "minecraft" : namespace, path == null ? "" : path);
    }

    public ResourceLocation(String value) {
        super(value == null ? "" : value);
    }
}
