package dev.arsmatrix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.arsmatrix.blockentity.ArcaneMineCoreBlockEntity;
import dev.arsmatrix.config.MatrixConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/** Renders the inverted-beacon scan beam above a formed Arcane Mine. */
public final class ArcaneMineCoreRenderer implements BlockEntityRenderer<ArcaneMineCoreBlockEntity> {

    private static final int ACTIVE_BEAM_COLOR = 0xFF8B5CFF;
    private static final int AMPLIFIED_BEAM_COLOR = 0x78E0A52B;
    private static final int PAUSED_BEAM_COLOR = 0xFF651018;

    public ArcaneMineCoreRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
            ArcaneMineCoreBlockEntity core,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        StructurePreviewRenderer.renderMine(core.getBlockPos(), poseStack, bufferSource);

        int completedLayers = core.getCompletedLayers();
        if (!core.isActive() || completedLayers <= 0 || core.getLevel() == null) {
            return;
        }

        long gameTime = core.getLevel().getGameTime();
        boolean paused = core.isRedstonePaused();
        int amplifierCount = core.getAmplifierCount();

        if (!paused && amplifierCount > 0) {
            float amplifierStrength = Math.min(amplifierCount, 4) / 4.0F;
            BeaconRenderer.renderBeaconBeam(
                    poseStack,
                    bufferSource,
                    BeaconRenderer.BEAM_LOCATION,
                    partialTick,
                    1.0F,
                    gameTime,
                    0,
                    -completedLayers,
                    AMPLIFIED_BEAM_COLOR,
                    0.105F + amplifierStrength * 0.025F,
                    0.16F + amplifierStrength * 0.04F
            );
        }

        BeaconRenderer.renderBeaconBeam(
                poseStack,
                bufferSource,
                BeaconRenderer.BEAM_LOCATION,
                partialTick,
                paused ? 0.45F : 0.8F,
                gameTime,
                0,
                -completedLayers,
                paused ? PAUSED_BEAM_COLOR : ACTIVE_BEAM_COLOR,
                paused ? 0.045F : 0.065F,
                paused ? 0.08F : 0.105F
        );
    }

    @Override
    public boolean shouldRenderOffScreen(ArcaneMineCoreBlockEntity core) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 192;
    }

    @Override
    public AABB getRenderBoundingBox(ArcaneMineCoreBlockEntity core) {
        if (StructurePreviewRenderer.isMinePreviewActive(core.getBlockPos())) {
            int radius = MatrixConfig.mineLayerSizes().stream()
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(9) / 2;
            int height = MatrixConfig.mineLayerSizes().size() + 1;
            return new AABB(core.getBlockPos()).inflate(radius, 0.0D, radius)
                    .expandTowards(0.0D, height, 0.0D);
        }
        int depth = Math.max(1, core.getCompletedLayers()) + 1;
        return new AABB(core.getBlockPos()).expandTowards(0.0D, -depth, 0.0D);
    }
}
