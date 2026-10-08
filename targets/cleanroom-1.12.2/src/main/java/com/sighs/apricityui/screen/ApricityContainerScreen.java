package com.sighs.apricityui.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.viewport.ApricityViewport;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;

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
        if (linkedDocument != null) linkedDocument.applyViewport(false);
    }

    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        // AUI owns the visual surface; vanilla slots remain available above it.
    }

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (linkedDocument == null) return;
        ApricityViewport viewport = linkedDocument.getViewport();
        PoseStack pose = new PoseStack();
        pose.pushPose();
        pose.translate(guiLeft, guiTop, 0);
        pose.scale(viewport.renderScale(), viewport.renderScale(), 1.0f);
        Mask.pushScissorScale(viewport.scissorScale(), pose);
        try { Base.drawScreenDocument(pose, linkedDocument); }
        finally { Mask.popScissorScale(); pose.popPose(); }
        Cursor.drawPseudoCursor(pose);
    }

    @Override public void onGuiClosed() {
        if (linkedDocument != null) { linkedDocument.remove(); linkedDocument = null; }
        Cursor.resetToDefault();
        super.onGuiClosed();
    }
    public ApricityContainerMenu getMenu() { return menu; }
}
