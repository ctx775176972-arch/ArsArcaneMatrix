package dev.arsmatrix.client;

import com.hollingsworth.arsnouveau.common.block.tile.WixieCauldronTile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.arsmatrix.blockentity.WixieOrderTerminalBlockEntity;
import dev.arsmatrix.blockentity.WixiePatternProviderBlockEntity;
import dev.arsmatrix.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;

/** Client-local range diagnostics for the Wixie crafting network. */
public final class WixieRangeRenderer {
    private static BlockPos activePos;
    private static ResourceKey<Level> activeDimension;
    private static RangeType activeType;
    private static long lastScanTime = Long.MIN_VALUE;
    private static List<BlockPos> detected = List.of();

    private WixieRangeRenderer() {}

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND
                || !event.getItemStack().isEmpty() || !event.getEntity().isShiftKeyDown()) return;
        RangeType type = typeOf(event.getLevel().getBlockState(event.getPos()).getBlock());
        if (type == null) return;
        boolean closing = event.getPos().equals(activePos)
                && event.getLevel().dimension().equals(activeDimension) && type == activeType;
        if (closing) {
            clear();
            display("message.ars_arcane_matrix.wixie_range.hidden");
            return;
        }
        activePos = event.getPos().immutable();
        activeDimension = event.getLevel().dimension();
        activeType = type;
        lastScanTime = Long.MIN_VALUE;
        refreshDetected(event.getLevel());
        int horizontal = horizontalRadius();
        int vertical = verticalRadius();
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.wixie_range.shown", horizontal, vertical), true);
        }
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || activePos == null) return;
        Level level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(activeDimension)
                || typeOf(level.getBlockState(activePos).getBlock()) != activeType) {
            clear();
            return;
        }
        if (level.getGameTime() - lastScanTime >= 20) refreshDetected(level);
        int horizontal = horizontalRadius();
        int vertical = verticalRadius();
        float[] color = activeType == RangeType.PROVIDER
                ? new float[]{0.25F, 0.65F, 1.0F}
                : activeType == RangeType.PEDESTAL
                    ? new float[]{1.0F, 0.68F, 0.15F}
                    : new float[]{0.72F, 0.30F, 1.0F};

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer lines = Minecraft.getInstance().renderBuffers().bufferSource()
                .getBuffer(RenderType.lines());
        AABB range = new AABB(
                activePos.getX() - horizontal, activePos.getY() - vertical, activePos.getZ() - horizontal,
                activePos.getX() + horizontal + 1, activePos.getY() + vertical + 1,
                activePos.getZ() + horizontal + 1);
        LevelRenderer.renderLineBox(poseStack, lines, range, color[0], color[1], color[2], 0.9F);
        LevelRenderer.renderLineBox(poseStack, lines, new AABB(activePos), 1.0F, 1.0F, 1.0F, 1.0F);
        for (BlockPos pos : detected) {
            LevelRenderer.renderLineBox(poseStack, lines, new AABB(pos).inflate(0.03D),
                    0.25F, 1.0F, 0.35F, 1.0F);
        }
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch(RenderType.lines());
        poseStack.popPose();
    }

    private static void refreshDetected(Level level) {
        if (activePos == null || activeType == null) return;
        int horizontal = horizontalRadius();
        int vertical = verticalRadius();
        List<BlockPos> found = new ArrayList<>();
        BlockPos.betweenClosedStream(activePos.offset(-horizontal, -vertical, -horizontal),
                        activePos.offset(horizontal, vertical, horizontal))
                .filter(level::hasChunkAt)
                .forEach(pos -> {
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    boolean matches = switch (activeType) {
                        case PROVIDER -> blockEntity instanceof WixieCauldronTile;
                        case TERMINAL, LECTERN -> level.getBlockState(pos).is(ModBlocks.WIXIE_PATTERN_PROVIDER.get())
                                || level.getBlockState(pos).is(ModBlocks.ARCANE_ORDER_PEDESTAL.get());
                        case PEDESTAL -> level.getBlockState(pos).is(ModBlocks.WIXIE_ORDER_TERMINAL.get())
                                || level.getBlockState(pos).is(ModBlocks.ADVANCED_STORAGE_LECTERN.get());
                    };
                    if (matches) found.add(pos.immutable());
                });
        detected = List.copyOf(found);
        lastScanTime = level.getGameTime();
    }

    private static int horizontalRadius() {
        return activeType == RangeType.PROVIDER
                ? WixiePatternProviderBlockEntity.WORKSTATION_RADIUS
                : WixieOrderTerminalBlockEntity.NETWORK_RADIUS;
    }

    private static int verticalRadius() {
        return activeType == RangeType.PROVIDER
                ? WixiePatternProviderBlockEntity.WORKSTATION_VERTICAL_RADIUS
                : WixieOrderTerminalBlockEntity.NETWORK_RADIUS;
    }

    private static RangeType typeOf(Block block) {
        if (block == ModBlocks.WIXIE_PATTERN_PROVIDER.get()) return RangeType.PROVIDER;
        if (block == ModBlocks.WIXIE_ORDER_TERMINAL.get()) return RangeType.TERMINAL;
        if (block == ModBlocks.ARCANE_ORDER_PEDESTAL.get()) return RangeType.PEDESTAL;
        if (block == ModBlocks.ADVANCED_STORAGE_LECTERN.get()) return RangeType.LECTERN;
        return null;
    }

    private static void display(String key) {
        if (Minecraft.getInstance().player != null)
            Minecraft.getInstance().player.displayClientMessage(Component.translatable(key), true);
    }

    private static void clear() {
        activePos = null;
        activeDimension = null;
        activeType = null;
        detected = List.of();
        lastScanTime = Long.MIN_VALUE;
    }

    private enum RangeType { PROVIDER, TERMINAL, PEDESTAL, LECTERN }
}
