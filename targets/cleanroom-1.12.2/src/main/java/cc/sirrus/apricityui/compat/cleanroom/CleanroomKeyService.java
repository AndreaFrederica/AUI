package cc.sirrus.apricityui.compat.cleanroom;

import com.sighs.apricityui.spi.AuiKeyService;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;

/**
 * Cleanroom 1.12.2 implementation of {@link AuiKeyService}, mirroring Forge 1.20.1's
 * {@code Keybindings}/{@code KeyService}. Translates LWJGL2 key codes to GLFW codes
 * for common AUI handlers.
 */
public final class CleanroomKeyService implements AuiKeyService {
    public static final CleanroomKeyService INSTANCE = new CleanroomKeyService();
    private static final String CATEGORY = "key.categories.apricityui";

    public static final KeyBinding RELEASE_MOUSE = new KeyBinding(
            "key.apricityui.release_mouse", Keyboard.KEY_LMENU, CATEGORY);
    public static final KeyBinding RELOAD = new KeyBinding(
            "key.apricityui.reload", Keyboard.KEY_NONE, CATEGORY);
    public static final KeyBinding DEV_TOOLS = new KeyBinding(
            "key.apricityui.dev_tools", Keyboard.KEY_NONE, CATEGORY);
    public static final KeyBinding RESOURCE_MANAGER = new KeyBinding(
            "key.apricityui.resource_manager", Keyboard.KEY_NONE, CATEGORY);

    private boolean registered;

    private CleanroomKeyService() {
    }

    public void register() {
        if (registered) return;
        registered = true;
        ClientRegistry.registerKeyBinding(RELEASE_MOUSE);
        ClientRegistry.registerKeyBinding(RELOAD);
        ClientRegistry.registerKeyBinding(DEV_TOOLS);
        ClientRegistry.registerKeyBinding(RESOURCE_MANAGER);
    }

    @Override
    public boolean isReleaseMouseDown() {
        int code = RELEASE_MOUSE.getKeyCode();
        if (code <= Keyboard.KEY_NONE || !Keyboard.isCreated()) return false;
        return Keyboard.isKeyDown(code);
    }

    @Override
    public int devToolsKey() {
        return toGlfwKeyOrUnbound(DEV_TOOLS);
    }

    @Override
    public int resourceManagerKey() {
        return toGlfwKeyOrUnbound(RESOURCE_MANAGER);
    }

    @Override
    public int reloadKey() {
        return toGlfwKeyOrUnbound(RELOAD);
    }

    private static int toGlfwKeyOrUnbound(KeyBinding binding) {
        if (binding == null) return -1;
        int code = binding.getKeyCode();
        if (code <= Keyboard.KEY_NONE) return -1;
        return CleanroomInput.toGlfwKey(code);
    }
}
