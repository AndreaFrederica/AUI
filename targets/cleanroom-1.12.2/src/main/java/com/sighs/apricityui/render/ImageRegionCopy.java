package com.sighs.apricityui.render;

import com.mojang.blaze3d.platform.NativeImage;

/** Copies between Cleanroom's NativeImage compatibility buffers. */
public final class ImageRegionCopy {
    private ImageRegionCopy() {
    }

    public static void copy(NativeImage source, NativeImage target,
                            int srcX, int srcY, int dstX, int dstY,
                            int width, int height, boolean flipX, boolean flipY) {
        if (source == null || target == null || width <= 0 || height <= 0) return;
        for (int y = 0; y < height; y++) {
            int sourceY = srcY + (flipY ? height - 1 - y : y);
            for (int x = 0; x < width; x++) {
                int sourceX = srcX + (flipX ? width - 1 - x : x);
                target.setPixelRGBA(dstX + x, dstY + y, source.getPixelRGBA(sourceX, sourceY));
            }
        }
    }
}
