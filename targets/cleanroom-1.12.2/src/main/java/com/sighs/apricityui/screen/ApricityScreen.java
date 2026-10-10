package com.sighs.apricityui.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.render.Operation;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.spi.AuiServices;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomInput;
import com.sighs.apricityui.viewport.ApricityViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.joml.Matrix4f;

/** 1.12 GuiScreen owner for one AUI document. */
public class ApricityScreen extends GuiScreen implements AuiLinkedScreen {
    private final String templatePath;
    private boolean pauseGame;
    private boolean showDefaultBackground;
    private Document linkedDocument;

    public ApricityScreen(String templatePath) { this.templatePath = templatePath; }
    public ApricityScreen setPauseGame(boolean value) { pauseGame = value; return this; }
    public ApricityScreen setShowDefaultBackground(boolean value) { showDefaultBackground = value; return this; }
    public boolean isPauseGame() { return pauseGame; }
    public boolean isShowDefaultBackground() { return showDefaultBackground; }
    @Override public Document getLinkedDocument() { return linkedDocument; }

    @Override public void initGui() {
        // GuiScreen invokes initGui on every resize as well as first open.
        // Keep the live DOM and script state when only the viewport changed.
        if (linkedDocument == null) {
            linkedDocument = Document.create(templatePath);
            if (linkedDocument != null) linkedDocument.applyViewport(false);
        } else {
            linkedDocument.applyViewport(true);
        }
    }

    @Override public void onResize(Minecraft minecraft, int width, int height) {
        super.onResize(minecraft, width, height);
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Operation.onMouseMoveFrame(new com.sighs.apricityui.layout.Position(mouseX, mouseY));
        if (showDefaultBackground) drawDefaultBackground();
        if (linkedDocument != null) {
            PoseStack pose = new PoseStack();
            ApricityViewport viewport = linkedDocument.getViewport();
            int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            Matrix4f previousProjection = AuiServices.render().getProjectionMatrix();
            Matrix4f screenProjection = new Matrix4f().ortho(
                    0.0f,
                    (float) AuiServices.client().getScaledWidth(),
                    (float) AuiServices.client().getScaledHeight(),
                    0.0f,
                    -1000.0f,
                    1000.0f);
            IntBuffer previousViewport = BufferUtils.createIntBuffer(4);
            GL11.glGetInteger(GL11.GL_VIEWPORT, previousViewport);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0, AuiServices.client().getScaledWidth(),
                    AuiServices.client().getScaledHeight(), 0.0, -1000.0, 1000.0);
            // The Cleanroom adapter's GLSL mesh path consumes the same logical
            // GUI projection as the legacy matrix stack. Keep both matrices in
            // sync so resize and high-DPI framebuffer changes cannot move or
            // clip POSITION_COLOR geometry.
            AuiServices.render().setProjectionMatrix(screenProjection);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            // Cleanroom can leave the legacy viewport at GUI-scaled dimensions
            // (854x480 on a high-DPI display). AUI coordinates are logical GUI
            // pixels, while OpenGL rasterization must target the physical
            // framebuffer.
            GL11.glViewport(0, 0, Minecraft.getMinecraft().displayWidth, Minecraft.getMinecraft().displayHeight);
            GL20.glUseProgram(0);
            // AUI assigns monotonically increasing Z offsets to DOM paint
            // nodes.  Keep depth ordering for a screen too; without it, a
            // later body/section background covers the title and controls.
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            boolean previousDepth = AuiServices.render().isDepthTestEnabled();
            boolean previousDepthMask = AuiServices.render().isDepthMaskEnabled();
            Base.pushDepthTest(true);
            AuiServices.render().enableDepthTest();
            AuiServices.render().setDepthMask(true);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColorMask(true, true, true, true);
            pose.pushPose();
            pose.scale(viewport.renderScale(), viewport.renderScale(), 1.0f);
            Mask.pushScissorScale(viewport.scissorScale(), pose);
            try { Base.drawScreenDocument(pose, linkedDocument); }
            finally {
                Mask.popScissorScale();
                pose.popPose();
                if (previousDepth) AuiServices.render().enableDepthTest();
                else AuiServices.render().disableDepthTest();
                AuiServices.render().setDepthMask(previousDepthMask);
                Base.popDepthTest();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                AuiServices.render().setProjectionMatrix(previousProjection);
                GL11.glViewport(previousViewport.get(0), previousViewport.get(1), previousViewport.get(2), previousViewport.get(3));
                GL20.glUseProgram(previousProgram);
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws java.io.IOException {
        if (Operation.onMouseDown(mouseButton)) return;
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (Operation.onMouseUp(state)) return;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        Operation.onMouseMove(new com.sighs.apricityui.layout.Position(mouseX, mouseY));
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override public void handleMouseInput() throws java.io.IOException {
        if (Mouse.getEventDWheel() != 0 && Operation.scroll(Mouse.getEventDWheel())) return;
        super.handleMouseInput();
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == AuiServices.keys().reloadKey()) { ClientLoader.reload(); return; }
        int glfwKey = CleanroomInput.toGlfwKey(keyCode);
        boolean consumed = Operation.onCharTyped(typedChar);
        consumed |= Operation.onKeyPressed(glfwKey < 0 ? keyCode : glfwKey, 0, 0, Keyboard.isRepeatEvent(),
                com.sighs.apricityui.event.KeyEvent.Source.SCREEN_EVENT);
        if (consumed) return;
        super.keyTyped(typedChar, keyCode);
    }

    @Override public void onGuiClosed() {
        if (linkedDocument != null) {
            if (linkedDocument.body != null) Event.triggerSingle(new Event(linkedDocument.body, "unload", false));
            linkedDocument.remove();
            linkedDocument = null;
        }
        Cursor.resetToDefault();
        super.onGuiClosed();
    }

    @Override public boolean doesGuiPauseGame() { return pauseGame; }
}
