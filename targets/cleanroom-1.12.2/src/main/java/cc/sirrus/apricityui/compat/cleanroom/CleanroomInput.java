package cc.sirrus.apricityui.compat.cleanroom;

import org.lwjgl.input.Keyboard;
import java.util.Locale;

public final class CleanroomInput {
    private CleanroomInput() { }

    static Integer keyCode(String name) {
        if (name == null) return null;
        String key = name.toLowerCase(Locale.ROOT);
        if (key.length() == 1) return Keyboard.getKeyIndex(key.toUpperCase(Locale.ROOT));
        return switch (key) {
            case "space" -> Keyboard.KEY_SPACE;
            case "enter" -> Keyboard.KEY_RETURN;
            case "escape" -> Keyboard.KEY_ESCAPE;
            case "backspace" -> Keyboard.KEY_BACK;
            case "tab" -> Keyboard.KEY_TAB;
            case "left" -> Keyboard.KEY_LEFT;
            case "right" -> Keyboard.KEY_RIGHT;
            case "up" -> Keyboard.KEY_UP;
            case "down" -> Keyboard.KEY_DOWN;
            case "shift" -> Keyboard.KEY_LSHIFT;
            case "control", "ctrl" -> Keyboard.KEY_LCONTROL;
            case "alt" -> Keyboard.KEY_LMENU;
            default -> key.startsWith("f") ? functionKey(key) : null;
        };
    }

    /** Converts a LWJGL2 Keyboard code to the GLFW code used by common AUI. */
    public static int toGlfwKey(int key) {
        if (key >= Keyboard.KEY_0 && key <= Keyboard.KEY_9) return org.lwjgl.glfw.GLFW.GLFW_KEY_0 + key - Keyboard.KEY_0;
        if (key >= Keyboard.KEY_A && key <= Keyboard.KEY_Z) return org.lwjgl.glfw.GLFW.GLFW_KEY_A + key - Keyboard.KEY_A;
        return switch (key) {
            case Keyboard.KEY_SPACE -> org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
            case Keyboard.KEY_RETURN -> org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
            case Keyboard.KEY_ESCAPE -> org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
            case Keyboard.KEY_BACK -> org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
            case Keyboard.KEY_DELETE -> org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE;
            case Keyboard.KEY_TAB -> org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
            case Keyboard.KEY_LEFT -> org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
            case Keyboard.KEY_RIGHT -> org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
            case Keyboard.KEY_UP -> org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
            case Keyboard.KEY_DOWN -> org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
            case Keyboard.KEY_HOME -> org.lwjgl.glfw.GLFW.GLFW_KEY_HOME;
            case Keyboard.KEY_END -> org.lwjgl.glfw.GLFW.GLFW_KEY_END;
            case Keyboard.KEY_PRIOR -> org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP;
            case Keyboard.KEY_NEXT -> org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN;
            case Keyboard.KEY_LSHIFT, Keyboard.KEY_RSHIFT -> org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
            case Keyboard.KEY_LCONTROL, Keyboard.KEY_RCONTROL -> org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
            case Keyboard.KEY_LMENU, Keyboard.KEY_RMENU -> org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT;
            case Keyboard.KEY_F1 -> org.lwjgl.glfw.GLFW.GLFW_KEY_F1;
            case Keyboard.KEY_F2 -> org.lwjgl.glfw.GLFW.GLFW_KEY_F2;
            case Keyboard.KEY_F3 -> org.lwjgl.glfw.GLFW.GLFW_KEY_F3;
            case Keyboard.KEY_F4 -> org.lwjgl.glfw.GLFW.GLFW_KEY_F4;
            case Keyboard.KEY_F5 -> org.lwjgl.glfw.GLFW.GLFW_KEY_F5;
            case Keyboard.KEY_F6 -> org.lwjgl.glfw.GLFW.GLFW_KEY_F6;
            case Keyboard.KEY_F7 -> org.lwjgl.glfw.GLFW.GLFW_KEY_F7;
            case Keyboard.KEY_F8 -> org.lwjgl.glfw.GLFW.GLFW_KEY_F8;
            case Keyboard.KEY_F9 -> org.lwjgl.glfw.GLFW.GLFW_KEY_F9;
            case Keyboard.KEY_F10 -> org.lwjgl.glfw.GLFW.GLFW_KEY_F10;
            case Keyboard.KEY_F11 -> org.lwjgl.glfw.GLFW.GLFW_KEY_F11;
            case Keyboard.KEY_F12 -> org.lwjgl.glfw.GLFW.GLFW_KEY_F12;
            default -> -1;
        };
    }

    private static Integer functionKey(String key) {
        try {
            int index = Integer.parseInt(key.substring(1));
            return index >= 1 && index <= 19 ? Keyboard.KEY_F1 + index - 1 : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
