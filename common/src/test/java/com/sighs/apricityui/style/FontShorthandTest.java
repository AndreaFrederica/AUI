package com.sighs.apricityui.style;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FontShorthandTest {
    @Test
    void expandsInheritedFontKeywordToAllSupportedLonghands() {
        Style style = new Style();
        style.update("font", "inherit");

        assertEquals("inherit", style.font);
        assertEquals("inherit", style.fontStyle);
        assertEquals("inherit", style.fontWeight);
        assertEquals("inherit", style.fontSize);
        assertEquals("inherit", style.lineHeight);
        assertEquals("inherit", style.fontFamily);
    }

    @Test
    void parsesStyleWeightSizeLineHeightAndFamily() {
        Map<String, String> expanded = ShorthandParser.expandFont(
                "italic 700 12px/1.4 \"Consolas\", \"Courier New\", monospace");

        assertEquals("italic", expanded.get("font-style"));
        assertEquals("700", expanded.get("font-weight"));
        assertEquals("12px", expanded.get("font-size"));
        assertEquals("1.4", expanded.get("line-height"));
        assertEquals("\"Consolas\", \"Courier New\", monospace", expanded.get("font-family"));
    }

    @Test
    void appliesFontShorthandThroughStyleUpdate() {
        Style style = new Style();
        style.update("font", "normal 10px Consolas, monospace");

        assertEquals("normal 10px Consolas, monospace", style.font);
        assertEquals("normal", style.fontStyle);
        assertEquals("normal", style.fontWeight);
        assertEquals("10px", style.fontSize);
        assertEquals("normal", style.lineHeight);
        assertEquals("Consolas, monospace", style.fontFamily);
    }
}
