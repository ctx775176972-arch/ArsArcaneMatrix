package dev.arsmatrix.item;

import com.hollingsworth.arsnouveau.common.items.ItemScroll;
import com.hollingsworth.arsnouveau.common.items.data.ItemScrollData;
import com.hollingsworth.arsnouveau.setup.registry.DataComponentRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Internal scroll installed by the logistics hub into native transport behavior. */
public final class HubFilterScrollItem extends ItemScroll {
    public HubFilterScrollItem(Item.Properties properties) { super(properties); }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag settings = stack.getOrDefault(
                DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return Component.translatable(settings.getBoolean("Allow")
                ? "item.ars_arcane_matrix.hub_filter_scroll.allow"
                : "item.ars_arcane_matrix.hub_filter_scroll.deny");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CompoundTag settings = stack.getOrDefault(
                DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String mode = settings.getString("Mode").toLowerCase(java.util.Locale.ROOT);
        if (mode.isBlank()) mode = "item";
        tooltip.add(Component.translatable(
                "tooltip.ars_arcane_matrix.hub_filter_scroll.match." + mode));
    }

    @Override
    public SortPref getSortPref(ItemStack candidate, ItemStack scroll, IItemHandler inventory) {
        ItemScrollData data = scroll.getOrDefault(
                DataComponentRegistry.ITEM_SCROLL_DATA.get(), new ItemScrollData());
        List<ItemStack> templates = new java.util.ArrayList<>();
        data.getItems().forEach(templates::add);
        CompoundTag settings = scroll.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        boolean allow = settings.getBoolean("Allow");
        MatchMode parsedMode;
        try { parsedMode = MatchMode.valueOf(settings.getString("Mode")); }
        catch (IllegalArgumentException ignored) { parsedMode = MatchMode.ITEM; }
        final MatchMode mode = parsedMode;
        boolean matches = templates.stream().anyMatch(template -> matches(candidate, template, mode));
        boolean accepted = allow ? matches : !matches;
        return accepted ? (allow ? SortPref.HIGHEST : SortPref.LOW) : SortPref.INVALID;
    }

    private static boolean matches(ItemStack candidate, ItemStack template, MatchMode mode) {
        if (candidate.isEmpty() || template.isEmpty()) return false;
        return switch (mode) {
            case EXACT -> ItemStack.isSameItemSameComponents(candidate, template);
            case ITEM -> candidate.is(template.getItem());
            case TAG -> sharesTag(candidate, template);
            case MOD -> BuiltInRegistries.ITEM.getKey(candidate.getItem()).getNamespace().equals(
                    BuiltInRegistries.ITEM.getKey(template.getItem()).getNamespace());
        };
    }

    private static boolean sharesTag(ItemStack first, ItemStack second) {
        Set<net.minecraft.resources.ResourceLocation> tags = new HashSet<>();
        first.getTags().map(net.minecraft.tags.TagKey::location).forEach(tags::add);
        return second.getTags().map(net.minecraft.tags.TagKey::location).anyMatch(tags::contains);
    }

    public enum MatchMode { EXACT, ITEM, TAG, MOD }
}
