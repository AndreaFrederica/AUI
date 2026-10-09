package com.sighs.apricityui.proxy;

import cc.sirrus.apricityui.compat.cleanroom.CleanroomNetworkService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomScriptService;
import com.sighs.apricityui.spi.AuiServices;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy implements IProxy {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        AuiServices.setNetwork(CleanroomNetworkService.INSTANCE);
        AuiServices.setScript(CleanroomScriptService.INSTANCE);
        CleanroomScriptService.INSTANCE.warmUp();
        CleanroomNetworkService.INSTANCE.register();
    }
}

