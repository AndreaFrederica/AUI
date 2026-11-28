package com.sighs.apricityui.startup;

import net.minecraftforge.fml.common.eventhandler.Event;

/** Startup loading-page event for the Cleanroom client and NekoJS NativeEvents. */
public final class CleanroomStartupEvent extends Event {
    private final StartupContext context;

    public CleanroomStartupEvent(StartupContext context) {
        this.context = context;
    }

    public StartupContext getContext() {
        return context;
    }
}
