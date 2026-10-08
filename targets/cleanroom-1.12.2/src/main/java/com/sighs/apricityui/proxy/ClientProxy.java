package com.sighs.apricityui.proxy;

import cc.sirrus.apricityui.compat.cleanroom.CleanroomClientService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomResourceService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomRenderService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomNetworkService;
import com.sighs.apricityui.spi.AuiServices;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy implements IProxy{
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        AuiServices.setClient(CleanroomClientService.INSTANCE);
        AuiServices.setResources(CleanroomResourceService.INSTANCE);
        AuiServices.setRender(CleanroomRenderService.INSTANCE);
        AuiServices.setNetwork(CleanroomNetworkService.INSTANCE);
        CleanroomNetworkService.INSTANCE.register();
    }
}

