package com.sighs.apricityui.screen;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.element.ContainerDeclaration;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Basic 1.12 menu implementation used by the Cleanroom client bridge. */
public class ApricityContainerMenu extends Container {
    private final String templatePath;
    private final List<ContainerDeclaration> declarations;
    private final List<IInventory> customInventories = new ArrayList<>();

    public ApricityContainerMenu(InventoryPlayer playerInventory, String templatePath,
                                 List<ContainerDeclaration> declarations) {
        this.templatePath = templatePath == null ? "" : templatePath;
        this.declarations = declarations == null ? List.of() : List.copyOf(declarations);
        int customRow = 0;
        for (ContainerDeclaration declaration : this.declarations) {
            if (declaration == null || declaration.capacity() <= 0 || declaration.bindType() == ContainerBindType.PLAYER) continue;
            InventoryBasic inventory = new InventoryBasic(declaration.id(), false, declaration.capacity());
            customInventories.add(inventory);
            for (int index = 0; index < declaration.capacity(); index++) {
                int column = index % 9;
                int row = index / 9;
                addSlotToContainer(new Slot(inventory, index, 8 + column * 18, 8 + (customRow + row) * 18));
            }
            customRow += Math.max(1, (declaration.capacity() + 8) / 9) + 1;
        }
        int playerTop = 8 + customRow * 18;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlotToContainer(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, playerTop + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlotToContainer(new Slot(playerInventory, column, 8 + column * 18, playerTop + 58));
        }
    }

    public String getTemplatePath() { return templatePath; }
    public List<ContainerDeclaration> getDeclarations() { return declarations; }

    @Override public boolean canInteractWith(EntityPlayer player) { return player != null && !player.isDead; }

    @Override public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (index < 0 || index >= inventorySlots.size()) return ItemStack.EMPTY;
        Slot source = inventorySlots.get(index);
        if (source == null || !source.getHasStack()) return ItemStack.EMPTY;
        ItemStack copy = source.getStack().copy();
        int customCount = inventorySlots.size() - 36;
        boolean moved = index < customCount
                ? mergeItemStack(source.getStack(), customCount, inventorySlots.size(), true)
                : customCount > 0 && mergeItemStack(source.getStack(), 0, customCount, false);
        if (!moved) return ItemStack.EMPTY;
        if (source.getStack().isEmpty()) source.putStack(ItemStack.EMPTY);
        else source.onSlotChanged();
        source.onTake(player, source.getStack());
        return copy;
    }

    @Override public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        for (IInventory inventory : customInventories) inventory.closeInventory(player);
    }
}
