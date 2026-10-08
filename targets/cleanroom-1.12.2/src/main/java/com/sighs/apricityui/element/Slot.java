package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;

/**
 * Lightweight 1.12 slot DOM element.  The newer targets use a loader-specific
 * MinecraftElement implementation; Cleanroom keeps the DOM contract small and
 * lets ApricityContainerScreen project its geometry onto vanilla Slot objects.
 */
@ElementRegister(Slot.TAG_NAME)
public class Slot extends Element {
    public static final String TAG_NAME = "SLOT";
    private boolean bound;
    private boolean boundDisabled;

    public Slot(Document document) { super(document, TAG_NAME); }
    static { Element.register(TAG_NAME, (document, tagName) -> new Slot(document)); }

    public void bindToMenuSlot(boolean disabled) { bound = true; boundDisabled = disabled; }
    public boolean isBound() { return bound; }
    public boolean isExplicitlyDisabled() {
        if (!hasAttribute("disabled")) return false;
        String value = getAttribute("disabled");
        return value == null || value.isBlank() || !("false".equalsIgnoreCase(value) || "0".equals(value));
    }
    @Override public boolean isDisabled() { return boundDisabled || isExplicitlyDisabled(); }
    public boolean canOperateBoundMenuSlot() { return bound && !isDisabled(); }
    public boolean shouldRenderItem() {
        String value = getAttribute("render-item");
        if (value != null && !value.isBlank()) return !("false".equalsIgnoreCase(value) || "0".equals(value));
        String render = getAttribute("render");
        return render == null || !("bg".equalsIgnoreCase(render) || "none".equalsIgnoreCase(render));
    }
    public int getSlotIndex() {
        String value = getAttribute("slot-index");
        if (value == null || value.isBlank()) value = getAttribute("index");
        try { return value == null ? -1 : Integer.parseInt(value.trim()); }
        catch (RuntimeException ignored) { return -1; }
    }
    public Element.DOMRect getSlotVisualBounds() { return getBoundingClientRect(); }
}
