package net.minecraft.world.item;

/** Opaque item-stack facade reserved for common networking signatures. */
public class ItemStack {
    public static final ItemStack EMPTY = new ItemStack();
    private boolean empty = true;
    public ItemStack() { }
    public boolean isEmpty() { return empty; }
}
