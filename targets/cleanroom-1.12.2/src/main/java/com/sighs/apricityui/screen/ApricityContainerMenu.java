package com.sighs.apricityui.screen;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.element.ContainerDeclaration;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Basic 1.12 menu implementation used by the Cleanroom client bridge. */
public class ApricityContainerMenu extends Container {
    private final String templatePath;
    private final List<ContainerDeclaration> declarations;
    private final List<IInventory> customInventories = new ArrayList<>();

    /** Server constructor: resolve declarations against real 1.12 world data. */
    public ApricityContainerMenu(EntityPlayerMP owner, String templatePath,
                                 List<ContainerDeclaration> declarations,
                                 Map<String, Map<String, String>> argsById) {
        this(owner.inventory, templatePath, normalizeDeclarations(owner, declarations, argsById), argsById, owner);
    }

    public ApricityContainerMenu(InventoryPlayer playerInventory, String templatePath,
                                 List<ContainerDeclaration> declarations) {
        this(playerInventory, templatePath, declarations, Map.of(), null);
    }

    private ApricityContainerMenu(InventoryPlayer playerInventory, String templatePath,
                                  List<ContainerDeclaration> declarations,
                                  Map<String, Map<String, String>> argsById,
                                  EntityPlayerMP owner) {
        this.templatePath = templatePath == null ? "" : templatePath;
        this.declarations = declarations == null ? List.of() : List.copyOf(declarations);
        int customRow = 0;
        for (ContainerDeclaration declaration : this.declarations) {
            if (declaration == null || declaration.capacity() <= 0 || declaration.bindType() == ContainerBindType.PLAYER) continue;
            IInventory inventory = owner == null ? null : resolveInventory(owner, declaration, argsById);
            if (inventory == null) inventory = new InventoryBasic(declaration.id(), false, declaration.capacity());
            customInventories.add(inventory);
            int slotCount = Math.min(declaration.capacity(), inventory.getSizeInventory());
            for (int index = 0; index < slotCount; index++) {
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
    public void setWindowId(int windowId) { this.windowId = windowId; }

    private static List<ContainerDeclaration> normalizeDeclarations(EntityPlayerMP owner,
                                                                     List<ContainerDeclaration> input,
                                                                     Map<String, Map<String, String>> argsById) {
        if (input == null || input.isEmpty()) return List.of();
        ArrayList<ContainerDeclaration> result = new ArrayList<>(input.size());
        for (ContainerDeclaration declaration : input) {
            if (declaration == null) continue;
            int capacity = declaration.capacity();
            if (declaration.bindType() != ContainerBindType.PLAYER && capacity <= 0) {
                IInventory inventory = resolveInventory(owner, declaration, argsById);
                if (inventory != null) capacity = inventory.getSizeInventory();
            }
            result.add(new ContainerDeclaration(declaration.id(), declaration.bindType(), capacity, declaration.primary()));
        }
        return List.copyOf(result);
    }

    private static IInventory resolveInventory(EntityPlayerMP owner, ContainerDeclaration declaration,
                                               Map<String, Map<String, String>> argsById) {
        if (owner == null || declaration == null) return null;
        Map<String, String> args = argsById == null ? Map.of() : argsById.getOrDefault(declaration.id(), Map.of());
        switch (declaration.bindType()) {
            case BLOCK_ENTITY -> {
                Integer x = parseInt(args.get("x")), y = parseInt(args.get("y")), z = parseInt(args.get("z"));
                if (x == null || y == null || z == null) return null;
                TileEntity tile = owner.world.getTileEntity(new BlockPos(x, y, z));
                return tile instanceof IInventory inventory ? inventory : null;
            }
            case ENTITY -> {
                Integer id = parseInt(args.get("entity_id"));
                Entity entity = id == null ? null : owner.world.getEntityByID(id);
                return entity instanceof IInventory inventory ? inventory : null;
            }
            case SAVED_DATA -> {
                String dataName = args.getOrDefault("data_name", "apricityui_data");
                int capacity = Math.max(1, declaration.capacity());
                return SavedInventory.open(owner.world, dataName, declaration.id(), capacity);
            }
            default -> { return null; }
        }
    }

    private static Integer parseInt(String value) {
        try { return value == null ? null : Integer.parseInt(value.trim()); }
        catch (RuntimeException ignored) { return null; }
    }

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

    /** Persistent 1.12 WorldSavedData inventory used by SAVED_DATA bindings. */
    private static final class SavedInventory extends InventoryBasic {
        private final SavedInventoryData data;
        private final String inventoryKey;

        private SavedInventory(SavedInventoryData data, String inventoryKey, int size) {
            super(inventoryKey, false, size);
            this.data = data;
            this.inventoryKey = inventoryKey;
            NBTTagList list = data.inventoryTag(inventoryKey).getTagList("items", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound entry = list.getCompoundTagAt(i);
                int slot = entry.getByte("slot") & 255;
                if (slot < getSizeInventory()) setInventorySlotContents(slot, new ItemStack(entry));
            }
        }

        static SavedInventory open(World world, String dataName, String id, int size) {
            MapStorage storage = world.getMapStorage();
            if (storage == null) return new SavedInventory(new SavedInventoryData("unused"), id, size);
            String key = "apricityui:" + dataName;
            SavedInventoryData data = (SavedInventoryData) storage.getOrLoadData(SavedInventoryData.class, key);
            if (data == null) { data = new SavedInventoryData(key); storage.setData(key, data); }
            return new SavedInventory(data, id, size);
        }

        @Override public void markDirty() {
            NBTTagList list = new NBTTagList();
            for (int i = 0; i < getSizeInventory(); i++) {
                ItemStack stack = getStackInSlot(i);
                if (stack == null || stack.isEmpty()) continue;
                NBTTagCompound entry = new NBTTagCompound();
                entry.setByte("slot", (byte) i);
                stack.writeToNBT(entry);
                list.appendTag(entry);
            }
            data.inventoryTag(inventoryKey).setTag("items", list);
            data.markDirty();
            super.markDirty();
        }
    }

    public static final class SavedInventoryData extends WorldSavedData {
        private final NBTTagCompound inventories = new NBTTagCompound();
        public SavedInventoryData(String name) { super(name); }
        NBTTagCompound inventoryTag(String key) {
            if (!inventories.hasKey(key, 10)) inventories.setTag(key, new NBTTagCompound());
            return inventories.getCompoundTag(key);
        }
        @Override public void readFromNBT(NBTTagCompound nbt) {
            inventories.merge(nbt);
        }
        @Override public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
            nbt.merge(inventories);
            return nbt;
        }
    }
}
