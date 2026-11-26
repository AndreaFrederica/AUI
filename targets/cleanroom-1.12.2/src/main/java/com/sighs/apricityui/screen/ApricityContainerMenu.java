package com.sighs.apricityui.screen;

import com.sighs.apricityui.container.bind.ContainerBindType;
import com.sighs.apricityui.element.ContainerDeclaration;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
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
import com.sighs.apricityui.container.filter.FilterUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Basic 1.12 menu implementation used by the Cleanroom client bridge. */
public class ApricityContainerMenu extends Container {
    private final String templatePath;
    private final List<ContainerDeclaration> declarations;
    private final List<IInventory> customInventories = new ArrayList<>();
    private final EntityPlayerMP owner;
    private final Map<String, Map<String, String>> bindingArgs;
    /** Server constructor: resolve declarations against real 1.12 world data. */
    public ApricityContainerMenu(EntityPlayerMP owner, String templatePath,
                                 List<ContainerDeclaration> declarations,
                                 Map<String, Map<String, String>> argsById) {
        this(owner, templatePath, declarations, argsById, Map.of());
    }

    public ApricityContainerMenu(EntityPlayerMP owner, String templatePath,
                                 List<ContainerDeclaration> declarations,
                                 Map<String, Map<String, String>> argsById,
                                 Map<String, FilterUtil> filtersById) {
        this(owner.inventory, templatePath, normalizeDeclarations(owner, declarations, argsById), argsById, owner, filtersById);
    }

    public ApricityContainerMenu(InventoryPlayer playerInventory, String templatePath,
                                 List<ContainerDeclaration> declarations) {
        this(playerInventory, templatePath, declarations, Map.of(), null, Map.of());
    }

    public ApricityContainerMenu(InventoryPlayer playerInventory, String templatePath,
                                 List<ContainerDeclaration> declarations,
                                 Map<String, Map<String, String>> argsById) {
        this(playerInventory, templatePath, declarations, argsById, null, Map.of());
    }

    private ApricityContainerMenu(InventoryPlayer playerInventory, String templatePath,
                                  List<ContainerDeclaration> declarations,
                                  Map<String, Map<String, String>> argsById,
                                  EntityPlayerMP owner,
                                  Map<String, FilterUtil> filtersById) {
        this.templatePath = templatePath == null ? "" : templatePath;
        this.declarations = declarations == null ? List.of() : List.copyOf(declarations);
        this.owner = owner;
        this.bindingArgs = argsById == null ? Map.of() : Map.copyOf(argsById);
        Map<String, FilterUtil> usableFilters = filtersById == null ? Map.of() : filtersById;
        int customRow = 0;
        for (ContainerDeclaration declaration : this.declarations) {
            if (declaration == null || declaration.bindType() == ContainerBindType.PLAYER) continue;
            EntityPlayer contextPlayer = owner != null ? owner : (playerInventory == null ? null : playerInventory.player);
            IInventory inventory = contextPlayer == null ? null : resolveInventory(contextPlayer, declaration, argsById);
            if (declaration.capacity() <= 0) continue;
            if (inventory == null) inventory = new InventoryBasic(declaration.id(), false, declaration.capacity());
            customInventories.add(inventory);
            int slotCount = Math.min(declaration.capacity(), inventory.getSizeInventory());
            for (int index = 0; index < slotCount; index++) {
                int column = index % 9;
                int row = index / 9;
                addSlotToContainer(new UiSlot(inventory, index, 8 + column * 18, 8 + (customRow + row) * 18,
                        usableFilters.get(declaration.id())));
            }
            customRow += Math.max(1, (declaration.capacity() + 8) / 9) + 1;
        }
        int playerTop = 8 + customRow * 18;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlotToContainer(new UiSlot(playerInventory, column + row * 9 + 9, 8 + column * 18, playerTop + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlotToContainer(new UiSlot(playerInventory, column, 8 + column * 18, playerTop + 58));
        }
    }

    public String getTemplatePath() { return templatePath; }
    public List<ContainerDeclaration> getDeclarations() { return declarations; }
    public void setWindowId(int windowId) { this.windowId = windowId; }
    public Integer resolveGlobalSlotIndex(String id, int localIndex) {
        if (id == null || localIndex < 0) return null;
        int customOffset = 0;
        for (ContainerDeclaration declaration : declarations) {
            if (declaration == null) continue;
            if (declaration.bindType() == ContainerBindType.PLAYER) {
                if (id.equals(declaration.id()) && localIndex < 36) {
                    // InventoryPlayer stores hotbar at 0..8 and main inventory at 9..35,
                    // while Container adds main inventory (0..26) before hotbar (27..35).
                    int menuOffset = localIndex < 9 ? (27 + localIndex) : (localIndex - 9);
                    return inventorySlots.size() - 36 + menuOffset;
                }
                continue;
            }
            int count = Math.max(0, declaration.capacity());
            if (id.equals(declaration.id())) return localIndex < count ? customOffset + localIndex : null;
            customOffset += count;
        }
        return null;
    }

    /** Projects SLOT DOM geometry onto vanilla slot positions after layout. */
    public void updateSlotLayout(Document document, int left, int top) {
        if (document == null) return;
        float scale = document.getViewport() == null ? 1.0f : document.getViewport().renderScale();
        int declarationIndex = 0;
        for (Element element : document.getElements()) {
            if (!(element instanceof com.sighs.apricityui.element.Slot domSlot)) continue;
            com.sighs.apricityui.element.Container container = ancestorContainer(domSlot);
            if (container == null) continue;
            String id = container.getAttribute("id");
            if (id == null || id.isBlank()) {
                id = declarationIndex < declarations.size() ? declarations.get(declarationIndex).id() : "";
                declarationIndex++;
            }
            int local = domSlot.getSlotIndex();
            Integer global = resolveGlobalSlotIndex(id, local);
            if (global == null || global < 0 || global >= inventorySlots.size()) continue;
            Element.DOMRect rect = domSlot.getSlotVisualBounds();
            if (rect == null) continue;
            int scaledX = (int) Math.round(rect.x * scale);
            int scaledY = (int) Math.round(rect.y * scale);
            int scaledW = Math.max(1, (int) Math.round(rect.width * scale));
            int scaledH = Math.max(1, (int) Math.round(rect.height * scale));
            // Center the 16x16 vanilla item inside the DOM slot's border-box (18x18 -> +1px inset).
            int insetX = Math.max(0, (scaledW - 16) / 2);
            int insetY = Math.max(0, (scaledH - 16) / 2);
            Slot slot = inventorySlots.get(global);
            if (slot instanceof UiSlot uiSlot) {
                domSlot.bindToMenuSlot(domSlot.isExplicitlyDisabled());
                uiSlot.setUiDisabled(domSlot.isExplicitlyDisabled());
                uiSlot.setUiHidden(!domSlot.shouldRenderItem());
                uiSlot.setUiSlotBounds(scaledW, scaledH);
                slot.xPos = uiSlot.isUiHidden() ? -10000 : scaledX + insetX;
                slot.yPos = uiSlot.isUiHidden() ? -10000 : scaledY + insetY;
            } else {
                slot.xPos = scaledX + insetX;
                slot.yPos = scaledY + insetY;
            }
        }
    }

    private static com.sighs.apricityui.element.Container ancestorContainer(Element element) {
        Element current = element == null ? null : element.parentElement;
        while (current != null) {
            if (current instanceof com.sighs.apricityui.element.Container container) return container;
            current = current.parentElement;
        }
        return null;
    }

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

    private static IInventory resolveInventory(EntityPlayer owner, ContainerDeclaration declaration,
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

    @Override public boolean canInteractWith(EntityPlayer player) {
        if (player == null || player.isDead) return false;
        if (owner != null && player != owner) return false;
        for (ContainerDeclaration declaration : declarations) {
            if (declaration == null) continue;
            Map<String, String> args = bindingArgs.getOrDefault(declaration.id(), Map.of());
            if (declaration.bindType() == ContainerBindType.BLOCK_ENTITY) {
                Integer x = parseInt(args.get("x")), y = parseInt(args.get("y")), z = parseInt(args.get("z"));
                if (x != null && y != null && z != null
                        && player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D) > 64.0D) return false;
            } else if (declaration.bindType() == ContainerBindType.ENTITY) {
                Integer id = parseInt(args.get("entity_id"));
                Entity entity = id == null ? null : player.world.getEntityByID(id);
                if (entity != null && player.getDistanceSq(entity) > 64.0D) return false;
            }
        }
        return true;
    }

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
        source.onTake(player, copy);
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
        private boolean loading;

        private SavedInventory(SavedInventoryData data, String inventoryKey, int size) {
            super(inventoryKey, false, size);
            this.data = data;
            this.inventoryKey = inventoryKey;
            this.loading = true;
            try {
                NBTTagList list = data.inventoryTag(inventoryKey).getTagList("items", 10);
                for (int i = 0; i < list.tagCount(); i++) {
                    NBTTagCompound entry = list.getCompoundTagAt(i);
                    int slot = entry.getByte("slot") & 255;
                    if (slot < getSizeInventory()) setInventorySlotContents(slot, new ItemStack(entry));
                }
            } finally {
                this.loading = false;
            }
        }

        static SavedInventory open(World world, String dataName, String id, int size) {
            MapStorage storage = world.getMapStorage();
            if (storage == null) return new SavedInventory(new SavedInventoryData("unused"), id, size);
            // MapStorage writes <world>/data/<key>.dat; colons are illegal in Windows filenames.
            String safeName = (dataName == null || dataName.isBlank() ? "apricityui_data" : dataName)
                    .replaceAll("[^a-zA-Z0-9._-]", "_");
            String key = "apricityui_" + safeName;
            SavedInventoryData data = (SavedInventoryData) storage.getOrLoadData(SavedInventoryData.class, key);
            if (data == null) { data = new SavedInventoryData(key); storage.setData(key, data); }
            return new SavedInventory(data, id, size);
        }

        @Override public void markDirty() {
            if (loading) return;
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

    public static class UiSlot extends Slot {
        private final FilterUtil filter;
        private boolean uiDisabled;
        private boolean uiHidden;
        private int uiWidth = 16, uiHeight = 16;
        public UiSlot(IInventory inventory, int index, int x, int y) { this(inventory, index, x, y, null); }
        public UiSlot(IInventory inventory, int index, int x, int y, FilterUtil filter) {
            super(inventory, index, x, y); this.filter = filter;
        }
        @Override public boolean isItemValid(ItemStack stack) {
            return !uiDisabled && super.isItemValid(stack)
                    && (inventory == null || inventory.isItemValidForSlot(getSlotIndex(), stack))
                    && (filter == null || filter.test(stack));
        }
        @Override public boolean canTakeStack(EntityPlayer player) { return !uiDisabled && super.canTakeStack(player); }
        public void setUiDisabled(boolean value) { uiDisabled = value; }
        public void setUiHidden(boolean value) { uiHidden = value; }
        public boolean isUiHidden() { return uiHidden; }
        public void setUiSlotBounds(int width, int height) { uiWidth = Math.max(1, width); uiHeight = Math.max(1, height); }
        public int getUiSlotWidth() { return uiWidth; }
        public int getUiSlotHeight() { return uiHeight; }
    }
}
