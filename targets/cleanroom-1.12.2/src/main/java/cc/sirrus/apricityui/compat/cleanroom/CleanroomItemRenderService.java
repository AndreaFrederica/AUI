package cc.sirrus.apricityui.compat.cleanroom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.spi.AuiItemRenderRequest;
import com.sighs.apricityui.spi.AuiItemRenderService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

/** 1.12 RenderItem backend for AUI item paint nodes and stack overlays. */
public final class CleanroomItemRenderService implements AuiItemRenderService {
    public static final CleanroomItemRenderService INSTANCE = new CleanroomItemRenderService();
    private final FloatBuffer poseMatrixBuffer = BufferUtils.createFloatBuffer(16);

    private CleanroomItemRenderService() { }

    @Override public boolean isEmptyStack(Object stack) {
        return !(stack instanceof ItemStack itemStack) || itemStack.isEmpty();
    }

    @Override public void render(AuiItemRenderRequest request) {
        if (request == null || !(request.stack() instanceof ItemStack stack)) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        RenderItem renderer = minecraft.getRenderItem();
        if (renderer == null) return;
        boolean hasStack = !stack.isEmpty();
        String overlay = request.overlayText();
        boolean hasOverlay = overlay != null && !overlay.isBlank();
        if (!hasStack && !hasOverlay) return;

        PoseStack pose = request.poseStack();
        GlStateManager.pushMatrix();
        try {
            if (pose != null) {
                // Apply the full PoseStack matrix (translation, viewport renderScale, CSS scale/rotation)
                // instead of only extracting translation.
                poseMatrixBuffer.clear();
                pose.last().pose().get(poseMatrixBuffer);
                GL11.glMultMatrix(poseMatrixBuffer);
            }
            if (hasStack) {
                RenderHelper.enableGUIStandardItemLighting();
                try {
                    renderer.renderItemAndEffectIntoGUI(stack, 0, 0);
                } finally {
                    RenderHelper.disableStandardItemLighting();
                }
            }
            if (request.decorations() && (hasOverlay || hasStack)) {
                GlStateManager.pushMatrix();
                try {
                    if (request.decorationOffsetY() != 0.0f) {
                        GlStateManager.translate(0.0f, request.decorationOffsetY(), 0.0f);
                    }
                    // In MC 1.12.2, passing "" suppresses the stack count; null lets RenderItem draw stack.getCount().
                    renderer.renderItemOverlayIntoGUI(minecraft.fontRenderer, stack, 0, 0,
                            hasOverlay ? overlay : null);
                } finally {
                    GlStateManager.popMatrix();
                }
            }
        } finally {
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.popMatrix();
        }
    }
}
