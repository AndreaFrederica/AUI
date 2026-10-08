package cc.sirrus.apricityui.compat.cleanroom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.spi.AuiItemRenderRequest;
import com.sighs.apricityui.spi.AuiItemRenderService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;
import org.joml.Vector4f;

/** 1.12 RenderItem backend for AUI item paint nodes and stack overlays. */
public final class CleanroomItemRenderService implements AuiItemRenderService {
    public static final CleanroomItemRenderService INSTANCE = new CleanroomItemRenderService();
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
        if (!hasStack && (overlay == null || overlay.isBlank())) return;

        PoseStack pose = request.poseStack();
        Vector4f origin = pose == null
                ? new Vector4f(0, 0, 0, 1)
                : pose.last().pose().transform(new Vector4f(0, 0, 0, 1));
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(origin.x, origin.y + request.decorationOffsetY(), origin.z);
            RenderHelper.enableGUIStandardItemLighting();
            if (hasStack) renderer.renderItemAndEffectIntoGUI(stack, 0, 0);
            if (request.decorations() && (overlay != null && !overlay.isBlank() || hasStack)) {
                renderer.renderItemOverlayIntoGUI(minecraft.fontRenderer, stack, 0, 0,
                        overlay == null ? "" : overlay);
            }
        } finally {
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
        }
    }
}
