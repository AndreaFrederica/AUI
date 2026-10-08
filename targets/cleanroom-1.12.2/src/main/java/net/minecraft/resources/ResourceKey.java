package net.minecraft.resources;

import net.minecraft.core.Registry;

public final class ResourceKey<T> {
    private final ResourceLocation location;
    private ResourceKey(ResourceLocation location) { this.location = location; }
    public static <T> ResourceKey<T> createRegistryKey(ResourceLocation location) { return new ResourceKey<>(location); }
    public static <T> ResourceKey<T> create(ResourceKey<? extends Registry<T>> registry, ResourceLocation location) { return new ResourceKey<>(location); }
    public ResourceLocation location() { return location; }
}
