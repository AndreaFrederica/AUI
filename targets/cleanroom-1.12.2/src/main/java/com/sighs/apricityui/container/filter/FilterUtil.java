package com.sighs.apricityui.container.filter;

import com.sighs.apricityui.spi.AuiBindingFilter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.function.Predicate;

/** 1.12 item insertion filter used by Cleanroom container bindings. */
@FunctionalInterface
public interface FilterUtil extends AuiBindingFilter {
    FilterUtil ANY = stack -> true;
    FilterUtil NONE = stack -> false;
    FilterUtil EMPTY = stack -> stack == null || stack.isEmpty();

    boolean test(ItemStack stack);

    static FilterUtil item(Item item) {
        return item == null ? NONE : stack -> stack != null && !stack.isEmpty() && stack.getItem() == item;
    }

    static FilterUtil item(String registryName) {
        if (registryName == null || registryName.isBlank()) return NONE;
        String expected = registryName.startsWith("#") ? registryName.substring(1) : registryName;
        return stack -> stack != null && !stack.isEmpty() && stack.getItem().getRegistryName() != null
                && expected.equals(stack.getItem().getRegistryName().toString());
    }

    static FilterUtil custom(Predicate<ItemStack> predicate) {
        return predicate == null ? NONE : stack -> stack != null && predicate.test(stack);
    }

    static FilterUtil allOf(FilterUtil... filters) {
        if (filters == null || filters.length == 0) return ANY;
        return stack -> {
            for (FilterUtil filter : filters) if (filter != null && !filter.test(stack)) return false;
            return true;
        };
    }

    static FilterUtil anyOf(FilterUtil... filters) {
        if (filters == null || filters.length == 0) return NONE;
        return stack -> {
            for (FilterUtil filter : filters) if (filter != null && filter.test(stack)) return true;
            return false;
        };
    }

    static FilterUtil not(FilterUtil filter) { return filter == null ? ANY : stack -> !filter.test(stack); }
}
