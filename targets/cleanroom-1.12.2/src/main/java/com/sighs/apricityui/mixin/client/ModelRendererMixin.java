package com.sighs.apricityui.mixin.client;

import cc.sirrus.apricityui.compat.cleanroom.CleanroomModelCapture;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelRenderer.class)
public abstract class ModelRendererMixin {
    @Inject(method = "compileDisplayList", at = @At("HEAD"), cancellable = true)
    private void apricityui$skipDisplayListCompilation(float scale, CallbackInfo callback) {
        if (CleanroomModelCapture.isActive()) callback.cancel();
    }

    @Redirect(method = {"render", "renderWithRotation"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;callList(I)V"))
    private void apricityui$captureModel(int list, float scale) {
        if (!CleanroomModelCapture.part((ModelRenderer) (Object) this, scale)) {
            GlStateManager.callList(list);
        }
    }
}
