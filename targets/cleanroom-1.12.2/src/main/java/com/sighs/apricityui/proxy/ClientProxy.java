package com.sighs.apricityui.proxy;

import cc.sirrus.apricityui.compat.cleanroom.CleanroomClientService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomConfigService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomDocumentExpander;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomResourceService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomRenderService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomItemRenderService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomNetworkService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomAudioService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomKeyService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomScriptService;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.dev.DevToolsLogBridge;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.webview.NativeWebViewService;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import com.sighs.apricityui.loader.ClientLoader;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.SoundCategory;
import com.sighs.apricityui.client.CleanroomClientEvents;

public class ClientProxy implements IProxy{
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        AuiServices.setClient(CleanroomClientService.INSTANCE);
        AuiServices.setConfig(CleanroomConfigService.INSTANCE);
        AuiServices.setExpander(CleanroomDocumentExpander.INSTANCE);
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
        AuiServices.setKeys(CleanroomKeyService.INSTANCE);
        CleanroomKeyService.INSTANCE.register();
        DevToolsLogBridge.install(ApricityUI.LOGGER);
        // Cleanroom ships the same optional Windows x64 WebView2 JNI backend as
        // the other targets.  Registration is safe on servers and unsupported
        // platforms because NativeWebViewService reports availability lazily.
        AuiServices.setWebView(NativeWebViewService.INSTANCE);
        try {
            com.sighs.apricityui.ApricityUI.LOGGER.info(
                    "[AUI Cleanroom] WebView2 backend available={} reason={}",
                    NativeWebViewService.INSTANCE.isAvailable(),
                    NativeWebViewService.INSTANCE.unavailableReason());
        } catch (Throwable error) {
            com.sighs.apricityui.ApricityUI.LOGGER.warn(
                    "[AUI Cleanroom] WebView2 backend probe failed", error);
        }
        AuiServices.setScript(CleanroomScriptService.INSTANCE);
        CleanroomScriptService.INSTANCE.warmUp();
        ApricityUIRegistry.scanPackages("com.sighs.apricityui.element");
        ApricityUIRegistry.register();
        CleanroomNetworkService.INSTANCE.register();
        CleanroomClientEvents.register();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        // Forge 1.12 has no FMLClientSetupEvent.  The first client task is the
        // equivalent safe point: the resource manager exists and all mod
        // resource packs have been attached, so HTML/CSS/JS discovery can run.
        String smokePage = System.getProperty("apricityui.testScreen", "").trim();
        CleanroomClientEvents.deferBootstrap(() -> {
            ClientLoader.reloadResources();
            if (!smokePage.isEmpty()) {
                try {
                    AuiServices.client().openScreen(smokePage);
                    com.sighs.apricityui.ApricityUI.LOGGER.info(
                            "[AUI Cleanroom] smoke screen opened: {}", smokePage);
                } catch (Throwable error) {
                    com.sighs.apricityui.ApricityUI.LOGGER.warn(
                        "[AUI Cleanroom] smoke screen failed: {}", smokePage, error);
                }
            }
        });
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

