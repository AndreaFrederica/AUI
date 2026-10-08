package com.sighs.apricityui.element;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.registry.annotation.ElementRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Cleanroom DOM container element and declaration extractor. */
@ElementRegister(Container.TAG_NAME)
public class Container extends Element {
    public static final String TAG_NAME = "CONTAINER";
    public Container(Document document) { super(document, TAG_NAME); }

    public static List<ContainerDeclaration> extractDeclarations(Document document) {
        if (document == null) return List.of();
        ArrayList<ContainerDeclaration> result = new ArrayList<>();
        int index = 0;
        for (Element element : document.getElements()) {
            if (!(element instanceof Container container)) continue;
            String id = container.getAttribute("id");
            if (id == null || id.isBlank()) id = "c" + index;
            id = id.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
            ContainerBindType bind = ContainerBindType.fromRaw(container.getAttribute("bind"));
            if (bind == null) bind = ContainerBindType.PLAYER;
            int capacity = parseInt(container.getAttribute("size"), bind == ContainerBindType.PLAYER ? 36 : 0);
            boolean primary = parseBoolean(container.getAttribute("primary"));
            result.add(new ContainerDeclaration(id, bind, capacity, primary));
            index++;
        }
        return List.copyOf(result);
    }

    private static int parseInt(String raw, int fallback) {
        try { return raw == null || raw.isBlank() ? fallback : Math.max(0, Integer.parseInt(raw.trim())); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private static boolean parseBoolean(String raw) {
        if (raw == null) return false;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes", "on", "enabled" -> true;
            default -> false;
        };
    }
}
