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
        if (linkedDocument != null) linkedDocument.remove();
        linkedDocument = Document.create(templatePath);
        if (linkedDocument != null) linkedDocument.applyViewport(false);
    }

    @Override public void onResize(Minecraft minecraft, int width, int height) {
        super.onResize(minecraft, width, height);
        if (linkedDocument != null) linkedDocument.applyViewport(true);
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Operation.onMouseMoveFrame(new com.sighs.apricityui.layout.Position(mouseX, mouseY));
        if (showDefaultBackground) drawDefaultBackground();
        if (linkedDocument != null) {
            PoseStack pose = new PoseStack();
            ApricityViewport viewport = linkedDocument.getViewport();
            int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0, AuiServices.client().getScaledWidth(),
                    AuiServices.client().getScaledHeight(), 0.0, -1000.0, 1000.0);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL20.glUseProgram(0);
            // GuiScreen is a 2D pass. The vanilla screen renderer normally
            // disables depth before drawing; AUI's shared graph defaults to
            // depth enabled for world windows, so explicitly make the screen
            // pass depth independent.
            boolean previousDepth = AuiServices.render().isDepthTestEnabled();
            boolean previousDepthMask = AuiServices.render().isDepthMaskEnabled();
            AuiServices.render().disableDepthTest();
            AuiServices.render().setDepthMask(false);
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
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
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
