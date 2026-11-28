package cc.sirrus.apricityui.compat.cleanroom;

import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.webview.StartupLoadingOverlay;
import org.lwjgl.opengl.Display;

/** The legacy splash owns the GL context on its loading thread. */
public final class CleanroomStartup {
    private static volatile boolean enabled;
    private static volatile boolean confirming;

    private CleanroomStartup() {
    }

    public static void begin() {
        AuiServices.setClient(CleanroomClientService.INSTANCE);
        AuiServices.setRender(CleanroomRenderService.INSTANCE);
        AuiServices.setResources(CleanroomResourceService.INSTANCE);
        enabled = true;
    }

    public static void confirming(boolean value) {
        confirming = value;
    }

    public static void draw() {
        if (!enabled || confirming) return;
        int width = Display.getWidth();
        int height = Display.getHeight();
        StartupLoadingOverlay.draw(null, width, height, width, height);
    }

    public static void finish() {
        if (!enabled) return;
        enabled = false;
        StartupLoadingOverlay.finish();
    }
}
