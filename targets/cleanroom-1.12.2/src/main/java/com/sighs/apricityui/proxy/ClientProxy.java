package com.sighs.apricityui.proxy;

import cc.sirrus.apricityui.compat.cleanroom.CleanroomClientService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomResourceService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomRenderService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomItemRenderService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomNetworkService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomAudioService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomScriptService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.SoundCategory;
import com.sighs.apricityui.client.CleanroomClientEvents;

public class ClientProxy implements IProxy{
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        AuiServices.setClient(CleanroomClientService.INSTANCE);
        AuiServices.setResources(CleanroomResourceService.INSTANCE);
        AuiServices.setRender(CleanroomRenderService.INSTANCE);
        AuiServices.setItems(CleanroomItemRenderService.INSTANCE);
        AuiServices.setAudio(CleanroomAudioService.create(() -> {
            try {
                Minecraft mc = Minecraft.getMinecraft();
                return mc == null || mc.gameSettings == null
                        ? 1.0 : mc.gameSettings.getSoundLevel(SoundCategory.MASTER);
            } catch (Throwable ignored) {
                return 1.0;
            }
        }));
        AuiServices.setNetwork(CleanroomNetworkService.INSTANCE);
        AuiServices.setScript(CleanroomScriptService.INSTANCE);
        CleanroomScriptService.INSTANCE.warmUp();
        ApricityUIRegistry.scanPackages("com.sighs.apricityui.element");
        ApricityUIRegistry.register();
        CleanroomNetworkService.INSTANCE.register();
        CleanroomClientEvents.register();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        if (!Boolean.getBoolean("apricityui.cleanroom.shaderProbe")) return;
        // FML init runs after the display/context exists. Compile every AUI
        // shader once so a dev run can verify the real Cleanroom GLSL path.
        try {
            Object filter = CleanroomRenderService.INSTANCE.getFilterShader();
            Object blur = CleanroomRenderService.INSTANCE.getFilterBlurShader();
            Object blend = CleanroomRenderService.INSTANCE.getFilterBlendShader();
            Object mask = CleanroomRenderService.INSTANCE.getFilterMaskShader(false);
            Object maskLuma = CleanroomRenderService.INSTANCE.getFilterMaskShader(true);
            Object intersect = CleanroomRenderService.INSTANCE.getFilterMaskMergeShader(
                    com.sighs.apricityui.spi.AuiRenderService.MaskCompositeOp.INTERSECT);
            Object subtract = CleanroomRenderService.INSTANCE.getFilterMaskMergeShader(
                    com.sighs.apricityui.spi.AuiRenderService.MaskCompositeOp.SUBTRACT);
            Object exclude = CleanroomRenderService.INSTANCE.getFilterMaskMergeShader(
                    com.sighs.apricityui.spi.AuiRenderService.MaskCompositeOp.EXCLUDE);
            com.sighs.apricityui.ApricityUI.LOGGER.info(
                    "[AUI Cleanroom] shader probe filter={} blur={} blend={} mask={} maskLuma={} intersect={} subtract={} exclude={}",
                    filter != null, blur != null, blend != null, mask != null, maskLuma != null,
                    intersect != null, subtract != null, exclude != null);
        } catch (Throwable error) {
            com.sighs.apricityui.ApricityUI.LOGGER.warn("[AUI Cleanroom] shader probe failed", error);
        }
    }
}

