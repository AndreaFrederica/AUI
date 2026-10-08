package cc.sirrus.apricityui.compat.cleanroom;

import org.lwjgl.input.Keyboard;
import java.util.Locale;

final class CleanroomInput {
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

    private static Integer functionKey(String key) {
        try {
            int index = Integer.parseInt(key.substring(1));
            return index >= 1 && index <= 19 ? Keyboard.KEY_F1 + index - 1 : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
