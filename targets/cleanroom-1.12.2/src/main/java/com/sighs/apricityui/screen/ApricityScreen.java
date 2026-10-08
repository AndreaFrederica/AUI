package com.sighs.apricityui.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.viewport.ApricityViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

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
        if (showDefaultBackground) drawDefaultBackground();
        if (linkedDocument != null) {
            PoseStack pose = new PoseStack();
            ApricityViewport viewport = linkedDocument.getViewport();
            pose.pushPose();
            pose.scale(viewport.renderScale(), viewport.renderScale(), 1.0f);
            Mask.pushScissorScale(viewport.scissorScale(), pose);
            try { Base.drawScreenDocument(pose, linkedDocument); }
            finally { Mask.popScissorScale(); pose.popPose(); }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override protected void keyTyped(char typedChar, int keyCode) throws java.io.IOException {
        if (keyCode == AuiServices.keys().reloadKey()) { ClientLoader.reload(); return; }
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
