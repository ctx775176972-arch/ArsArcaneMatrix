package dev.arsmatrix.item;

import com.hollingsworth.arsnouveau.api.item.ArsNouveauCurio;
import com.hollingsworth.arsnouveau.api.item.ICasterTool;
import com.hollingsworth.arsnouveau.api.spell.*;
import com.hollingsworth.arsnouveau.common.spell.method.MethodSelf;
import com.hollingsworth.arsnouveau.setup.registry.DataComponentRegistry;
import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.menu.WizardsPocketWatchMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;

import java.util.List;

/** Experimental self-casting curio. Uses the native scribing protocol and spell resolver. */
public final class WizardsPocketWatchItem extends ArsNouveauCurio implements ICasterTool {
    private static final String INTERVAL = "matrix_watch_interval";
    private static final String ENABLED = "matrix_watch_enabled";
    private static final String NEXT = "matrix_watch_next";
    private static final String PLAYER_GATE = "ars_matrix_watch_gate";
    public static final int DEFAULT_SECONDS = 31;
    public static final int MIN_SECONDS = 5;
    public static final int MAX_SECONDS = 3600;

    public WizardsPocketWatchItem() { super(new Properties().stacksTo(1)); }

    @Override public AbstractCaster<?> getSpellCaster(ItemStack stack) {
        return stack.getOrDefault(DataComponentRegistry.SPELL_CASTER.get(), new SpellCaster());
    }

    // Same input rule as Enchanter's Mirror: effects/augments only; Self is inserted by the item.
    @Override public boolean isScribedSpellValid(AbstractCaster<?> caster, Player player,
                                                InteractionHand hand, ItemStack stack, Spell spell) {
        return !spell.isEmpty() && spell.unsafeList().stream().noneMatch(p -> p instanceof AbstractCastMethod);
    }

    @Override public void scribeModifiedSpell(AbstractCaster<?> caster, Player player,
                                              InteractionHand hand, ItemStack stack, Spell.Mutable spell) {
        var recipe = new java.util.ArrayList<AbstractSpellPart>();
        recipe.add(MethodSelf.INSTANCE);
        recipe.addAll(spell.recipe);
        spell.recipe = recipe;
    }

    @Override public void sendInvalidMessage(Player player) {
        player.displayClientMessage(Component.translatable("ars_nouveau.mirror.invalid"), true);
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
    public static int interval(ItemStack stack) {
        var tag = data(stack);
        return tag.contains(INTERVAL) ? Math.clamp(tag.getInt(INTERVAL), MIN_SECONDS, MAX_SECONDS) : DEFAULT_SECONDS;
    }
    public static boolean enabled(ItemStack stack) {
        var tag = data(stack);
        return !tag.contains(ENABLED) || tag.getBoolean(ENABLED);
    }
    public static void setInterval(ItemStack stack, int seconds) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putInt(INTERVAL, Math.clamp(seconds, MIN_SECONDS, MAX_SECONDS)));
    }
    public static void setEnabled(ItemStack stack, boolean enabled) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(ENABLED, enabled));
    }

    @Override public boolean canEquipFromUse(SlotContext context, ItemStack stack) { return false; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inv, p) ->
                    new WizardsPocketWatchMenu(id, inv, hand), stack.getHoverName()), buf -> buf.writeEnum(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override public void curioTick(SlotContext slot, ItemStack stack) {
        if (!(slot.entity() instanceof ServerPlayer player) || player.isSpectator()
                || !player.isAlive() || player.tickCount % 20 != 0 || !enabled(stack)) return;
        long now = player.server.overworld().getGameTime();
        long next = data(stack).getLong(NEXT);
        long gate = player.getPersistentData().getLong(PLAYER_GATE);
        if (next > now && next <= now + MAX_SECONDS * 20L || gate > now && gate <= now + 100L) return;
        Spell spell = getSpellCaster(stack).getSpell();
        if (spell.isEmpty() || spell.getCastMethod() != MethodSelf.INSTANCE) return;
        // Persist per-item cooldown and share a five-second gate across all watches on a player.
        // Failed attempts are not queued, and removing/re-equipping cannot reset either timer.
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(NEXT, now + interval(stack) * 20L));
        player.getPersistentData().putLong(PLAYER_GATE, now + 100L);
        try {
            SpellResolver resolver = new SpellResolver(SpellContext.fromEntity(spell, player, stack)).withSilent(true);
            resolver.onCast(stack, player.level());
        } catch (RuntimeException exception) {
            setEnabled(stack, false);
            ArsArcaneMatrix.LOGGER.error("Paused experimental pocket watch after a spell error for {}",
                    player.getUUID(), exception);
            player.displayClientMessage(Component.translatable("item.ars_arcane_matrix.wizards_pocket_watch.error"), true);
        }
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("item.ars_arcane_matrix.wizards_pocket_watch.prototype"));
        lines.add(Component.translatable("item.ars_arcane_matrix.wizards_pocket_watch.help"));
        lines.add(Component.translatable("gui.ars_arcane_matrix.watch.interval", interval(stack)));
        lines.add(Component.translatable(enabled(stack) ? "gui.ars_arcane_matrix.watch.enabled" : "gui.ars_arcane_matrix.watch.paused"));
        getInformation(stack, context, lines, flag);
    }
}
