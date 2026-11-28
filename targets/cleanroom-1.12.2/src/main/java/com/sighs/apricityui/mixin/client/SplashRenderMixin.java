package com.sighs.apricityui.mixin.client;

import cc.sirrus.apricityui.compat.cleanroom.CleanroomStartup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraftforge.fml.client.SplashProgress$2", remap = false)
public abstract class SplashRenderMixin {
    @Inject(method = "run", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;update()V"))
    private void apricityui$draw(CallbackInfo callback) {
        CleanroomStartup.draw();
    }
}
