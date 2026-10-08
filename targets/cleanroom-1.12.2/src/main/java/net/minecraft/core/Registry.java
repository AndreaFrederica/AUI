package net.minecraft.core;

import net.minecraft.resources.ResourceLocation;

public interface Registry<T> {
    default ResourceLocation getKey(T value) { return null; }
    default T get(ResourceLocation id) { return null; }
}
