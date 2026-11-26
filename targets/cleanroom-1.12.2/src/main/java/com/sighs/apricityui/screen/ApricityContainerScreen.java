package com.sighs.apricityui.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.client.CleanroomClientEvents;
import com.sighs.apricityui.dev.DevTools;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.FrameTimingHud;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.render.Operation;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.ui.Tooltip;
import com.sighs.apricityui.viewport.ApricityViewport;
import com.sighs.apricityui.spi.AuiServices;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import java.nio.IntBuffer;

/** 1.12 GuiContainer shell that renders the linked AUI document over slots. */
public class ApricityContainerScreen extends GuiContainer implements AuiLinkedScreen {
    private final ApricityContainerMenu menu;
    private Document linkedDocument;
    private final IntBuffer previousViewport = BufferUtils.createIntBuffer(4);
    private final IntBuffer previousScissorBox = BufferUtils.createIntBuffer(4);

    public ApricityContainerScreen(ApricityContainerMenu menu, InventoryPlayer inventory) {
        super(menu);
        this.menu = menu;
        this.xSize = 176;
        this.ySize = 166;
    }

    @Override public Document getLinkedDocument() { return linkedDocument; }

    @Override public void initGui() {
        if (linkedDocument == null) {
            linkedDocument = Document.create(menu.getTemplatePath());
            if (linkedDocument != null) {
                linkedDocument.applyViewport(false);
            }
        } else {
            linkedDocument.applyViewport(true);
        }
        if (linkedDocument != null) {
            ApricityViewport viewport = linkedDocument.getViewport();
            this.xSize = Math.max(1, Math.round(viewport.layoutWidth() * viewport.renderScale()));
            this.ySize = Math.max(1, Math.round(viewport.layoutHeight() * viewport.renderScale()));
        }
        super.initGui();
        if (linkedDocument != null) {
            menu.updateSlotLayout(linkedDocument, guiLeft, guiTop);
        }
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        if (linkedDocument == null) {
            drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF18202A);
            drawRect(guiLeft + 1, guiTop + 1, guiLeft + xSize - 1, guiTop + ySize - 1, 0xFF263545);
            fontRenderer.drawStringWithShadow("AUI template unavailable", guiLeft + 8, guiTop + 8, 0xFFFF8080);
            fontRenderer.drawString(menu.getTemplatePath(), guiLeft + 8, guiTop + 22, 0xFFD0D8E0);
            return;
        }
        // Render the AUI document as the background layer BEFORE GuiContainer
        // draws real Minecraft item stacks, hover highlights, and carried items
        // so opaque panel backgrounds never paint over slot contents.
        drawLinkedDocumentLayer();
    }

    private void drawLinkedDocumentLayer() {
        ApricityViewport viewport = linkedDocument.getViewport();
        Mask.resetDepth();
        PoseStack pose = new PoseStack();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        Matrix4f previousProjection = AuiServices.render().getProjectionMatrix();
        boolean previousStencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean previousScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        previousViewport.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, previousViewport);
        previousScissorBox.clear();
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, previousScissorBox);
        int scaledW = Math.max(1, AuiServices.client().getScaledWidth());
        int scaledH = Math.max(1, AuiServices.client().getScaledHeight());
        int framebufferWidth = Math.max(1, Minecraft.getMinecraft().displayWidth);
        int framebufferHeight = Math.max(1, Minecraft.getMinecraft().displayHeight);
        Matrix4f screenProjection = new Matrix4f().ortho(
                0.0f, (float) scaledW, (float) scaledH, 0.0f, -1000.0f, 1000.0f);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, scaledW, scaledH, 0.0, -1000.0, 1000.0);
        AuiServices.render().setProjectionMatrix(screenProjection);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glViewport(0, 0, framebufferWidth, framebufferHeight);
        int clipLeft = Math.max(0, Math.round(guiLeft * framebufferWidth / (float) scaledW));
        int clipRight = Math.min(framebufferWidth, Math.round((guiLeft + xSize) * framebufferWidth / (float) scaledW));
        int clipTop = Math.max(0, Math.round(guiTop * framebufferHeight / (float) scaledH));
        int clipBottom = Math.min(framebufferHeight, Math.round((guiTop + ySize) * framebufferHeight / (float) scaledH));
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(clipLeft, framebufferHeight - clipBottom,
                Math.max(1, clipRight - clipLeft), Math.max(1, clipBottom - clipTop));
        GL20.glUseProgram(0);
        Base.pushDepthTest(AuiServices.render().isDepthTestEnabled());
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glColorMask(true, true, true, true);
        pose.pushPose();
        pose.translate(guiLeft, guiTop, 0);
        pose.scale(viewport.renderScale(), viewport.renderScale(), 1.0f);
        // Pass an UNTRANSLATED scale-only base pose to Mask.pushScissorScale so
        // Mask.resolveLocalScissorTransform preserves translate(guiLeft, guiTop)
        // when computing GL scissor rectangles for overflow:hidden elements!
        PoseStack scissorBase = new PoseStack();
        scissorBase.scale(viewport.renderScale(), viewport.renderScale(), 1.0f);
        Mask.pushScissorScale(viewport.scissorScale(), scissorBase);
        Base.pushDepthMode(false);
        try {
            Base.drawDocument(pose, linkedDocument);
        } catch (Throwable error) {
            com.sighs.apricityui.ApricityUI.LOGGER.error("[AUI Cleanroom] container document render failed for {}",
                    menu.getTemplatePath(), error);
            drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF18202A);
            fontRenderer.drawStringWithShadow("AUI render error", guiLeft + 8, guiTop + 8, 0xFFFF8080);
        } finally {
            Base.popDepthMode();
            Mask.popScissorScale();
            pose.popPose();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            AuiServices.render().setProjectionMatrix(previousProjection);
            Base.popDepthTest();
            if (previousStencil) GL11.glEnable(GL11.GL_STENCIL_TEST); else GL11.glDisable(GL11.GL_STENCIL_TEST);
            if (previousCull) GL11.glEnable(GL11.GL_CULL_FACE); else GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glViewport(previousViewport.get(0), previousViewport.get(1), previousViewport.get(2), previousViewport.get(3));
            if (previousScissor) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(previousScissorBox.get(0), previousScissorBox.get(1),
                        previousScissorBox.get(2), previousScissorBox.get(3));
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            GL13.glActiveTexture(previousActiveTexture);
            GL20.glUseProgram(previousProgram);
            GL11.glPopAttrib();
        }
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        FrameTimingHud.beginFrame();
        try {
            Position mousePos = new Position(mouseX, mouseY);
            Operation.onMouseMoveFrame(mousePos);
            Tooltip.moveActiveFromScreen(mousePos);
            DevTools.handleInspectMouseMove(mousePos);
            if (linkedDocument != null) {
                linkedDocument.commitPendingStyleRecalcForRender();
                menu.updateSlotLayout(linkedDocument, guiLeft, guiTop);
            }
            this.drawDefaultBackground();
            super.drawScreen(mouseX, mouseY, partialTicks);
            this.renderHoveredToolTip(mouseX, mouseY);
            drawElementHoverTooltip(mouseX, mouseY);
            if (linkedDocument != null) {
                PoseStack overlayPose = new PoseStack();
                CleanroomClientEvents.drawPersistentScreenDocuments(overlayPose, linkedDocument);
                Cursor.drawPseudoCursor(overlayPose);
            }
        } finally {
            FrameTimingHud.endFrame();
            CleanroomClientEvents.drawFrameTimingHud();
        }
    }

    /** Renders native Minecraft hover tooltips for DOM elements declaring data-tooltip (e.g. EU & fluid bars). */
    private void drawElementHoverTooltip(int mouseX, int mouseY) {
        if (linkedDocument == null || mc == null || mc.player == null) return;
        if (!mc.player.inventory.getItemStack().isEmpty()) return;
        for (net.minecraft.inventory.Slot slot : menu.inventorySlots) {
            if (slot == null || !slot.getHasStack()) continue;
            int sx = guiLeft + slot.xPos;
            int sy = guiTop + slot.yPos;
            if (mouseX >= sx - 1 && mouseX < sx + 17 && mouseY >= sy - 1 && mouseY < sy + 17) return;
        }
        ApricityViewport viewport = linkedDocument.getViewport();
        float scale = viewport == null ? 1.0f : viewport.renderScale();
        java.util.List<com.sighs.apricityui.init.Element> elements = linkedDocument.getElements();
        for (int i = elements.size() - 1; i >= 0; i--) {
            com.sighs.apricityui.init.Element element = elements.get(i);
            if (element == null || !element.hasAttribute("data-tooltip")) continue;
            String raw = element.getAttribute("data-tooltip");
            if (raw == null || raw.isBlank()) continue;
            com.sighs.apricityui.init.Element.DOMRect rect = element.getBoundingClientRect();
            if (rect == null || rect.width <= 0 || rect.height <= 0) continue;
            double x0 = guiLeft + rect.x * scale;
            double y0 = guiTop + rect.y * scale;
            double x1 = x0 + rect.width * scale;
            double y1 = y0 + rect.height * scale;
            if (mouseX >= x0 && mouseX <= x1 && mouseY >= y0 && mouseY <= y1) {
                String normalized = raw.replace("\\n", "\n");
                java.util.List<String> lines = java.util.Arrays.asList(normalized.split("\n"));
                this.drawHoveringText(lines, mouseX, mouseY);
                return;
            }
        }
    }

    private boolean isSlotInteraction(int mouseX, int mouseY) {
        if (mc != null && mc.player != null && !mc.player.inventory.getItemStack().isEmpty()) {
            return true;
        }
        for (net.minecraft.inventory.Slot slot : menu.inventorySlots) {
            if (slot == null) continue;
            int sx = guiLeft + slot.xPos;
            int sy = guiTop + slot.yPos;
            int sw = slot instanceof ApricityContainerMenu.UiSlot uiSlot ? Math.max(16, uiSlot.getUiSlotWidth()) : 16;
            int sh = slot instanceof ApricityContainerMenu.UiSlot uiSlot ? Math.max(16, uiSlot.getUiSlotHeight()) : 16;
            if (mouseX >= sx - 1 && mouseX < sx + sw + 1 && mouseY >= sy - 1 && mouseY < sy + sh + 1) {
                return true;
            }
        }
        return false;
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        boolean consumed = Operation.onMouseDown(mouseButton);
        if (consumed && !isSlotInteraction(mouseX, mouseY)) return;
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override protected void mouseReleased(int mouseX, int mouseY, int state) {
        boolean consumed = Operation.onMouseUp(state);
        if (consumed && !isSlotInteraction(mouseX, mouseY)) return;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        Operation.onMouseMove(new com.sighs.apricityui.layout.Position(mouseX, mouseY));
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override public void handleMouseInput() throws java.io.IOException {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && Operation.scroll(wheel / 120.0d)) return;
        super.handleMouseInput();
    }

    @Override public void handleKeyboardInput() throws java.io.IOException {
        if (!Keyboard.getEventKeyState()) {
            int glfwKey = CleanroomInput.toGlfwKey(Keyboard.getEventKey());
            if (glfwKey >= 0) {
                Operation.onKeyReleased(glfwKey, 0, CleanroomInput.currentGlfwModifiers(),
                        com.sighs.apricityui.event.KeyEvent.Source.SCREEN_EVENT);
            }
        }
        super.handleKeyboardInput();
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        int glfwKey = CleanroomInput.toGlfwKey(keyCode);
        int modifiers = CleanroomInput.currentGlfwModifiers();
        int reloadKey = AuiServices.keys().reloadKey();
        if (reloadKey >= 0 && glfwKey == reloadKey) { ClientLoader.reload(); return; }
        boolean consumed = false;
        if (glfwKey >= 0) {
            consumed = Operation.onKeyPressed(glfwKey, 0, modifiers, Keyboard.isRepeatEvent(),
                    com.sighs.apricityui.event.KeyEvent.Source.SCREEN_EVENT);
        }
        if (CleanroomInput.isPrintableChar(typedChar, modifiers)) {
            consumed |= Operation.onCharTyped(typedChar);
        }
        if (consumed) return;
        super.keyTyped(typedChar, keyCode);
    }

    @Override public void onGuiClosed() {
        if (linkedDocument != null) {
            if (linkedDocument.body != null) Event.triggerSingle(new Event(linkedDocument.body, "unload", false));
            linkedDocument.remove();
            linkedDocument = null;
        }
        Size.clearViewportOverride();
        Cursor.resetToDefault();
        super.onGuiClosed();
    }
    public ApricityContainerMenu getMenu() { return menu; }
}
