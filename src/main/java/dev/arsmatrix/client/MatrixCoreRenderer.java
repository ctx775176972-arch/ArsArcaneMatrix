package dev.arsmatrix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.blockentity.MatrixCoreBlockEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/** Renders the matrix core as a hovering arcane astrolabe instead of a solid cube. */
public final class MatrixCoreRenderer implements BlockEntityRenderer<MatrixCoreBlockEntity> {

    private static final float MODEL_CUBE_SIZE = 0.25F;

    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "matrix_core"),
            "cube"
    );

    private static final Material RUNE_MATERIAL = new Material(
            TextureAtlas.LOCATION_BLOCKS,
            ResourceLocation.withDefaultNamespace("block/purple_stained_glass")
    );
    private static final Material SOURCE_MATERIAL = new Material(
            TextureAtlas.LOCATION_BLOCKS,
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "block/source_gem_block")
    );
    private final ModelPart cube;

    public MatrixCoreRenderer(BlockEntityRendererProvider.Context context) {
        this.cube = context.bakeLayer(MODEL_LAYER).getChild("cube");
    }

    public static LayerDefinition createCubeLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild(
                "cube",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F),
                PartPose.ZERO
        );
        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void render(
            MatrixCoreBlockEntity core,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        boolean active = core.isActive();
        long gameTime = core.getLevel() == null ? 0L : core.getLevel().getGameTime();
        float positionOffset = Math.floorMod(core.getBlockPos().hashCode(), 360);
        float time = gameTime + partialTick + positionOffset;
        float angle = time * (active ? 2.4F : 0.38F);
        float bob = Mth.sin(time * (active ? 0.12F : 0.05F)) * (active ? 0.035F : 0.012F);
        float pulse = active ? Mth.sin(time * 0.10F) * 0.018F : 0.0F;
        float radius = (active ? 0.37F : 0.29F) + pulse;
        int energyLight = active ? LightTexture.FULL_BRIGHT : packedLight;

        VertexConsumer runeBuffer = RUNE_MATERIAL.buffer(bufferSource, RenderType::entityCutout);
        VertexConsumer sourceBuffer = SOURCE_MATERIAL.buffer(bufferSource, RenderType::entitySolid);
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F + bob, 0.5F);

        renderCenter(poseStack, sourceBuffer, energyLight, angle, active);
        renderRings(poseStack, runeBuffer, LightTexture.FULL_BRIGHT, radius, angle, active);

        poseStack.popPose();
        StructurePreviewRenderer.renderMatrix(core.getBlockPos(), poseStack, bufferSource);
    }

    @Override
    public boolean shouldRenderOffScreen(MatrixCoreBlockEntity core) {
        return StructurePreviewRenderer.isMatrixPreviewActive(core.getBlockPos());
    }

    @Override
    public AABB getRenderBoundingBox(MatrixCoreBlockEntity core) {
        return new AABB(core.getBlockPos()).inflate(2.0D);
    }

    private void renderCenter(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            float angle,
            boolean active
    ) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(angle * 1.3F));
        poseStack.mulPose(Axis.XP.rotationDegrees(22.5F + angle * 0.42F));
        float scale = active ? 1.12F : 0.88F;
        poseStack.scale(scale, scale, scale);
        cube.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private void renderRings(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            float radius,
            float angle,
            boolean active
    ) {
        float thickness = active ? 0.058F : 0.07F;

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        poseStack.mulPose(Axis.ZP.rotationDegrees(active ? 18.0F : 8.0F));
        renderSquareRing(poseStack, buffer, packedLight, radius, thickness);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(24.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-angle * 0.82F));
        renderSquareRing(poseStack, buffer, packedLight, radius * 0.92F, thickness * 0.9F);
        poseStack.popPose();

        if (active) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(-20.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(angle * 0.64F));
            renderSquareRing(poseStack, buffer, packedLight, radius * 0.84F, thickness * 0.72F);
            poseStack.popPose();
        }
    }

    private void renderSquareRing(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            float radius,
            float thickness
    ) {
        float horizontalLengthScale = (radius * 2.0F + thickness) / MODEL_CUBE_SIZE;
        float verticalLengthScale = (radius * 2.0F - thickness) / MODEL_CUBE_SIZE;
        float thicknessScale = thickness / MODEL_CUBE_SIZE;
        float depthScale = thicknessScale * 0.72F;

        renderCuboid(poseStack, buffer, packedLight, 0.0F, radius, 0.0F,
                horizontalLengthScale, thicknessScale, depthScale);
        renderCuboid(poseStack, buffer, packedLight, 0.0F, -radius, 0.0F,
                horizontalLengthScale, thicknessScale, depthScale);
        renderCuboid(poseStack, buffer, packedLight, radius, 0.0F, 0.0F,
                thicknessScale, verticalLengthScale, depthScale);
        renderCuboid(poseStack, buffer, packedLight, -radius, 0.0F, 0.0F,
                thicknessScale, verticalLengthScale, depthScale);
    }

    private void renderCuboid(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            float x,
            float y,
            float z,
            float scaleX,
            float scaleY,
            float scaleZ
    ) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.scale(scaleX, scaleY, scaleZ);
        cube.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }
}
