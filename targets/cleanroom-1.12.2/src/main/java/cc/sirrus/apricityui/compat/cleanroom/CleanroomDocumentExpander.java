package cc.sirrus.apricityui.compat.cleanroom;

import com.sighs.apricityui.dom.DocumentExpander;
import com.sighs.apricityui.element.Container;
import com.sighs.apricityui.element.Slot;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Cleanroom 1.12.2 {@link DocumentExpander}: expands {@code <slot repeat="N">}
 * and auto-generates slots for empty {@code <container bind="player">} or
 * {@code <container size="N">} declarations, matching high-version ContainerExpander.
 */
public final class CleanroomDocumentExpander implements DocumentExpander {
    public static final CleanroomDocumentExpander INSTANCE = new CleanroomDocumentExpander();
    private static final int PLAYER_AUTO_SLOT_COUNT = 36;

    private CleanroomDocumentExpander() {
    }

    @Override
    public void apply(Document document) {
        if (document == null) return;
        ArrayList<Element> snapshot = new ArrayList<>(document.getElements());
        for (Element element : snapshot) {
            if (!(element instanceof Container container)) continue;
            expandContainer(document, container);
        }
    }

    private static void expandContainer(Document document, Container container) {
        List<Slot> ownedSlots = collectOwnedSlots(document, container);
        materializeRepeatedSlots(ownedSlots);
        ownedSlots = collectOwnedSlots(document, container);

        if (ownedSlots.isEmpty()) {
            Integer declaredSize = parsePositiveInt(container.getAttribute("size"));
            String bindType = normalize(container.getAttribute("bind"));
            if (declaredSize != null) {
                appendAutoSlots(document, container, declaredSize, false);
            } else if ("player".equals(bindType)) {
                appendAutoSlots(document, container, PLAYER_AUTO_SLOT_COUNT, true);
            }
            ownedSlots = collectOwnedSlots(document, container);
        }

        normalizeSlotIndices(ownedSlots);
    }

    private static void materializeRepeatedSlots(List<Slot> slots) {
        if (slots.isEmpty()) return;
        int nextImplicitIndex = 0;
        for (Slot source : new ArrayList<>(slots)) {
            int repeatCount = parseRepeatCount(source.getAttribute("repeat"));
            int requestedIndex = source.getSlotIndex();
            int startIndex = requestedIndex >= 0 ? requestedIndex : nextImplicitIndex;
            nextImplicitIndex = Math.max(nextImplicitIndex, startIndex + repeatCount);
            if (repeatCount <= 1) continue;

            ensureIndexAttributes(source, startIndex);
            source.removeAttribute("repeat");
            Slot insertionPoint = source;
            for (int offset = 1; offset < repeatCount; offset++) {
                Element cloned = source.cloneNode(true);
                if (!(cloned instanceof Slot clone)) break;
                clone.removeAttribute("id");
                ensureIndexAttributes(clone, startIndex + offset);
                clone.setAttribute("data-generated", "container-repeat");
                insertionPoint.after(clone);
                insertionPoint = clone;
            }
        }
    }

    private static void appendAutoSlots(Document document, Container container, int count, boolean playerAuto) {
        int safeCount = Math.max(0, Math.min(count, 512));
        boolean presetPlayer = playerAuto && "preset:player".equalsIgnoreCase(normalize(container.getAttribute("layout")));
        for (int index = 0; index < safeCount; index++) {
            Slot slot = new Slot(document);
            String indexStr = String.valueOf(index);
            slot.setAttribute("index", indexStr);
            slot.setAttribute("slot-index", indexStr);
            slot.setAttribute("data-generated", "container-auto");
            if (playerAuto) {
                slot.setAttribute("part", index < 9 ? "hotbar" : "inv");
                if (presetPlayer) {
                    int column = index % 9;
                    // Classic Minecraft inventory layout: 3 rows (y=0,18,36) + 4px gap + hotbar (y=58).
                    int top = index < 9 ? 58 : ((index - 9) / 9) * 18;
                    slot.setAttribute("style", "position:absolute;left:" + (column * 18)
                            + "px;top:" + top + "px;width:18px;height:18px;box-sizing:border-box;"
                            + "background:#8b8b8b;border-top:1px solid #373737;border-left:1px solid #373737;"
                            + "border-right:1px solid #ffffff;border-bottom:1px solid #ffffff;");
                }
            }
            container.append(slot);
        }
    }

    private static void normalizeSlotIndices(List<Slot> slots) {
        if (slots.isEmpty()) return;
        Set<Integer> used = new HashSet<>();
        ArrayList<Slot> missing = new ArrayList<>();
        for (Slot slot : slots) {
            int idx = slot.getSlotIndex();
            if (idx >= 0) used.add(idx);
            else missing.add(slot);
        }
        int next = 0;
        for (Slot slot : missing) {
            while (used.contains(next)) next++;
            ensureIndexAttributes(slot, next);
            used.add(next);
            next++;
        }
    }

    private static List<Slot> collectOwnedSlots(Document document, Container container) {
        ArrayList<Slot> result = new ArrayList<>();
        for (Element element : document.getElements()) {
            if (!(element instanceof Slot slot)) continue;
            if (ancestorContainer(slot) == container) {
                result.add(slot);
            }
        }
        return result;
    }

    private static Container ancestorContainer(Element element) {
        Element current = element == null ? null : element.parentElement;
        while (current != null) {
            if (current instanceof Container container) return container;
            current = current.parentElement;
        }
        return null;
    }

    private static void ensureIndexAttributes(Slot slot, int index) {
        String value = String.valueOf(index);
        slot.setAttribute("index", value);
        slot.setAttribute("slot-index", value);
    }

    private static int parseRepeatCount(String raw) {
        Integer parsed = parsePositiveInt(raw);
        return parsed == null ? 1 : Math.min(parsed, 512);
    }

    private static Integer parsePositiveInt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            int parsed = Integer.parseInt(raw.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
}
