package net.minecraft.core.registries;

import net.minecraft.core.Registry;

public final class BuiltInRegistries {
    private BuiltInRegistries() { }
    public static final Registry<Object> ITEM = new Registry<>() { };
}
