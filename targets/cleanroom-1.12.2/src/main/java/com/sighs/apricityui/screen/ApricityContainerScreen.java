package com.sighs.apricityui.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.render.Operation;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.viewport.ApricityViewport;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomInput;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import org.lwjgl.input.Mouse;

/** 1.12 GuiContainer shell that renders the linked AUI document over slots. */
public class ApricityContainerScreen extends GuiContainer implements AuiLinkedScreen {
    private final ApricityContainerMenu menu;
    private Document linkedDocument;

    public ApricityContainerScreen(ApricityContainerMenu menu, InventoryPlayer inventory) {
        super(menu);
        this.menu = menu;
        this.xSize = 176;
        this.ySize = 166;
    }

    @Override public Document getLinkedDocument() { return linkedDocument; }

    @Override public void initGui() {
        super.initGui();
        if (linkedDocument != null) linkedDocument.remove();
        linkedDocument = Document.create(menu.getTemplatePath());
        if (linkedDocument != null) {
            linkedDocument.applyViewport(false);
            menu.updateSlotLayout(linkedDocument, guiLeft, guiTop);
        }
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        // AUI owns the visual surface; vanilla slots remain available above it.
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Operation.onMouseMoveFrame(new com.sighs.apricityui.layout.Position(mouseX, mouseY));
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (linkedDocument == null) return;
        ApricityViewport viewport = linkedDocument.getViewport();
        menu.updateSlotLayout(linkedDocument, guiLeft, guiTop);
        PoseStack pose = new PoseStack();
        pose.pushPose();
        pose.translate(guiLeft, guiTop, 0);
        pose.scale(viewport.renderScale(), viewport.renderScale(), 1.0f);
        Mask.pushScissorScale(viewport.scissorScale(), pose);
        try { Base.drawScreenDocument(pose, linkedDocument); }
        finally { Mask.popScissorScale(); pose.popPose(); }
        Cursor.drawPseudoCursor(pose);
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
        int glfwKey = CleanroomInput.toGlfwKey(keyCode);
        if (Operation.onCharTyped(typedChar)
                || Operation.onKeyPressed(glfwKey < 0 ? keyCode : glfwKey, 0, 0, false, com.sighs.apricityui.event.KeyEvent.Source.SCREEN_EVENT)) return;
        super.keyTyped(typedChar, keyCode);
    }

    @Override public void onGuiClosed() {
        if (linkedDocument != null) { linkedDocument.remove(); linkedDocument = null; }
        Cursor.resetToDefault();
        super.onGuiClosed();
    }
    public ApricityContainerMenu getMenu() { return menu; }
}
