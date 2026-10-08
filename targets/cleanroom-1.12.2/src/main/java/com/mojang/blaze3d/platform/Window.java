package com.mojang.blaze3d.platform;

/** High-version window view backed by values supplied by the Cleanroom client service. */
public class Window {
    private final long handle;
    private final int width;
    private final int height;
    private final int guiWidth;
    private final int guiHeight;
    private final double guiScale;

    public Window(long handle, int width, int height, int guiWidth, int guiHeight, double guiScale) {
        this.handle = handle;
        this.width = width;
        this.height = height;
        this.guiWidth = guiWidth;
        this.guiHeight = guiHeight;
        this.guiScale = guiScale;
    }

    public Window(int width, int height) {
        this(0L, width, height, width, height, 1.0D);
    }

    public Window(int width, int height, int guiWidth, int guiHeight, double guiScale) {
        this(0L, width, height, guiWidth, guiHeight, guiScale);
    }

    public long getWindow() { return handle; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getGuiScaledWidth() { return guiWidth; }
    public int getGuiScaledHeight() { return guiHeight; }
    public double getGuiScale() { return guiScale; }
    public int getScreenWidth() { return width; }
    public int getScreenHeight() { return height; }
}
