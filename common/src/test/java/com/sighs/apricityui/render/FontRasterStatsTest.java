package com.sighs.apricityui.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文字光栅与字体存储的指标语义：光栅任务数/耗时是速率，走帧级双缓冲；
 * 图集页数/占用、缓存条目数、是否装不下是状态量，立即发布并夹取。
 */
class FontRasterStatsTest {
    @Test
    void rasterTimingsAccumulateWithinFrameAndPublishOnEndFrame() {
        RenderBatchStats.beginFrame();
        RenderBatchStats.recordRaster(2_000_000L);
        RenderBatchStats.recordRaster(3_000_000L);
        assertEquals(0, RenderBatchStats.lastRasterTasks(), "endFrame 之前不得改写快照");
        RenderBatchStats.endFrame();

        assertEquals(2, RenderBatchStats.lastRasterTasks(), "帧内光栅任务应累加");
        assertEquals(5_000_000L, RenderBatchStats.lastRasterNanos(), "帧内光栅耗时应累加");

        // 非正耗时仍算一次任务，但不计入耗时
        RenderBatchStats.beginFrame();
        RenderBatchStats.recordRaster(0L);
        RenderBatchStats.endFrame();
        assertEquals(1, RenderBatchStats.lastRasterTasks(), "非正耗时也是一次光栅任务");
        assertEquals(0L, RenderBatchStats.lastRasterNanos(), "非正耗时不参与累加");
    }

    @Test
    void nonFramePathPublishesRasterThroughEndDocument() {
        RenderBatchStats.beginDocument();
        RenderBatchStats.recordRaster(900_000L);
        RenderBatchStats.endDocument();
        assertEquals(1, RenderBatchStats.lastRasterTasks(), "非帧路径由 endDocument 产出快照");
        assertEquals(900_000L, RenderBatchStats.lastRasterNanos());
    }

    @Test
    void fontStorageGaugesPublishImmediatelyAndClamp() {
        RenderBatchStats.beginFrame();
        RenderBatchStats.setFontStorageState(2, 63, 1500, true);
        assertEquals(63, RenderBatchStats.atlasUsedPercent(), "图集占用是 gauge，发布即可读");
        assertEquals(1500, RenderBatchStats.cacheEntries());
        assertTrue(RenderBatchStats.atlasExhausted());
        RenderBatchStats.endFrame();
        assertEquals(63, RenderBatchStats.atlasUsedPercent(), "endFrame 不得重置 gauge");

        RenderBatchStats.setFontStorageState(-1, 250, -3, false);
        assertEquals(0, RenderBatchStats.atlasPages());
        assertEquals(100, RenderBatchStats.atlasUsedPercent(), "占用率被夹到 0..100");
        assertEquals(0, RenderBatchStats.cacheEntries());
        assertFalse(RenderBatchStats.atlasExhausted());
    }

    @Test
    void fontStatsLineCarriesRasterAndStorageFields() {
        String line = RenderBatchStats.fontStatsLine(12.5d, 20.25d);
        assertTrue(line.startsWith("[AUI FontStats] "), "日志行前缀是验收脚本 grep 的锚点: " + line);
        assertTrue(line.contains("avgFrame=12.50ms"), line);
        assertTrue(line.contains("avgWallFrame=20.25ms"), line);
        assertTrue(line.contains("imageFlushes="), line);
        assertTrue(line.contains("graphFlushes="), line);
        assertTrue(line.contains("rasterTasks="), line);
        assertTrue(line.contains("rasterMs="), line);
        assertTrue(line.contains("atlasPages="), line);
        assertTrue(line.contains("atlasUsed="), line);
        assertTrue(line.contains("cacheSize="), line);
        assertTrue(line.contains("atlasFull="), line);
    }

    @Test
    void glyphRasterBudgetEnforcesFrameTimeCapWithoutMidFrameReset() {
        GlyphCache.beginFrame();
        // First glyph always allowed for forward progress.
        assertTrue(GlyphCache.claimRasterBudgetForTest());
        // Simulate a slow glyph upload exceeding the 4ms per-frame time budget.
        GlyphCache.recordRasterDurationForTest(10_000_000L);
        // Must NOT reset mid-frame even after a slow raster; remaining glyphs defer to next frame.
        assertFalse(GlyphCache.claimRasterBudgetForTest());

        // Starting the next frame resets both count and time budgets.
        GlyphCache.beginFrame();
        assertTrue(GlyphCache.claimRasterBudgetForTest());
        GlyphCache.recordRasterDurationForTest(500_000L);
        assertTrue(GlyphCache.claimRasterBudgetForTest());
    }
}
