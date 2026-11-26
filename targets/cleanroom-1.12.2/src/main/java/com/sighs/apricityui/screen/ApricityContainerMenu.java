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
    private Object machineTile;
    private MachineTelemetryMethods machineMethods;
    private int machineProgress;
    private int machineDuration;
    private long machineEnergy;
    private long machineCapacity;
    private int machineState;
    private boolean machineStructureValid = true;
    private long machineFluidInput;
    private long machineFluidOutput;
    private long machineEnergyIn;
    private long machineEnergyOut;
    private long machineHeat;
    private long machineMaxHeat = 10000L;
    private int machineMode;
    private int machineChambers;
    private long machineFuelRemaining;
    private boolean machineMeltdown;
    private boolean machineInputOvervoltage;
    private long machineExtraRate;
    private final long[] machineFluidInputs = new long[4];
    private final long[] machineFluidOutputs = new long[4];
    private boolean machineTelemetryInitialized;

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
            if (inventory != null && "com.sfr.machinelib.cleanroom.MachineTileEntity".equals(inventory.getClass().getName())) {
                machineTile = inventory;
                machineMethods = MachineTelemetryMethods.forClass(inventory.getClass());
            }
            if (declaration.capacity() <= 0) continue;
            int slotCount = declaration.capacity();
            // Keep the server's slot indices even if the client tile is not ready.
            if (inventory == null || (owner == null && inventory.getSizeInventory() < slotCount)) {
                inventory = new InventoryBasic(declaration.id(), false, slotCount);
            }
            customInventories.add(inventory);
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
    public int machineProgress() { return machineProgress; }
    public int machineDuration() { return machineDuration; }
    public long machineEnergy() { return machineEnergy; }
    public long machineCapacity() { return machineCapacity; }
    public int machineState() { return machineState; }
    public boolean machineStructureValid() { return machineStructureValid; }
    public long machineFluidInput() { return machineFluidInput; }
    public long machineFluidOutput() { return machineFluidOutput; }
    public long machineEnergyIn() { return machineEnergyIn; }
    public long machineEnergyOut() { return machineEnergyOut; }
    public long machineHeat() { return machineHeat; }
    public long machineMaxHeat() { return machineMaxHeat; }
    public int machineMode() { return machineMode; }
    public int machineChambers() { return machineChambers; }
    public long machineFuelRemaining() { return machineFuelRemaining; }
    public boolean machineMeltdown() { return machineMeltdown; }
    public boolean machineInputOvervoltage() { return machineInputOvervoltage; }
    public long machineExtraRate() { return machineExtraRate; }
    public long machineFluidInput(int slot) { return slot >= 0 && slot < machineFluidInputs.length ? machineFluidInputs[slot] : 0L; }
    public long machineFluidOutput(int slot) { return slot >= 0 && slot < machineFluidOutputs.length ? machineFluidOutputs[slot] : 0L; }

    @Override public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (machineTile == null || machineMethods == null) return;
        int progress = machineMethods.invokeInt(machineTile, machineMethods.progressValue);
        int duration = machineMethods.invokeInt(machineTile, machineMethods.durationValue);
        long energy = machineMethods.invokeLong(machineTile, machineMethods.energyStored);
        long capacity = machineMethods.invokeLong(machineTile, machineMethods.energyCapacity);
        int state = machineMethods.invokeInt(machineTile, machineMethods.stateValue);
        boolean structureValid = machineMethods.invokeBoolean(machineTile, machineMethods.isStructureValid, true);
        long energyIn = machineMethods.invokeLong(machineTile, machineMethods.energyInPerTick);
        long energyOut = machineMethods.invokeLong(machineTile, machineMethods.energyOutPerTick);
        long heat = machineMethods.invokeLong(machineTile, machineMethods.machineHeat);
        long maxHeat = Math.max(1L, machineMethods.invokeLong(machineTile, machineMethods.machineMaxHeat));
        int mode = machineMethods.invokeInt(machineTile, machineMethods.machineMode);
        int chambers = machineMethods.invokeInt(machineTile, machineMethods.machineChambers);
        long fuelRemaining = machineMethods.invokeLong(machineTile, machineMethods.machineFuelRemaining);
        boolean meltdown = machineMethods.invokeBoolean(machineTile, machineMethods.machineMeltdown, false);
        boolean inputOvervoltage = machineMethods.invokeBoolean(machineTile, machineMethods.isInputOvervoltage, false);
        long extraRate = machineMethods.invokeLong(machineTile, machineMethods.machineExtraRate);
        long fluidInput = machineMethods.invokeFluid(machineTile, false, 0);
        long fluidOutput = machineMethods.invokeFluid(machineTile, true, 0);
        long[] fluidInputs = new long[machineFluidInputs.length];
        long[] fluidOutputs = new long[machineFluidOutputs.length];
        boolean fluidSlotsChanged = false;
        for (int slot = 0; slot < fluidInputs.length; slot++) {
            fluidInputs[slot] = machineMethods.invokeFluid(machineTile, false, slot);
            fluidOutputs[slot] = machineMethods.invokeFluid(machineTile, true, slot);
            fluidSlotsChanged |= fluidInputs[slot] != machineFluidInputs[slot] || fluidOutputs[slot] != machineFluidOutputs[slot];
        }
        if (machineTelemetryInitialized
                && progress == machineProgress && duration == machineDuration && energy == machineEnergy
                && capacity == machineCapacity && state == machineState && structureValid == machineStructureValid
                && energyIn == machineEnergyIn && energyOut == machineEnergyOut
                && heat == machineHeat && maxHeat == machineMaxHeat && mode == machineMode
                && chambers == machineChambers && fuelRemaining == machineFuelRemaining
                && meltdown == machineMeltdown && inputOvervoltage == machineInputOvervoltage && extraRate == machineExtraRate
                && fluidInput == machineFluidInput && fluidOutput == machineFluidOutput && !fluidSlotsChanged) return;
        machineTelemetryInitialized = true;
        machineProgress = progress; machineDuration = duration; machineEnergy = energy;
        machineCapacity = capacity; machineState = state; machineStructureValid = structureValid;
        machineEnergyIn = energyIn; machineEnergyOut = energyOut;
        machineHeat = heat; machineMaxHeat = maxHeat; machineMode = mode;
        machineChambers = chambers; machineFuelRemaining = fuelRemaining;
        machineMeltdown = meltdown; machineExtraRate = extraRate;
        machineInputOvervoltage = inputOvervoltage;
        machineFluidInput = fluidInput; machineFluidOutput = fluidOutput;
        System.arraycopy(fluidInputs, 0, machineFluidInputs, 0, fluidInputs.length);
        System.arraycopy(fluidOutputs, 0, machineFluidOutputs, 0, fluidOutputs.length);
        for (net.minecraft.inventory.IContainerListener listener : listeners) {
            sendInt32(listener, 0, progress);
            sendInt32(listener, 2, duration);
            sendLong64(listener, 4, energy);
            sendLong64(listener, 8, capacity);
            listener.sendWindowProperty(this, 12, state);
            listener.sendWindowProperty(this, 13, structureValid ? 1 : 0);
            for (int slot = 0; slot < machineFluidInputs.length; slot++) {
                int base = 16 + slot * 8;
                sendLong64(listener, base, fluidInputs[slot]);
                sendLong64(listener, base + 4, fluidOutputs[slot]);
            }
            sendLong64(listener, 48, energyIn);
            sendLong64(listener, 52, energyOut);
            sendLong64(listener, 56, heat);
            sendLong64(listener, 60, maxHeat);
            listener.sendWindowProperty(this, 64, mode);
            listener.sendWindowProperty(this, 65, chambers);
            listener.sendWindowProperty(this, 66, meltdown ? 1 : 0);
            sendLong64(listener, 68, fuelRemaining);
            sendLong64(listener, 72, extraRate);
            listener.sendWindowProperty(this, 76, inputOvervoltage ? 1 : 0);
        }
    }

    private void sendInt32(net.minecraft.inventory.IContainerListener listener, int baseId, int value) {
        listener.sendWindowProperty(this, baseId, value & 0xFFFF);
        listener.sendWindowProperty(this, baseId + 1, (value >>> 16) & 0xFFFF);
    }

    private void sendLong64(net.minecraft.inventory.IContainerListener listener, int baseId, long value) {
        for (int chunk = 0; chunk < 4; chunk++) {
            listener.sendWindowProperty(this, baseId + chunk, (int) ((value >>> (chunk * 16)) & 0xFFFFL));
        }
    }

    private static int mergeInt16(int current, int chunkIndex, int value) {
        int shift = chunkIndex * 16;
        return (current & ~(0xFFFF << shift)) | ((value & 0xFFFF) << shift);
    }

    private static long mergeLong16(long current, int chunkIndex, int value) {
        int shift = chunkIndex * 16;
        return (current & ~(0xFFFFL << shift)) | ((value & 0xFFFFL) << shift);
    }

    @Override public void updateProgressBar(int id, int value) {
        if (id == 0 || id == 1) {
            machineProgress = mergeInt16(machineProgress, id, value);
        } else if (id == 2 || id == 3) {
            machineDuration = mergeInt16(machineDuration, id - 2, value);
        } else if (id >= 4 && id < 8) {
            machineEnergy = mergeLong16(machineEnergy, id - 4, value);
        } else if (id >= 8 && id < 12) {
            machineCapacity = mergeLong16(machineCapacity, id - 8, value);
        } else if (id == 12) {
            machineState = value;
        } else if (id == 13) {
            machineStructureValid = value != 0;
        } else if (id >= 16 && id < 16 + machineFluidInputs.length * 8) {
            int offset = id - 16;
            int slot = offset / 8;
            int field = offset % 8;
            if (field < 4) {
                machineFluidInputs[slot] = mergeLong16(machineFluidInputs[slot], field, value);
                if (slot == 0) machineFluidInput = machineFluidInputs[0];
            } else {
                machineFluidOutputs[slot] = mergeLong16(machineFluidOutputs[slot], field - 4, value);
                if (slot == 0) machineFluidOutput = machineFluidOutputs[0];
            }
        } else if (id >= 48 && id < 52) {
            machineEnergyIn = mergeLong16(machineEnergyIn, id - 48, value);
        } else if (id >= 52 && id < 56) {
            machineEnergyOut = mergeLong16(machineEnergyOut, id - 52, value);
        } else if (id >= 56 && id < 60) {
            machineHeat = mergeLong16(machineHeat, id - 56, value);
        } else if (id >= 60 && id < 64) {
            machineMaxHeat = mergeLong16(machineMaxHeat, id - 60, value);
        } else if (id == 64) {
            machineMode = value;
        } else if (id == 65) {
            machineChambers = value;
        } else if (id == 66) {
            machineMeltdown = value != 0;
        } else if (id >= 68 && id < 72) {
            machineFuelRemaining = mergeLong16(machineFuelRemaining, id - 68, value);
        } else if (id >= 72 && id < 76) {
            machineExtraRate = mergeLong16(machineExtraRate, id - 72, value);
        } else if (id == 76) {
            machineInputOvervoltage = value != 0;
        } else {
            super.updateProgressBar(id, value);
        }
    }

    private static final class MachineTelemetryMethods {
        private static final java.util.concurrent.ConcurrentHashMap<Class<?>, MachineTelemetryMethods> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
        final java.lang.reflect.Method progressValue;
        final java.lang.reflect.Method durationValue;
        final java.lang.reflect.Method energyStored;
        final java.lang.reflect.Method energyCapacity;
        final java.lang.reflect.Method stateValue;
        final java.lang.reflect.Method isStructureValid;
        final java.lang.reflect.Method fluidAmount;
        final java.lang.reflect.Method energyInPerTick;
        final java.lang.reflect.Method energyOutPerTick;
        final java.lang.reflect.Method machineHeat;
        final java.lang.reflect.Method machineMaxHeat;
        final java.lang.reflect.Method machineMode;
        final java.lang.reflect.Method machineChambers;
        final java.lang.reflect.Method machineFuelRemaining;
        final java.lang.reflect.Method machineMeltdown;
        final java.lang.reflect.Method isInputOvervoltage;
        final java.lang.reflect.Method machineExtraRate;

        private MachineTelemetryMethods(Class<?> type) {
            this.progressValue = find(type, "progressValue");
            this.durationValue = find(type, "durationValue");
            this.energyStored = find(type, "energyStored");
            this.energyCapacity = find(type, "energyCapacity");
            this.stateValue = find(type, "stateValue");
            this.isStructureValid = find(type, "isStructureValid");
            this.fluidAmount = find(type, "fluidAmount", boolean.class, int.class);
            this.energyInPerTick = find(type, "energyInPerTick");
            this.energyOutPerTick = find(type, "energyOutPerTick");
            this.machineHeat = find(type, "machineHeat");
            this.machineMaxHeat = find(type, "machineMaxHeat");
            this.machineMode = find(type, "machineMode");
            this.machineChambers = find(type, "machineChambers");
            this.machineFuelRemaining = find(type, "machineFuelRemaining");
            this.machineMeltdown = find(type, "machineMeltdown");
            this.isInputOvervoltage = find(type, "isInputOvervoltage");
            this.machineExtraRate = find(type, "machineExtraRate");
        }

        static MachineTelemetryMethods forClass(Class<?> type) {
            return CACHE.computeIfAbsent(type, MachineTelemetryMethods::new);
        }

        private static java.lang.reflect.Method find(Class<?> type, String name, Class<?>... params) {
            try { return type.getMethod(name, params); }
            catch (Throwable ignored) { return null; }
        }

        int invokeInt(Object target, java.lang.reflect.Method m) {
            if (m == null) return 0;
            try { return ((Number) m.invoke(target)).intValue(); }
            catch (Throwable ignored) { return 0; }
        }

        long invokeLong(Object target, java.lang.reflect.Method m) {
            if (m == null) return 0L;
            try { return ((Number) m.invoke(target)).longValue(); }
            catch (Throwable ignored) { return 0L; }
        }

        boolean invokeBoolean(Object target, java.lang.reflect.Method m, boolean fallback) {
            if (m == null) return fallback;
            try { return ((Boolean) m.invoke(target)).booleanValue(); }
            catch (Throwable ignored) { return fallback; }
        }

        long invokeFluid(Object target, boolean output, int slot) {
            if (fluidAmount == null) return 0L;
            try { return ((Number) fluidAmount.invoke(target, output, slot)).longValue(); }
            catch (Throwable ignored) { return 0L; }
        }
    }

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
            if (declaration.bindType() != ContainerBindType.PLAYER) {
                IInventory inventory = resolveInventory(owner, declaration, argsById);
                if (inventory != null) {
                    int available = inventory.getSizeInventory();
                    capacity = capacity <= 0 ? available : Math.min(capacity, available);
                }
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
