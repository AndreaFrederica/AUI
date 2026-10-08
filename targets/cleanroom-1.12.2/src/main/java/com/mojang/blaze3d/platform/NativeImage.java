package com.mojang.blaze3d.platform;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/** CPU image facade used by AUI before the Cleanroom texture uploader consumes it. */
public class NativeImage implements AutoCloseable {
    public enum Format { RGBA }

    private BufferedImage image;

    public NativeImage(int width, int height, boolean clear) {
        this(Format.RGBA, width, height, clear);
    }

    public NativeImage(Format format, int width, int height, boolean clear) {
        image = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
        if (!clear) {
            Graphics2D graphics = image.createGraphics();
            graphics.clearRect(0, 0, image.getWidth(), image.getHeight());
            graphics.dispose();
        }
    }

    private NativeImage(BufferedImage image) {
        this.image = image;
    }

    public static NativeImage read(InputStream input) throws IOException {
        BufferedImage decoded = ImageIO.read(input);
        return decoded == null ? null : new NativeImage(decoded);
    }

    public int getWidth() { return image == null ? 0 : image.getWidth(); }
    public int getHeight() { return image == null ? 0 : image.getHeight(); }
    public BufferedImage bufferedImage() { return image; }
    public Graphics2D createGraphics() { return image.createGraphics(); }
    public java.awt.image.WritableRaster getRaster() { return image.getRaster(); }
    public int[] getRGB(int x, int y, int width, int height, int[] pixels, int offset, int scanSize) {
        return image.getRGB(x, y, width, height, pixels, offset, scanSize);
    }
    public int getPixelRGBA(int x, int y) { return image.getRGB(x, y); }
    public void setPixelRGBA(int x, int y, int color) { image.setRGB(x, y, color); }

    @Override
    public void close() { image = null; }
}
