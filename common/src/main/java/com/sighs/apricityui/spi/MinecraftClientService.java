package com.sighs.apricityui.spi;

import com.mojang.blaze3d.platform.Window;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.screen.AuiLinkedScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Shared client operations for loaders using the Mojang-mapped Minecraft client API. */
public interface MinecraftClientService extends AuiClientService {
    @Override
    default Window getWindow() {
        Minecraft client = Minecraft.getInstance();
        return client == null ? null : client.getWindow();
    }

    @Override
    default boolean isClientGameplayActive() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.level != null && ClientScreenAccess.screen(client) == null
                && !ClientScreenAccess.hasOverlay(client) && client.isWindowActive();
    }

    @Override
    default void releaseMouse() {
        Minecraft.getInstance().mouseHandler.releaseMouse();
    }

    @Override
    default void grabMouse() {
        Minecraft.getInstance().mouseHandler.grabMouse();
    }

    @Override
    default String getClipboardText() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.keyboardHandler == null) return "";
        String text = client.keyboardHandler.getClipboard();
        return text == null ? "" : text;
    }

    @Override
    default void setClipboardText(String text) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.keyboardHandler != null) {
            client.keyboardHandler.setClipboard(text == null ? "" : text);
        }
    }

    @Override
    default void execute(Runnable runnable) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && runnable != null) client.execute(runnable);
    }

    @Override
    default Document getCurrentScreenDocument() {
        Minecraft client = Minecraft.getInstance();
        return client != null && ClientScreenAccess.screen(client) instanceof AuiLinkedScreen screen
                ? screen.getLinkedDocument() : null;
    }

    @Override
    default boolean hasClientWorld() {
        Minecraft client = Minecraft.getInstance();
        return client != null && client.level != null;
    }

    @Override
    default boolean isWorldOccluded(Vec3 origin, Vec3 target, double targetDistance) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null) return true;
        BlockHitResult hit = client.level.clip(new ClipContext(
                origin, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, client.player));
        return hit.getType() != HitResult.Type.MISS
                && origin.distanceTo(hit.getLocation()) + 1.0e-4d < targetDistance;
    }
}
