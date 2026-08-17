package dev.arsmatrix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.arsmatrix.ArsArcaneMatrix;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import dev.arsmatrix.block.ArcaneProcessorCoreBlock;
import dev.arsmatrix.blockentity.ArcaneProcessorCoreBlockEntity;
import dev.arsmatrix.block.ArcaneSmelterCoreBlock;
import dev.arsmatrix.blockentity.ArcaneSmelterCoreBlockEntity;
import dev.arsmatrix.block.ArcaneCrusherCoreBlock;
import dev.arsmatrix.blockentity.ArcaneCrusherCoreBlockEntity;
import dev.arsmatrix.blockentity.DrygmyArenaBlockEntity;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

/** Client-local translucent block previews toggled by empty-hand use on a controller. */
public final class StructurePreviewRenderer {

    private static final BlockState SOURCE_GEM_BLOCK = blockState("source_gem_block");
    private static final BlockState SOURCESTONE = blockState("sourcestone");
    private static final BlockState ARCANE_PEDESTAL = blockState("arcane_pedestal");
    private static final BlockState MOB_JAR = blockState("mob_jar");
    private static final TagKey<Block> MATRIX_FRAME_BLOCKS = blockTag("matrix_frame_blocks");
    private static final TagKey<Block> MINE_FRAME_BLOCKS = blockTag("arcane_mine_frame_blocks");
    private static final TagKey<Block> MINE_BASIC_FRAME_BLOCKS = blockTag("arcane_mine_basic_frame_blocks");
    private static final TagKey<Block> MINE_NODE_BLOCKS = blockTag("arcane_mine_node_blocks");
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
        } else if (block == ModBlocks.ARCANE_PROCESSOR_CORE.get()) {
            type = PreviewType.PROCESSOR;
        } else if (block == ModBlocks.ARCANE_SMELTER_CORE.get()) {
            type = PreviewType.SMELTER;
        } else if (block == ModBlocks.ARCANE_CRUSHER_CORE.get()) {
            type = PreviewType.CRUSHER;
        } else if (block == ModBlocks.DRYGMY_ARENA.get()) {
            type = PreviewType.HUNTING_GROUNDS;
        } else {
            return;
        }

        if (isStructureComplete(event.getLevel(), event.getPos(), type)) {
            if (event.getPos().equals(previewPos) && type == previewType) {
                previewPos = null;
                previewType = null;
            }
            displayMessage("message.ars_arcane_matrix.structure_preview.complete");
            return;
        }

        boolean closing = event.getPos().equals(previewPos) && type == previewType;
        previewPos = closing ? null : event.getPos().immutable();
        previewType = closing ? null : type;

        displayMessage(closing
                ? "message.ars_arcane_matrix.structure_preview.hidden"
                : "message.ars_arcane_matrix.structure_preview.shown");
    }

    public static boolean isMatrixPreviewActive(BlockPos pos) {
        return previewType == PreviewType.MATRIX && pos.equals(previewPos);
    }

    public static boolean isMinePreviewActive(BlockPos pos) {
        return previewType == PreviewType.MINE && pos.equals(previewPos);
    }

    public static boolean isProcessorPreviewActive(BlockPos pos) {
        return previewType == PreviewType.PROCESSOR && pos.equals(previewPos);
    }

    public static boolean isSmelterPreviewActive(BlockPos pos) {
        return previewType == PreviewType.SMELTER && pos.equals(previewPos);
    }

    public static void renderSmelter(BlockPos pos, PoseStack poseStack, MultiBufferSource bufferSource) {
        if (!isSmelterPreviewActive(pos)) return;
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        net.minecraft.core.Direction facing = level.getBlockState(pos).getValue(ArcaneSmelterCoreBlock.FACING);
        MultiBufferSource previewBuffers = translucentBuffers(bufferSource);
        TagKey<Block> frameTag = blockTag("arcane_smelter_frame_blocks");
        boolean missing = false;
        for (BlockPos frame : ArcaneSmelterCoreBlockEntity.framePositions(pos, facing)) {
            if (!level.getBlockState(frame).is(frameTag)) {
                renderBlock(poseStack, previewBuffers, ModBlocks.ARCANE_STRUCTURAL_FRAME.get().defaultBlockState(),
                        frame.getX() - pos.getX(), frame.getY() - pos.getY(), frame.getZ() - pos.getZ());
                missing = true;
            }
        }
        BlockPos pedestal = pos.above(2);
        if (!level.getBlockState(pedestal).is(ARCANE_PEDESTAL.getBlock())) {
            renderBlock(poseStack, previewBuffers, ARCANE_PEDESTAL, 0, 2, 0);
            missing = true;
        }
        if (!missing && level.getBlockState(pos.above()).isAir()) {
            closeCompletedPreview(pos, PreviewType.SMELTER);
        }
    }

    public static boolean isCrusherPreviewActive(BlockPos pos) {
        return previewType == PreviewType.CRUSHER && pos.equals(previewPos);
    }

    public static void renderHuntingGrounds(BlockPos pos, PoseStack poseStack, MultiBufferSource bufferSource) {
        if (previewType != PreviewType.HUNTING_GROUNDS || !pos.equals(previewPos)) return;
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        if (DrygmyArenaBlockEntity.isStructureFormed(level, pos)) {
            closeCompletedPreview(pos, PreviewType.HUNTING_GROUNDS);
            return;
        }
        MultiBufferSource previewBuffers = translucentBuffers(bufferSource);
        if (!level.getBlockState(pos.above()).is(MOB_JAR.getBlock())) {
            renderBlock(poseStack, previewBuffers, MOB_JAR, 0, 1, 0);
        }
        // These are recommended positions only. Runtime detection accepts any two
        // Arcane Pedestals in the nearby 5x4x5 volume.
        if (!level.getBlockState(pos.east(2)).is(ARCANE_PEDESTAL.getBlock()))
            renderBlock(poseStack, previewBuffers, ARCANE_PEDESTAL, 2, 0, 0);
        if (!level.getBlockState(pos.west(2)).is(ARCANE_PEDESTAL.getBlock()))
            renderBlock(poseStack, previewBuffers, ARCANE_PEDESTAL, -2, 0, 0);
    }

    public static void renderCrusher(BlockPos pos, PoseStack poseStack, MultiBufferSource bufferSource) {
        if (!isCrusherPreviewActive(pos)) return;
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        net.minecraft.core.Direction facing = level.getBlockState(pos).getValue(ArcaneCrusherCoreBlock.FACING);
        MultiBufferSource previewBuffers = translucentBuffers(bufferSource);
        TagKey<Block> frameTag = blockTag("arcane_crusher_frame_blocks");
        boolean missing = false;
        for (BlockPos frame : ArcaneCrusherCoreBlockEntity.framePositions(pos, facing)) {
            if (!level.getBlockState(frame).is(frameTag)) {
                renderBlock(poseStack, previewBuffers, ModBlocks.ARCANE_STRUCTURAL_FRAME.get().defaultBlockState(),
                        frame.getX() - pos.getX(), frame.getY() - pos.getY(), frame.getZ() - pos.getZ());
                missing = true;
            }
        }
        BlockPos pedestal = pos.above(2);
        if (!level.getBlockState(pedestal).is(ARCANE_PEDESTAL.getBlock())) {
            renderBlock(poseStack, previewBuffers, ARCANE_PEDESTAL, 0, 2, 0);
            missing = true;
        }
        if (!missing && level.getBlockState(pos.above()).isAir()) {
            closeCompletedPreview(pos, PreviewType.CRUSHER);
        }
    }

    public static void renderProcessor(BlockPos pos, PoseStack poseStack, MultiBufferSource bufferSource) {
        if (!isProcessorPreviewActive(pos)) return;
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        net.minecraft.core.Direction facing = level.getBlockState(pos).getValue(ArcaneProcessorCoreBlock.FACING);
        MultiBufferSource previewBuffers = translucentBuffers(bufferSource);
        boolean missing = false;
        for (BlockPos frame : ArcaneProcessorCoreBlockEntity.framePositions(pos, facing)) {
            if (!level.getBlockState(frame).is(blockTag("arcane_processor_frame_blocks"))) {
                renderBlock(poseStack, previewBuffers, ModBlocks.ARCANE_STRUCTURAL_FRAME.get().defaultBlockState(),
                        frame.getX() - pos.getX(), frame.getY() - pos.getY(), frame.getZ() - pos.getZ());
                missing = true;
            }
        }
        for (BlockPos pedestal : ArcaneProcessorCoreBlockEntity.pedestalPositions(pos, facing)) {
            if (!level.getBlockState(pedestal).is(ARCANE_PEDESTAL.getBlock())) {
                renderBlock(poseStack, previewBuffers, ARCANE_PEDESTAL,
                        pedestal.getX() - pos.getX(), pedestal.getY() - pos.getY(), pedestal.getZ() - pos.getZ());
                missing = true;
            }
        }
        if (!missing && level.getBlockState(pos.above(2)).isAir()
                && level.getBlockState(pos.relative(facing)).isAir()
                && level.getBlockState(pos.relative(facing.getOpposite())).isAir()) {
            closeCompletedPreview(pos, PreviewType.PROCESSOR);
        }
    }

    public static void renderMatrix(
            BlockPos pos,
            PoseStack poseStack,
            MultiBufferSource bufferSource
    ) {
        if (!isMatrixPreviewActive(pos)) {
            return;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        MultiBufferSource previewBuffers = translucentBuffers(bufferSource);
        boolean missing = false;
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (isMatrixFramePosition(x, y, z)
                            && !isValidMatrixBlock(level, pos, x, y, z)) {
                        renderBlock(poseStack, previewBuffers, ModBlocks.ARCANE_STRUCTURAL_FRAME.get().defaultBlockState(), x, y, z);
                        missing = true;
                    }
                }
            }
        }
        if (!missing) {
            closeCompletedPreview(pos, PreviewType.MATRIX);
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
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        MultiBufferSource previewBuffers = translucentBuffers(bufferSource);
        List<Integer> sizes = MatrixConfig.mineLayerSizes();
        boolean missing = false;
        for (int layer = 0; layer < sizes.size(); layer++) {
            int radius = sizes.get(layer) / 2;
            int y = layer + 1;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean node = x == 0 && z == 0
                            || Math.abs(x) == radius && Math.abs(z) == radius;
                    boolean blacklistAnchor = !node && (Math.abs(x) == radius && z == 0
                            || x == 0 && Math.abs(z) == radius);
                    boolean basicFrame = layer == 0 || blacklistAnchor;
                    if (!isValidMineBlock(level, pos, x, y, z, node, basicFrame)) {
                        renderBlock(
                                poseStack,
                                previewBuffers,
                                node ? SOURCE_GEM_BLOCK : basicFrame ? SOURCESTONE
                                        : ModBlocks.ARCANE_STRUCTURAL_FRAME.get().defaultBlockState(),
                                x,
                                y,
                                z
                        );
                        missing = true;
                    }
                }
            }
        }
        if (!missing) {
            closeCompletedPreview(pos, PreviewType.MINE);
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

    private static boolean isStructureComplete(Level level, BlockPos pos, PreviewType type) {
        if (type == PreviewType.HUNTING_GROUNDS) {
            return DrygmyArenaBlockEntity.isStructureFormed(level, pos);
        }
        if (type == PreviewType.SMELTER) {
            net.minecraft.core.Direction facing = level.getBlockState(pos).getValue(ArcaneSmelterCoreBlock.FACING);
            return ArcaneSmelterCoreBlockEntity.isStructureFormed(level, pos, facing);
        }
        if (type == PreviewType.CRUSHER) {
            net.minecraft.core.Direction facing = level.getBlockState(pos).getValue(ArcaneCrusherCoreBlock.FACING);
            return ArcaneCrusherCoreBlockEntity.isStructureFormed(level, pos, facing);
        }
        if (type == PreviewType.PROCESSOR) {
            net.minecraft.core.Direction facing = level.getBlockState(pos).getValue(ArcaneProcessorCoreBlock.FACING);
            return ArcaneProcessorCoreBlockEntity.isStructureFormed(level, pos, facing);
        }
        if (type == PreviewType.MATRIX) {
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -2; z <= 2; z++) {
                        if (isMatrixFramePosition(x, y, z)
                                && !isValidMatrixBlock(level, pos, x, y, z)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        List<Integer> sizes = MatrixConfig.mineLayerSizes();
        for (int layer = 0; layer < sizes.size(); layer++) {
            int radius = sizes.get(layer) / 2;
            int y = layer + 1;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean node = x == 0 && z == 0
                            || Math.abs(x) == radius && Math.abs(z) == radius;
                    boolean blacklistAnchor = !node && (Math.abs(x) == radius && z == 0
                            || x == 0 && Math.abs(z) == radius);
                    if (!isValidMineBlock(level, pos, x, y, z, node,
                            layer == 0 || blacklistAnchor)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean isValidMatrixBlock(
            Level level,
            BlockPos pos,
            int x,
            int y,
            int z
    ) {
        BlockState state = level.getBlockState(pos.offset(x, y, z));
        return state.is(MATRIX_FRAME_BLOCKS)
                || isMatrixAmplifierPosition(x, y, z)
                && state.is(ModBlocks.ARCANE_AMPLIFIER.get());
    }

    private static boolean isValidMineBlock(
            Level level,
            BlockPos pos,
            int x,
            int y,
            int z,
            boolean node,
            boolean basicFrame
    ) {
        BlockState state = level.getBlockState(pos.offset(x, y, z));
        if (!node) {
            return state.is(basicFrame ? MINE_BASIC_FRAME_BLOCKS : MINE_FRAME_BLOCKS);
        }
        return state.is(MINE_NODE_BLOCKS)
                || x == 0 && z == 0 && state.is(ModBlocks.ARCANE_AMPLIFIER.get());
    }

    private static boolean isMatrixAmplifierPosition(int x, int y, int z) {
        return Math.abs(x) == 2 && y == 0 && z == 0
                || x == 0 && Math.abs(y) == 2 && z == 0
                || x == 0 && y == 0 && Math.abs(z) == 2;
    }

    private static void closeCompletedPreview(BlockPos pos, PreviewType type) {
        if (!pos.equals(previewPos) || type != previewType) {
            return;
        }
        previewPos = null;
        previewType = null;
        displayMessage("message.ars_arcane_matrix.structure_preview.complete");
    }

    private static void displayMessage(String translationKey) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable(translationKey),
                    true
            );
        }
    }

    private static BlockState blockState(String path) {
        return BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("ars_nouveau", path)
        ).defaultBlockState();
    }

    private static TagKey<Block> blockTag(String path) {
        return BlockTags.create(ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, path));
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
        MINE,
        PROCESSOR,
        SMELTER,
        CRUSHER,
        HUNTING_GROUNDS
    }
}
