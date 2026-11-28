package com.sighs.apricityui.mixin.client;

import cc.sirrus.apricityui.compat.cleanroom.CleanroomModelCapture;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Tessellator.class)
public abstract class TessellatorMixin {
    @Shadow public abstract BufferBuilder getBuffer();

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void apricityui$captureVertices(CallbackInfo callback) {
        if (CleanroomModelCapture.vertices(getBuffer())) {
            getBuffer().finishDrawing();
            getBuffer().reset();
            callback.cancel();
        }
    }
}
