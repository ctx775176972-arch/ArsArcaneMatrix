package dev.arsmatrix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.arsmatrix.config.MatrixConfig;
import dev.arsmatrix.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

/** Client-local translucent block previews toggled by empty-hand use on a controller. */
public final class StructurePreviewRenderer {

    private static final BlockState SOURCE_GEM_BLOCK = blockState("source_gem_block");
    private static final BlockState SOURCESTONE = blockState("sourcestone");
    private static final float PREVIEW_ALPHA = 0.42F;
    private static final float PREVIEW_SCALE = 0.98F;
    private static final float PREVIEW_INSET = (1.0F - PREVIEW_SCALE) / 2.0F;

    private static BlockPos previewPos;
    private static PreviewType previewType;

    private StructurePreviewRenderer() {
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide
                || event.getHand() != InteractionHand.MAIN_HAND
                || !event.getItemStack().isEmpty()) {
            return;
        }

        Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
        PreviewType type;
        if (block == ModBlocks.MATRIX_CORE.get()) {
            type = PreviewType.MATRIX;
        } else if (block == ModBlocks.ARCANE_MINE_CORE.get()) {
            type = PreviewType.MINE;
        } else {
            return;
        }

        boolean closing = event.getPos().equals(previewPos) && type == previewType;
        previewPos = closing ? null : event.getPos().immutable();
        previewType = closing ? null : type;

        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.translatable(
                    closing
                            ? "message.ars_arcane_matrix.structure_preview.hidden"
                            : "message.ars_arcane_matrix.structure_preview.shown"
            ), true);
        }
    }

    public static boolean isMatrixPreviewActive(BlockPos pos) {
        return previewType == PreviewType.MATRIX && pos.equals(previewPos);
    }

    public static boolean isMinePreviewActive(BlockPos pos) {
        return previewType == PreviewType.MINE && pos.equals(previewPos);
    }

    public static void renderMatrix(
            BlockPos pos,
            PoseStack poseStack,
            MultiBufferSource bufferSource
    ) {
        if (!isMatrixPreviewActive(pos)) {
            return;
        }
        MultiBufferSource previewBuffers = translucentBuffers(bufferSource);
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (isMatrixFramePosition(x, y, z)) {
                        renderBlock(poseStack, previewBuffers, SOURCE_GEM_BLOCK, x, y, z);
                    }
                }
            }
        }
    }

    public static void renderMine(
            BlockPos pos,
            PoseStack poseStack,
            MultiBufferSource bufferSource
    ) {
        if (!isMinePreviewActive(pos)) {
            return;
        }
        MultiBufferSource previewBuffers = translucentBuffers(bufferSource);
        List<Integer> sizes = MatrixConfig.mineLayerSizes();
        for (int layer = 0; layer < sizes.size(); layer++) {
            int radius = sizes.get(layer) / 2;
            int y = layer + 1;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean node = x == 0 && z == 0
                            || Math.abs(x) == radius && Math.abs(z) == radius;
                    renderBlock(
                            poseStack,
                            previewBuffers,
                            node ? SOURCE_GEM_BLOCK : SOURCESTONE,
                            x,
                            y,
                            z
                    );
                }
            }
        }
    }

    private static MultiBufferSource translucentBuffers(MultiBufferSource buffers) {
        VertexConsumer translucent = new AlphaVertexConsumer(
                buffers.getBuffer(RenderType.translucent()),
                PREVIEW_ALPHA
        );
        return ignored -> translucent;
    }

    private static void renderBlock(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            BlockState state,
            int x,
            int y,
            int z
    ) {
        poseStack.pushPose();
        poseStack.translate(x + PREVIEW_INSET, y + PREVIEW_INSET, z + PREVIEW_INSET);
        poseStack.scale(PREVIEW_SCALE, PREVIEW_SCALE, PREVIEW_SCALE);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                state,
                poseStack,
                bufferSource,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY
        );
        poseStack.popPose();
    }

    private static boolean isMatrixFramePosition(int x, int y, int z) {
        return x == 0 && (Math.abs(y) == 2 || Math.abs(z) == 2)
                || y == 0 && (Math.abs(x) == 2 || Math.abs(z) == 2)
                || z == 0 && (Math.abs(x) == 2 || Math.abs(y) == 2);
    }

    private static BlockState blockState(String path) {
        return BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("ars_nouveau", path)
        ).defaultBlockState();
    }

    private record AlphaVertexConsumer(VertexConsumer delegate, float alpha)
            implements VertexConsumer {

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int originalAlpha) {
            delegate.setColor(red, green, blue, Math.round(alpha * 255.0F));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }

    private enum PreviewType {
        MATRIX,
        MINE
    }
}
