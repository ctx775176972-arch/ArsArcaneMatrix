package com.tianxin.arsmatrix.compat.jade;

import com.tianxin.arsmatrix.ArsArcaneMatrix;
import com.tianxin.arsmatrix.block.ArcaneMineCoreBlock;
import com.tianxin.arsmatrix.block.MatrixCoreBlock;
import com.tianxin.arsmatrix.blockentity.ArcaneMineCoreBlockEntity;
import com.tianxin.arsmatrix.blockentity.MatrixCoreBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin(ArsArcaneMatrix.MOD_ID)
public final class ArsMatrixJadePlugin implements IWailaPlugin {

    private static final CoreComponentProvider CORE_COMPONENTS = new CoreComponentProvider();

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CORE_COMPONENTS, MatrixCoreBlock.class);
        registration.registerBlockComponent(CORE_COMPONENTS, ArcaneMineCoreBlock.class);
    }

    private static final class CoreComponentProvider implements IBlockComponentProvider {

        private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
                ArsArcaneMatrix.MOD_ID,
                "core_status"
        );

        @Override
        public void appendTooltip(
                ITooltip tooltip,
                BlockAccessor accessor,
                IPluginConfig config
        ) {
            if (accessor.getBlockEntity() instanceof MatrixCoreBlockEntity core) {
                tooltip.add(Component.translatable(
                        "jade.ars_arcane_matrix.matrix.status",
                        Component.translatable(core.isActive()
                                ? "message.ars_arcane_matrix.state.active"
                                : "message.ars_arcane_matrix.state.unformed")
                ));
                tooltip.add(Component.translatable(
                        "jade.ars_arcane_matrix.matrix.source",
                        core.getSource(),
                        core.getMaxSource()
                ));
                tooltip.add(Component.translatable(
                        "jade.ars_arcane_matrix.matrix.frames",
                        core.getFrameBlockCount(),
                        core.getMaximumFrameBlocks(),
                        core.getSourceGenerationPerSecond(),
                        core.getAmplifierCount()
                ));
            } else if (accessor.getBlockEntity() instanceof ArcaneMineCoreBlockEntity mine) {
                tooltip.add(Component.translatable(
                        "jade.ars_arcane_matrix.mine.status",
                        Component.translatable(mine.isActive()
                                ? "message.ars_arcane_matrix.state.active"
                                : "message.ars_arcane_matrix.state.inactive")
                ));
                tooltip.add(Component.translatable(
                        "jade.ars_arcane_matrix.mine.source",
                        mine.getSource(),
                        mine.getMaxSource()
                ));
                tooltip.add(Component.translatable(
                        "jade.ars_arcane_matrix.mine.operation",
                        mine.getCompletedLayers(),
                        mine.getMaterialPoints(),
                        mine.getCooldownTicks(),
                        mine.getAmplifierCount()
                ));
                tooltip.add(Component.translatable(
                        "jade.ars_arcane_matrix.mine.links",
                        mine.getMaterialContainerCount(),
                        Component.translatable(mine.hasOutputContainer()
                                ? "message.ars_arcane_matrix.state.bound"
                                : "message.ars_arcane_matrix.state.unbound")
                ));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
