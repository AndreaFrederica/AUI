package com.mojang.blaze3d.platform;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * CPU RGBA image facade used by AUI on Cleanroom 1.12.2.
 *
 * <p>Pixels are stored in a flat ABGR-packed {@code int[]} ({@code 0xAABBGGRR}),
 * matching modern Minecraft's {@code NativeImage} layout so little-endian uploads
 * map directly to {@code GL_RGBA} / {@code GL_UNSIGNED_BYTE} without per-pixel
 * {@code BufferedImage} ColorModel overhead or Red/Blue channel swaps.</p>
 */
public class NativeImage implements AutoCloseable {
    public enum Format { RGBA }

    private int width;
    private int height;
    private int[] pixels;
    private boolean hasPixels;

    public NativeImage(int width, int height, boolean clear) {
        this(Format.RGBA, width, height, clear);
    }

    public NativeImage(Format format, int width, int height, boolean clear) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.pixels = new int[this.width * this.height];
        this.hasPixels = false;
    }

    private NativeImage(int width, int height, int[] abgrPixels) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.pixels = abgrPixels;
        this.hasPixels = true;
    }

    public static NativeImage read(InputStream input) throws IOException {
        BufferedImage decoded = ImageIO.read(input);
        if (decoded == null) return null;
        int w = Math.max(1, decoded.getWidth());
        int h = Math.max(1, decoded.getHeight());
        int[] argb = decoded.getRGB(0, 0, w, h, null, 0, w);
        int[] abgr = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            abgr[i] = (p & 0xFF00FF00) | ((p >>> 16) & 0xFF) | ((p & 0xFF) << 16);
        }
        return new NativeImage(w, h, abgr);
    }

    public int getWidth() { return pixels == null ? 0 : width; }
    public int getHeight() { return pixels == null ? 0 : height; }
    public int[] pixels() { return pixels; }
    public boolean hasPixels() { return hasPixels && pixels != null; }

    public int getPixelRGBA(int x, int y) {
        if (pixels == null || x < 0 || y < 0 || x >= width || y >= height) return 0;
        return pixels[y * width + x];
    }

    public void setPixelRGBA(int x, int y, int abgr) {
        if (pixels == null || x < 0 || y < 0 || x >= width || y >= height) return;
        pixels[y * width + x] = abgr;
        hasPixels = true;
    }

    public void writePixels(int x, int y, int regionWidth, int regionHeight, int[] abgrPixels) {
        if (pixels == null || abgrPixels == null || regionWidth <= 0 || regionHeight <= 0) return;
        int srcStartCol = Math.max(0, -x);
        int srcStartRow = Math.max(0, -y);
        int dstStartX = Math.max(0, x);
        int dstStartY = Math.max(0, y);
        int copyWidth = Math.min(regionWidth - srcStartCol, width - dstStartX);
        int copyHeight = Math.min(regionHeight - srcStartRow, height - dstStartY);
        if (copyWidth <= 0 || copyHeight <= 0) return;
        for (int row = 0; row < copyHeight; row++) {
            int srcOffset = (srcStartRow + row) * regionWidth + srcStartCol;
            int dstOffset = (dstStartY + row) * width + dstStartX;
            if (srcOffset + copyWidth > abgrPixels.length) break;
            System.arraycopy(abgrPixels, srcOffset, pixels, dstOffset, copyWidth);
        }
        hasPixels = true;
    }

    /** Compatibility view for legacy callers that still request a BufferedImage (ARGB). */
    public BufferedImage bufferedImage() {
        if (pixels == null) return null;
        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] argb = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            argb[i] = (p & 0xFF00FF00) | ((p >>> 16) & 0xFF) | ((p & 0xFF) << 16);
        }
        copy.setRGB(0, 0, width, height, argb, 0, width);
        return copy;
    }

    public Graphics2D createGraphics() {
        BufferedImage view = bufferedImage();
        return view == null ? null : view.createGraphics();
    }

    public java.awt.image.WritableRaster getRaster() {
        BufferedImage view = bufferedImage();
        return view == null ? null : view.getRaster();
    }

    public int[] getRGB(int x, int y, int w, int h, int[] out, int offset, int scanSize) {
        if (pixels == null) return out;
        int[] target = out != null ? out : new int[offset + h * scanSize];
        for (int row = 0; row < h; row++) {
            int srcY = y + row;
            if (srcY < 0 || srcY >= height) continue;
            for (int col = 0; col < w; col++) {
                int srcX = x + col;
                if (srcX < 0 || srcX >= width) continue;
                int p = pixels[srcY * width + srcX];
                target[offset + row * scanSize + col] =
                        (p & 0xFF00FF00) | ((p >>> 16) & 0xFF) | ((p & 0xFF) << 16);
            }
        }
        return target;
    }

    @Override
    public void close() {
        pixels = null;
        width = 0;
        height = 0;
        hasPixels = false;
    }
}
