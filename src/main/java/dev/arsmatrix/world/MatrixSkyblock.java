package dev.arsmatrix.world;

import com.hollingsworth.arsnouveau.api.item.ICasterTool;
import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.common.spell.method.MethodTouch;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectCrush;
import com.hollingsworth.arsnouveau.common.spell.effect.EffectPhantomBlock;
import com.hollingsworth.arsnouveau.common.spell.augment.AugmentAmplify;
import dev.arsmatrix.config.MatrixCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class MatrixSkyblock {
    private MatrixSkyblock() {}
    public static boolean isSkyblock(ChunkGenerator generator) {
        return generator instanceof NoiseBasedChunkGenerator noise && noise.generatorSettings().unwrapKey()
                .map(key -> key.location().getNamespace().equals("ars_arcane_matrix")
                        && key.location().getPath().startsWith("skyblock_")).orElse(false);
    }
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !MatrixCommonConfig.ENABLE_SKYBLOCK.get()
                || !isSkyblock(player.server.overworld().getChunkSource().getGenerator())
                || player.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)
                        .getBoolean("matrixSkyblockStarter")) return;
        var persisted = player.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
        persisted.putBoolean("matrixSkyblockStarter", true);
        player.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, persisted);
        var overworld = player.server.overworld();
        overworld.setDefaultSpawnPos(new BlockPos(0,81,0),0);
        player.setRespawnPosition(overworld.dimension(), new BlockPos(0,81,0),0,true,false);
        player.teleportTo(overworld,0.5,81,0.5,java.util.Set.of(),0,0);
        give(player, new ItemStack(Items.BONE_MEAL,64));
        give(player, tome(new Spell(MethodTouch.INSTANCE, EffectPhantomBlock.INSTANCE, AugmentAmplify.INSTANCE)));
        give(player, tome(new Spell(MethodTouch.INSTANCE, EffectCrush.INSTANCE)));
    }
    private static ItemStack item(String id,int count) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)),count);
    }
    private static ItemStack tome(Spell spell) {
        ItemStack stack=item("ars_nouveau:caster_tome",1);
        if (stack.getItem() instanceof ICasterTool tool) {
            var caster = tool.getSpellCaster(stack);
            if (caster != null) caster.setSpell(spell).saveToStack(stack);
        }
        return stack;
    }
    private static void give(ServerPlayer player,ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack,false);
    }
}
