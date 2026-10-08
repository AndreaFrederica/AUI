package com.sighs.apricityui.proxy;

import cc.sirrus.apricityui.compat.cleanroom.CleanroomNetworkService;
import com.sighs.apricityui.spi.AuiServices;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy implements IProxy {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        AuiServices.setNetwork(CleanroomNetworkService.INSTANCE);
        CleanroomNetworkService.INSTANCE.register();
    }
}

