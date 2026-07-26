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
                String statusKey = switch (mine.getOperatingState()) {
                    case UNFORMED -> "message.ars_arcane_matrix.state.unformed";
                    case REDSTONE_PAUSED -> "message.ars_arcane_matrix.state.redstone_paused";
                    case MATERIAL_STARVED -> "message.ars_arcane_matrix.state.material_starved";
                    case SOURCE_STARVED -> "message.ars_arcane_matrix.state.source_starved";
                    case COOLDOWN -> "message.ars_arcane_matrix.state.cooldown";
                    case OUTPUT_BLOCKED -> "message.ars_arcane_matrix.state.output_blocked";
                    case NO_TARGET -> "message.ars_arcane_matrix.state.no_target";
                    case ACTIVE -> "message.ars_arcane_matrix.state.active";
                };
                tooltip.add(Component.translatable(
                        "jade.ars_arcane_matrix.mine.status",
                        Component.translatable(statusKey)
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
                if (mine.getTargetMaterialCost() > 0 || mine.getTargetSourceCost() > 0) {
                    tooltip.add(Component.translatable(
                            "jade.ars_arcane_matrix.mine.requirement",
                            mine.getTargetMaterialCost(),
                            mine.getTargetSourceCost()
                    ));
                }
                if (mine.getPendingByproductCount() > 0) {
                    tooltip.add(Component.translatable(
                            "jade.ars_arcane_matrix.mine.byproduct_buffer",
                            mine.getPendingByproductCount()
                    ));
                }
                tooltip.add(Component.translatable(
                        "jade.ars_arcane_matrix.mine.links",
                        mine.getMaterialContainerCount(),
                        Component.translatable(mine.hasOutputContainer()
                                ? "message.ars_arcane_matrix.state.bound"
                                : "message.ars_arcane_matrix.state.unbound")
                ));
                tooltip.add(Component.translatable(
                        "jade.ars_arcane_matrix.mine.tuning",
                        mine.getWhitelistCount(),
                        mine.getBlacklistCount()
                ));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
