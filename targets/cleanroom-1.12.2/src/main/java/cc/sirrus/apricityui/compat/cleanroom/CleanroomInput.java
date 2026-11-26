package cc.sirrus.apricityui.compat.cleanroom;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.input.Keyboard;

import java.util.Locale;

/**
 * Bidirectional input translation between LWJGL2 {@link Keyboard} scancodes
 * (DirectInput order, non-contiguous!) and LWJGL3/GLFW keycodes & modifiers
 * expected by common AUI.
 */
public final class CleanroomInput {
    private CleanroomInput() { }

    static Integer keyCode(String name) {
        if (name == null || name.isBlank()) return null;
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (key.startsWith("key.keyboard.")) {
            key = key.substring("key.keyboard.".length());
        }
        return switch (key) {
            case "0" -> Keyboard.KEY_0;
            case "1" -> Keyboard.KEY_1;
            case "2" -> Keyboard.KEY_2;
            case "3" -> Keyboard.KEY_3;
            case "4" -> Keyboard.KEY_4;
            case "5" -> Keyboard.KEY_5;
            case "6" -> Keyboard.KEY_6;
            case "7" -> Keyboard.KEY_7;
            case "8" -> Keyboard.KEY_8;
            case "9" -> Keyboard.KEY_9;
            case "a" -> Keyboard.KEY_A;
            case "b" -> Keyboard.KEY_B;
            case "c" -> Keyboard.KEY_C;
            case "d" -> Keyboard.KEY_D;
            case "e" -> Keyboard.KEY_E;
            case "f" -> Keyboard.KEY_F;
            case "g" -> Keyboard.KEY_G;
            case "h" -> Keyboard.KEY_H;
            case "i" -> Keyboard.KEY_I;
            case "j" -> Keyboard.KEY_J;
            case "k" -> Keyboard.KEY_K;
            case "l" -> Keyboard.KEY_L;
            case "m" -> Keyboard.KEY_M;
            case "n" -> Keyboard.KEY_N;
            case "o" -> Keyboard.KEY_O;
            case "p" -> Keyboard.KEY_P;
            case "q" -> Keyboard.KEY_Q;
            case "r" -> Keyboard.KEY_R;
            case "s" -> Keyboard.KEY_S;
            case "t" -> Keyboard.KEY_T;
            case "u" -> Keyboard.KEY_U;
            case "v" -> Keyboard.KEY_V;
            case "w" -> Keyboard.KEY_W;
            case "x" -> Keyboard.KEY_X;
            case "y" -> Keyboard.KEY_Y;
            case "z" -> Keyboard.KEY_Z;
            case "space" -> Keyboard.KEY_SPACE;
            case "enter", "return" -> Keyboard.KEY_RETURN;
            case "escape", "esc" -> Keyboard.KEY_ESCAPE;
            case "backspace", "back" -> Keyboard.KEY_BACK;
            case "delete", "del" -> Keyboard.KEY_DELETE;
            case "insert", "ins" -> Keyboard.KEY_INSERT;
            case "tab" -> Keyboard.KEY_TAB;
            case "left" -> Keyboard.KEY_LEFT;
            case "right" -> Keyboard.KEY_RIGHT;
            case "up" -> Keyboard.KEY_UP;
            case "down" -> Keyboard.KEY_DOWN;
            case "home" -> Keyboard.KEY_HOME;
            case "end" -> Keyboard.KEY_END;
            case "page.up", "pageup", "prior" -> Keyboard.KEY_PRIOR;
            case "page.down", "pagedown", "next" -> Keyboard.KEY_NEXT;
            case "shift", "left.shift", "lshift" -> Keyboard.KEY_LSHIFT;
            case "right.shift", "rshift" -> Keyboard.KEY_RSHIFT;
            case "control", "ctrl", "left.control", "lcontrol" -> Keyboard.KEY_LCONTROL;
            case "right.control", "rcontrol" -> Keyboard.KEY_RCONTROL;
            case "alt", "left.alt", "lalt", "lmenu" -> Keyboard.KEY_LMENU;
            case "right.alt", "ralt", "rmenu" -> Keyboard.KEY_RMENU;
            case "left.win", "left.super", "lmeta" -> Keyboard.KEY_LMETA;
            case "right.win", "right.super", "rmeta" -> Keyboard.KEY_RMETA;
            case "caps.lock", "capital" -> Keyboard.KEY_CAPITAL;
            case "num.lock", "numlock" -> Keyboard.KEY_NUMLOCK;
            case "scroll.lock", "scroll" -> Keyboard.KEY_SCROLL;
            case "pause" -> Keyboard.KEY_PAUSE;
            case "print.screen", "sysrq" -> Keyboard.KEY_SYSRQ;
            case "minus", "-" -> Keyboard.KEY_MINUS;
            case "equal", "equals", "=" -> Keyboard.KEY_EQUALS;
            case "left.bracket", "[" -> Keyboard.KEY_LBRACKET;
            case "right.bracket", "]" -> Keyboard.KEY_RBRACKET;
            case "semicolon", ";" -> Keyboard.KEY_SEMICOLON;
            case "apostrophe", "'" -> Keyboard.KEY_APOSTROPHE;
            case "grave.accent", "grave", "`" -> Keyboard.KEY_GRAVE;
            case "backslash", "\\" -> Keyboard.KEY_BACKSLASH;
            case "comma", "," -> Keyboard.KEY_COMMA;
            case "period", "." -> Keyboard.KEY_PERIOD;
            case "slash", "/" -> Keyboard.KEY_SLASH;
            case "keypad.0", "kp.0" -> Keyboard.KEY_NUMPAD0;
            case "keypad.1", "kp.1" -> Keyboard.KEY_NUMPAD1;
            case "keypad.2", "kp.2" -> Keyboard.KEY_NUMPAD2;
            case "keypad.3", "kp.3" -> Keyboard.KEY_NUMPAD3;
            case "keypad.4", "kp.4" -> Keyboard.KEY_NUMPAD4;
            case "keypad.5", "kp.5" -> Keyboard.KEY_NUMPAD5;
            case "keypad.6", "kp.6" -> Keyboard.KEY_NUMPAD6;
            case "keypad.7", "kp.7" -> Keyboard.KEY_NUMPAD7;
            case "keypad.8", "kp.8" -> Keyboard.KEY_NUMPAD8;
            case "keypad.9", "kp.9" -> Keyboard.KEY_NUMPAD9;
            case "keypad.decimal", "kp.decimal" -> Keyboard.KEY_DECIMAL;
            case "keypad.divide", "kp.divide" -> Keyboard.KEY_DIVIDE;
            case "keypad.multiply", "kp.multiply" -> Keyboard.KEY_MULTIPLY;
            case "keypad.subtract", "kp.subtract" -> Keyboard.KEY_SUBTRACT;
            case "keypad.add", "kp.add" -> Keyboard.KEY_ADD;
            case "keypad.enter", "kp.enter" -> Keyboard.KEY_NUMPADENTER;
            case "keypad.equal", "kp.equal" -> Keyboard.KEY_NUMPADEQUALS;
            default -> functionKey(key);
        };
    }

    /**
     * Converts a LWJGL2 {@link Keyboard} key code to the GLFW key code used by common AUI.
     * LWJGL2 codes follow DirectInput physical scancode order (e.g. KEY_A=30, KEY_Z=44,
     * KEY_LSHIFT=42, KEY_0=11 > KEY_9=10), so every key must be mapped explicitly.
     */
    public static int toGlfwKey(int key) {
        return switch (key) {
            case Keyboard.KEY_0 -> GLFW.GLFW_KEY_0;
            case Keyboard.KEY_1 -> GLFW.GLFW_KEY_1;
            case Keyboard.KEY_2 -> GLFW.GLFW_KEY_2;
            case Keyboard.KEY_3 -> GLFW.GLFW_KEY_3;
            case Keyboard.KEY_4 -> GLFW.GLFW_KEY_4;
            case Keyboard.KEY_5 -> GLFW.GLFW_KEY_5;
            case Keyboard.KEY_6 -> GLFW.GLFW_KEY_6;
            case Keyboard.KEY_7 -> GLFW.GLFW_KEY_7;
            case Keyboard.KEY_8 -> GLFW.GLFW_KEY_8;
            case Keyboard.KEY_9 -> GLFW.GLFW_KEY_9;

            case Keyboard.KEY_A -> GLFW.GLFW_KEY_A;
            case Keyboard.KEY_B -> GLFW.GLFW_KEY_B;
            case Keyboard.KEY_C -> GLFW.GLFW_KEY_C;
            case Keyboard.KEY_D -> GLFW.GLFW_KEY_D;
            case Keyboard.KEY_E -> GLFW.GLFW_KEY_E;
            case Keyboard.KEY_F -> GLFW.GLFW_KEY_F;
            case Keyboard.KEY_G -> GLFW.GLFW_KEY_G;
            case Keyboard.KEY_H -> GLFW.GLFW_KEY_H;
            case Keyboard.KEY_I -> GLFW.GLFW_KEY_I;
            case Keyboard.KEY_J -> GLFW.GLFW_KEY_J;
            case Keyboard.KEY_K -> GLFW.GLFW_KEY_K;
            case Keyboard.KEY_L -> GLFW.GLFW_KEY_L;
            case Keyboard.KEY_M -> GLFW.GLFW_KEY_M;
            case Keyboard.KEY_N -> GLFW.GLFW_KEY_N;
            case Keyboard.KEY_O -> GLFW.GLFW_KEY_O;
            case Keyboard.KEY_P -> GLFW.GLFW_KEY_P;
            case Keyboard.KEY_Q -> GLFW.GLFW_KEY_Q;
            case Keyboard.KEY_R -> GLFW.GLFW_KEY_R;
            case Keyboard.KEY_S -> GLFW.GLFW_KEY_S;
            case Keyboard.KEY_T -> GLFW.GLFW_KEY_T;
            case Keyboard.KEY_U -> GLFW.GLFW_KEY_U;
            case Keyboard.KEY_V -> GLFW.GLFW_KEY_V;
            case Keyboard.KEY_W -> GLFW.GLFW_KEY_W;
            case Keyboard.KEY_X -> GLFW.GLFW_KEY_X;
            case Keyboard.KEY_Y -> GLFW.GLFW_KEY_Y;
            case Keyboard.KEY_Z -> GLFW.GLFW_KEY_Z;

            case Keyboard.KEY_SPACE -> GLFW.GLFW_KEY_SPACE;
            case Keyboard.KEY_APOSTROPHE -> GLFW.GLFW_KEY_APOSTROPHE;
            case Keyboard.KEY_COMMA -> GLFW.GLFW_KEY_COMMA;
            case Keyboard.KEY_MINUS -> GLFW.GLFW_KEY_MINUS;
            case Keyboard.KEY_PERIOD -> GLFW.GLFW_KEY_PERIOD;
            case Keyboard.KEY_SLASH -> GLFW.GLFW_KEY_SLASH;
            case Keyboard.KEY_SEMICOLON -> GLFW.GLFW_KEY_SEMICOLON;
            case Keyboard.KEY_EQUALS -> GLFW.GLFW_KEY_EQUAL;
            case Keyboard.KEY_LBRACKET -> GLFW.GLFW_KEY_LEFT_BRACKET;
            case Keyboard.KEY_BACKSLASH -> GLFW.GLFW_KEY_BACKSLASH;
            case Keyboard.KEY_RBRACKET -> GLFW.GLFW_KEY_RIGHT_BRACKET;
            case Keyboard.KEY_GRAVE -> GLFW.GLFW_KEY_GRAVE_ACCENT;

            case Keyboard.KEY_ESCAPE -> GLFW.GLFW_KEY_ESCAPE;
            case Keyboard.KEY_RETURN -> GLFW.GLFW_KEY_ENTER;
            case Keyboard.KEY_TAB -> GLFW.GLFW_KEY_TAB;
            case Keyboard.KEY_BACK -> GLFW.GLFW_KEY_BACKSPACE;
            case Keyboard.KEY_INSERT -> GLFW.GLFW_KEY_INSERT;
            case Keyboard.KEY_DELETE -> GLFW.GLFW_KEY_DELETE;
            case Keyboard.KEY_RIGHT -> GLFW.GLFW_KEY_RIGHT;
            case Keyboard.KEY_LEFT -> GLFW.GLFW_KEY_LEFT;
            case Keyboard.KEY_DOWN -> GLFW.GLFW_KEY_DOWN;
            case Keyboard.KEY_UP -> GLFW.GLFW_KEY_UP;
            case Keyboard.KEY_PRIOR -> GLFW.GLFW_KEY_PAGE_UP;
            case Keyboard.KEY_NEXT -> GLFW.GLFW_KEY_PAGE_DOWN;
            case Keyboard.KEY_HOME -> GLFW.GLFW_KEY_HOME;
            case Keyboard.KEY_END -> GLFW.GLFW_KEY_END;
            case Keyboard.KEY_CAPITAL -> GLFW.GLFW_KEY_CAPS_LOCK;
            case Keyboard.KEY_SCROLL -> GLFW.GLFW_KEY_SCROLL_LOCK;
            case Keyboard.KEY_NUMLOCK -> GLFW.GLFW_KEY_NUM_LOCK;
            case Keyboard.KEY_SYSRQ -> GLFW.GLFW_KEY_PRINT_SCREEN;
            case Keyboard.KEY_PAUSE -> GLFW.GLFW_KEY_PAUSE;

            case Keyboard.KEY_F1 -> GLFW.GLFW_KEY_F1;
            case Keyboard.KEY_F2 -> GLFW.GLFW_KEY_F2;
            case Keyboard.KEY_F3 -> GLFW.GLFW_KEY_F3;
            case Keyboard.KEY_F4 -> GLFW.GLFW_KEY_F4;
            case Keyboard.KEY_F5 -> GLFW.GLFW_KEY_F5;
            case Keyboard.KEY_F6 -> GLFW.GLFW_KEY_F6;
            case Keyboard.KEY_F7 -> GLFW.GLFW_KEY_F7;
            case Keyboard.KEY_F8 -> GLFW.GLFW_KEY_F8;
            case Keyboard.KEY_F9 -> GLFW.GLFW_KEY_F9;
            case Keyboard.KEY_F10 -> GLFW.GLFW_KEY_F10;
            case Keyboard.KEY_F11 -> GLFW.GLFW_KEY_F11;
            case Keyboard.KEY_F12 -> GLFW.GLFW_KEY_F12;
            case Keyboard.KEY_F13 -> GLFW.GLFW_KEY_F13;
            case Keyboard.KEY_F14 -> GLFW.GLFW_KEY_F14;
            case Keyboard.KEY_F15 -> GLFW.GLFW_KEY_F15;
            case Keyboard.KEY_F16 -> GLFW.GLFW_KEY_F16;
            case Keyboard.KEY_F17 -> GLFW.GLFW_KEY_F17;
            case Keyboard.KEY_F18 -> GLFW.GLFW_KEY_F18;
            case Keyboard.KEY_F19 -> GLFW.GLFW_KEY_F19;

            case Keyboard.KEY_NUMPAD0 -> GLFW.GLFW_KEY_KP_0;
            case Keyboard.KEY_NUMPAD1 -> GLFW.GLFW_KEY_KP_1;
            case Keyboard.KEY_NUMPAD2 -> GLFW.GLFW_KEY_KP_2;
            case Keyboard.KEY_NUMPAD3 -> GLFW.GLFW_KEY_KP_3;
            case Keyboard.KEY_NUMPAD4 -> GLFW.GLFW_KEY_KP_4;
            case Keyboard.KEY_NUMPAD5 -> GLFW.GLFW_KEY_KP_5;
            case Keyboard.KEY_NUMPAD6 -> GLFW.GLFW_KEY_KP_6;
            case Keyboard.KEY_NUMPAD7 -> GLFW.GLFW_KEY_KP_7;
            case Keyboard.KEY_NUMPAD8 -> GLFW.GLFW_KEY_KP_8;
            case Keyboard.KEY_NUMPAD9 -> GLFW.GLFW_KEY_KP_9;
            case Keyboard.KEY_DECIMAL -> GLFW.GLFW_KEY_KP_DECIMAL;
            case Keyboard.KEY_DIVIDE -> GLFW.GLFW_KEY_KP_DIVIDE;
            case Keyboard.KEY_MULTIPLY -> GLFW.GLFW_KEY_KP_MULTIPLY;
            case Keyboard.KEY_SUBTRACT -> GLFW.GLFW_KEY_KP_SUBTRACT;
            case Keyboard.KEY_ADD -> GLFW.GLFW_KEY_KP_ADD;
            case Keyboard.KEY_NUMPADENTER -> GLFW.GLFW_KEY_KP_ENTER;
            case Keyboard.KEY_NUMPADEQUALS -> GLFW.GLFW_KEY_KP_EQUAL;

            case Keyboard.KEY_LSHIFT -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case Keyboard.KEY_LCONTROL -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case Keyboard.KEY_LMENU -> GLFW.GLFW_KEY_LEFT_ALT;
            case Keyboard.KEY_LMETA -> GLFW.GLFW_KEY_LEFT_SUPER;
            case Keyboard.KEY_RSHIFT -> GLFW.GLFW_KEY_RIGHT_SHIFT;
            case Keyboard.KEY_RCONTROL -> GLFW.GLFW_KEY_RIGHT_CONTROL;
            case Keyboard.KEY_RMENU -> GLFW.GLFW_KEY_RIGHT_ALT;
            case Keyboard.KEY_RMETA -> GLFW.GLFW_KEY_RIGHT_SUPER;
            default -> GLFW.GLFW_KEY_UNKNOWN;
        };
    }

    /** Returns the current GLFW modifier bitmask from live LWJGL2 keyboard state. */
    public static int currentGlfwModifiers() {
        if (!Keyboard.isCreated()) return 0;
        int mods = 0;
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            mods |= GLFW.GLFW_MOD_SHIFT;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
            mods |= GLFW.GLFW_MOD_CONTROL;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) {
            mods |= GLFW.GLFW_MOD_ALT;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LMETA) || Keyboard.isKeyDown(Keyboard.KEY_RMETA)) {
            mods |= GLFW.GLFW_MOD_SUPER;
        }
        return mods;
    }

    /** Returns true when a LWJGL2 typed char represents printable text (not control codes like Ctrl+C or Backspace). */
    public static boolean isPrintableChar(char typedChar, int glfwModifiers) {
        if (typedChar < 32 || typedChar == 127) return false;
        return (glfwModifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) == 0;
    }

    private static Integer functionKey(String key) {
        return switch (key) {
            case "f1" -> Keyboard.KEY_F1;
            case "f2" -> Keyboard.KEY_F2;
            case "f3" -> Keyboard.KEY_F3;
            case "f4" -> Keyboard.KEY_F4;
            case "f5" -> Keyboard.KEY_F5;
            case "f6" -> Keyboard.KEY_F6;
            case "f7" -> Keyboard.KEY_F7;
            case "f8" -> Keyboard.KEY_F8;
            case "f9" -> Keyboard.KEY_F9;
            case "f10" -> Keyboard.KEY_F10;
            case "f11" -> Keyboard.KEY_F11;
            case "f12" -> Keyboard.KEY_F12;
            case "f13" -> Keyboard.KEY_F13;
            case "f14" -> Keyboard.KEY_F14;
            case "f15" -> Keyboard.KEY_F15;
            case "f16" -> Keyboard.KEY_F16;
            case "f17" -> Keyboard.KEY_F17;
            case "f18" -> Keyboard.KEY_F18;
            case "f19" -> Keyboard.KEY_F19;
            default -> null;
        };
    }
}
