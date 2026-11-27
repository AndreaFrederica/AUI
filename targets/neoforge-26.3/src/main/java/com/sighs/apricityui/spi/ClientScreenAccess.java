package com.sighs.apricityui.spi;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

final class ClientScreenAccess {
    private ClientScreenAccess() {
    }

    static Screen screen(Minecraft client) {
        return client.gui.screen();
    }

    static boolean hasOverlay(Minecraft client) {
        return client.gui.overlay() != null;
    }
}
