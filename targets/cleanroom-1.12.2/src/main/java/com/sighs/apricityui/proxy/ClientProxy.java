package com.sighs.apricityui.proxy;

import cc.sirrus.apricityui.compat.cleanroom.CleanroomClientService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomResourceService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomRenderService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomItemRenderService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomNetworkService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.registry.ApricityUIRegistry;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy implements IProxy{
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        AuiServices.setClient(CleanroomClientService.INSTANCE);
        AuiServices.setResources(CleanroomResourceService.INSTANCE);
        AuiServices.setRender(CleanroomRenderService.INSTANCE);
        AuiServices.setItems(CleanroomItemRenderService.INSTANCE);
        AuiServices.setNetwork(CleanroomNetworkService.INSTANCE);
        ApricityUIRegistry.scanPackages("com.sighs.apricityui.element");
        ApricityUIRegistry.register();
        CleanroomNetworkService.INSTANCE.register();
    }
}

