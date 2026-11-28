package com.sighs.apricityui.mixin.client;

import cc.sirrus.apricityui.compat.cleanroom.CleanroomStartup;
import net.minecraftforge.fml.client.SplashProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SplashProgress.class, remap = false)
public abstract class SplashProgressMixin {
    @Inject(method = "start", at = @At("HEAD"))
    private static void apricityui$begin(CallbackInfo callback) {
        CleanroomStartup.begin();
    }

    @Inject(method = "finish", at = @At("RETURN"))
    private static void apricityui$finish(CallbackInfo callback) {
        CleanroomStartup.finish();
    }

    @Inject(method = "confirm", at = @At("HEAD"))
    private static void apricityui$showPrompt(String warning, String prompt, CallbackInfoReturnable<Boolean> callback) {
        CleanroomStartup.confirming(true);
    }

    @Inject(method = "confirm", at = @At("RETURN"))
    private static void apricityui$closePrompt(String warning, String prompt, CallbackInfoReturnable<Boolean> callback) {
        CleanroomStartup.confirming(false);
    }
}
