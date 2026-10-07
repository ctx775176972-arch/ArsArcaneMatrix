package dev.arsmatrix.mixin;

import dev.arsmatrix.world.MatrixSkyblock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only entities serialized by world generation; never saved player entities. */
@Mixin(net.minecraft.world.level.chunk.status.ChunkStatusTasks.class)
public abstract class SkyblockGeneratedEntitiesMixin {
    @Inject(method = "postLoadProtoChunk", at = @At("HEAD"), cancellable = true)
    private static void matrix$skipGeneratedEntities(ServerLevel level, List<CompoundTag> entityTags, CallbackInfo ci) {
        if (MatrixSkyblock.isSkyblock(level.getChunkSource().getGenerator())) ci.cancel();
    }
}
