// 文件路径：dev/arsmatrix/compat/jade/ArsMatrixJadePlugin.java
package dev.arsmatrix.compat.jade;


import com.hollingsworth.arsnouveau.common.block.WhirlisprigFlower;
import com.hollingsworth.arsnouveau.common.block.tile.WhirlisprigTile;
import dev.arsmatrix.block.AdvancedStorageLecternBlock;
import dev.arsmatrix.block.ArcaneCrusherCoreBlock;
import dev.arsmatrix.block.ArcaneFluidReservoirBlock;
import dev.arsmatrix.block.ArcaneFluidTankBlock;
import dev.arsmatrix.block.ArcaneReactionVesselBlock;
import dev.arsmatrix.block.ArcaneImbuementCoreBlock;
import dev.arsmatrix.block.ArcaneMineCoreBlock;
import dev.arsmatrix.block.ArcaneOrderPedestalBlock;
import dev.arsmatrix.block.ArcaneProcessorCoreBlock;
import dev.arsmatrix.block.ArcaneSmelterCoreBlock;
import dev.arsmatrix.block.ArcaneVacuumHopperBlock;
import dev.arsmatrix.block.ArcaneSourceJarBlock;
import dev.arsmatrix.block.AutomaticStockRequesterBlock;
import dev.arsmatrix.block.DimensionAnchorBlock;
import dev.arsmatrix.block.DrygmyArenaBlock;
import dev.arsmatrix.block.IntegratedSourceRelayBlock;
import dev.arsmatrix.block.MatrixCoreBlock;
import dev.arsmatrix.block.SourceStoneGeneratorBlock;
import dev.arsmatrix.block.SuperSourceJarCoreBlock;
import dev.arsmatrix.block.WixieOrderTerminalBlock;
import dev.arsmatrix.block.WixiePatternProviderBlock;
import dev.arsmatrix.blockentity.AdvancedStorageLecternBlockEntity;
import dev.arsmatrix.blockentity.ArcaneCrusherCoreBlockEntity;
import dev.arsmatrix.blockentity.ArcaneFluidReservoirBlockEntity;
import dev.arsmatrix.blockentity.ArcaneFluidTankBlockEntity;
import dev.arsmatrix.blockentity.ArcaneReactionVesselBlockEntity;
import dev.arsmatrix.blockentity.ArcaneImbuementCoreBlockEntity;
import dev.arsmatrix.blockentity.ArcaneMineCoreBlockEntity;
import dev.arsmatrix.blockentity.ArcaneOrderPedestalBlockEntity;
import dev.arsmatrix.blockentity.ArcaneProcessorCoreBlockEntity;
import dev.arsmatrix.blockentity.ArcaneSmelterCoreBlockEntity;
import dev.arsmatrix.blockentity.ArcaneVacuumHopperBlockEntity;
import dev.arsmatrix.blockentity.ArcaneSourceJarBlockEntity;
import dev.arsmatrix.blockentity.AutomaticStockRequesterBlockEntity;
import dev.arsmatrix.blockentity.DimensionAnchorBlockEntity;
import dev.arsmatrix.blockentity.DrygmyArenaBlockEntity;
import dev.arsmatrix.blockentity.IntegratedSourceRelayBlockEntity;
import dev.arsmatrix.blockentity.MatrixCoreBlockEntity;
import dev.arsmatrix.blockentity.SourceStoneGeneratorBlockEntity;
import dev.arsmatrix.blockentity.SuperSourceJarCoreBlockEntity;
import dev.arsmatrix.blockentity.WixieOrderTerminalBlockEntity;
import dev.arsmatrix.blockentity.WixiePatternProviderBlockEntity;
import dev.arsmatrix.compat.arsnouveau.WhirlisprigEnhancements;
import dev.arsmatrix.config.MatrixConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

import java.util.Locale;

@WailaPlugin("ars_arcane_matrix")
public final class ArsMatrixJadePlugin implements IWailaPlugin {

    private static final CoreComponentProvider CORE_COMPONENTS = new CoreComponentProvider();

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CORE_COMPONENTS, MatrixCoreBlock.class);
        registration.registerBlockComponent(CORE_COMPONENTS, ArcaneMineCoreBlock.class);
        registration.registerBlockComponent(CORE_COMPONENTS, ArcaneImbuementCoreBlock.class);
        registration.registerBlockComponent(CORE_COMPONENTS, SourceStoneGeneratorBlock.class);
        registration.registerBlockComponent(CORE_COMPONENTS, ArcaneProcessorCoreBlock.class);
        registration.registerBlockComponent(CORE_COMPONENTS, ArcaneSmelterCoreBlock.class);
        registration.registerBlockComponent(CORE_COMPONENTS, ArcaneCrusherCoreBlock.class);
        registration.registerBlockComponent(CORE_COMPONENTS, WixiePatternProviderBlock.class);
        registration.registerBlockComponent(CORE_COMPONENTS, WixieOrderTerminalBlock.class);
        registration.registerBlockComponent(CORE_COMPONENTS, ArcaneOrderPedestalBlock.class);
        registration.registerBlockComponent(CORE_COMPONENTS, AdvancedStorageLecternBlock.class);
        registration.registerBlockComponent(CORE_COMPONENTS, AutomaticStockRequesterBlock.class);
        registration.registerBlockComponent(CORE_COMPONENTS, SuperSourceJarCoreBlock.class);
        registration.registerBlockComponent(CORE_COMPONENTS, ArcaneSourceJarBlock.class);
        registration.registerBlockComponent(CORE_COMPONENTS, IntegratedSourceRelayBlock.class);
        registration.registerBlockComponent(CORE_COMPONENTS, DimensionAnchorBlock.class);
        registration.registerBlockComponent(CORE_COMPONENTS, ArcaneFluidReservoirBlock.class);
        registration.registerBlockComponent(CORE_COMPONENTS, ArcaneFluidTankBlock.class);
        registration.registerBlockComponent(CORE_COMPONENTS, ArcaneReactionVesselBlock.class);
        registration.registerBlockComponent(CORE_COMPONENTS, ArcaneVacuumHopperBlock.class);
        registration.registerBlockComponent(CORE_COMPONENTS, WhirlisprigFlower.class);
        registration.registerBlockComponent(CORE_COMPONENTS, DrygmyArenaBlock.class);
    }

    public static final class CoreComponentProvider implements IBlockComponentProvider {

        private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("ars_arcane_matrix", "core_status");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockEntity be = accessor.getBlockEntity();
            if (be instanceof WhirlisprigTile whirlisprig) {
                WhirlisprigEnhancements.Mode whirlMode = WhirlisprigEnhancements.currentMode(whirlisprig);
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.whirlisprig.mode",
                        Component.translatable("message.ars_arcane_matrix.whirlisprig.mode."
                                + whirlMode.name().toLowerCase(Locale.ROOT))));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.whirlisprig.production",
                        Component.translatable("jade.ars_arcane_matrix.whirlisprig.production."
                                + whirlMode.name().toLowerCase(Locale.ROOT))));
                if (whirlMode != WhirlisprigEnhancements.Mode.NONE) {
                    tooltip.add(Component.translatable("jade.ars_arcane_matrix.whirlisprig.diversity",
                            WhirlisprigEnhancements.diversityLevel(whirlisprig),
                            WhirlisprigEnhancements.requiredProgressActions(whirlisprig)));
                }
            } else if (be instanceof AutomaticStockRequesterBlockEntity requester) {
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.stock_requester.status", Component.translatable(requester.getState().translationKey())));
                Component targetName = requester.getTarget().isEmpty()
                        ? Component.translatable("message.ars_arcane_matrix.stock_requester.state.no_target")
                        : requester.getTarget().getHoverName();
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.stock_requester.stock", targetName, requester.getCurrentStock(), requester.getMinimumStock(), requester.getRequestAmount()));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.stock_requester.links",
                        Component.translatable(requester.hasTargetContainer() ? "message.ars_arcane_matrix.state.bound" : "message.ars_arcane_matrix.state.unbound"),
                        Component.translatable(requester.hasOrderTerminal() ? "message.ars_arcane_matrix.state.bound" : "message.ars_arcane_matrix.state.unbound")));
            } else if (be instanceof ArcaneSourceJarBlockEntity jar) {
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.source_network.storage",
                        jar.getSource(), jar.getMaxSource()));
                tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.arcane_source_jar.pull",
                        jar.getLastPulled(), ArcaneSourceJarBlockEntity.PULL_RANGE));
            } else if (be instanceof SuperSourceJarCoreBlockEntity jar) {
                tooltip.add(Component.translatable(jar.isStructureFormed()
                        ? "tooltip.ars_arcane_matrix.matrix_source_reservoir.formed"
                        : "tooltip.ars_arcane_matrix.matrix_source_reservoir.incomplete"));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.source_network.storage", jar.getSource(), jar.getMaxSource()));
                tooltip.add(Component.translatable(jar.isLinked() ? "tooltip.ars_arcane_matrix.source_network.linked" : "tooltip.ars_arcane_matrix.source_network.unlinked"));
                tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.matrix_source_reservoir.pull",
                        jar.getLastPulled(), SuperSourceJarCoreBlockEntity.PULL_RANGE));
            } else if (be instanceof IntegratedSourceRelayBlockEntity relay) {
                tooltip.add(Component.translatable(relay.isLinked() ? "tooltip.ars_arcane_matrix.source_network.linked" : "tooltip.ars_arcane_matrix.source_network.unlinked"));
                tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.integrated_source_relay.on_demand"));
            } else if (be instanceof DimensionAnchorBlockEntity anchor) {
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.dimension_anchor.status", Component.translatable(anchor.getState().translationKey())));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.dimension_anchor.source_cost",
                        anchor.getSourceCostPerSecond()));
                int diameter = anchor.getLoadedRadius() * 2 + 1;
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.dimension_anchor.range",
                        diameter, diameter, anchor.getLoadedChunkCount()));
            } else if (be instanceof ArcaneFluidReservoirBlockEntity reservoir) {
                Object[] arr = new Object[1];
                arr[0] = Component.translatable("screen.ars_arcane_matrix.arcane_fluid_reservoir.mode." + reservoir.mode().name().toLowerCase(Locale.ROOT));
                tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.arcane_fluid_reservoir.mode", arr));
                tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.arcane_fluid_reservoir.tanks", reservoir.unlockedTankCount(), reservoir.capacity()));
                tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.arcane_fluid_reservoir.wireless", Component.translatable("screen.ars_arcane_matrix.arcane_fluid_reservoir.wireless_tier." + reservoir.wirelessTier())));
                tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.arcane_fluid_reservoir.targets",
                        reservoir.inputTargetCount(), reservoir.maxWirelessTargets(),
                        reservoir.outputTargetCount(), reservoir.maxWirelessTargets()));
                tooltip.add(Component.translatable("state.ars_arcane_matrix.arcane_fluid_reservoir." + reservoir.operatingState().name().toLowerCase(Locale.ROOT)));
            } else if (be instanceof ArcaneFluidTankBlockEntity tank) {
                var fluid = tank.fluid();
                tooltip.add(fluid.isEmpty()
                        ? Component.translatable("tooltip.ars_arcane_matrix.arcane_fluid_tank.empty",
                                ArcaneFluidTankBlockEntity.CAPACITY)
                        : Component.translatable("tooltip.ars_arcane_matrix.arcane_fluid_tank.stored",
                                fluid.getHoverName(), fluid.getAmount(), ArcaneFluidTankBlockEntity.CAPACITY));
            } else if (be instanceof ArcaneReactionVesselBlockEntity vessel) {
                tooltip.add(Component.translatable("state.ars_arcane_matrix.arcane_reaction_vessel."
                        + vessel.state().name().toLowerCase(Locale.ROOT)));
                var fluid = vessel.tank().getFluid();
                tooltip.add(Component.translatable("screen.ars_arcane_matrix.arcane_reaction_vessel.fluid",
                        fluid.isEmpty()
                                ? Component.translatable("screen.ars_arcane_matrix.arcane_reaction_vessel.empty")
                                : fluid.getHoverName(),
                        fluid.getAmount(), ArcaneReactionVesselBlockEntity.TANK_CAPACITY));
            } else if (be instanceof ArcaneVacuumHopperBlockEntity hopper) {
                tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.arcane_vacuum_hopper.range",
                        Component.translatable(hopper.rangeMode().translationKey()),
                        hopper.rangeMode().scanIntervalTicks() / 20.0D));
                tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.arcane_vacuum_hopper.experience", hopper.experience(), 10000000));
                Object[] arr = new Object[1];
                arr[0] = Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper.mode." + hopper.gemMode().name().toLowerCase(Locale.ROOT));
                tooltip.add(Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper.gems", arr));
                tooltip.add(Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper.destroy",
                        Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper." + (hopper.destroysMatches() ? "on" : "off"))));
                tooltip.add(Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper.nbt",
                        Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper.nbt."
                                + (hopper.strictComponents() ? "strict" : "ignore"))));
            } else if (be instanceof WixiePatternProviderBlockEntity) {
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.wixie_range.provider", 8, 8));
            } else if (be instanceof AdvancedStorageLecternBlockEntity lectern) {
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.wixie_range.network", 16));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.source_network.gateway",
                        lectern.getLinkedSourceJarCount(), lectern.getLinkedSourceRelayCount(), lectern.getNetworkSource(), lectern.getNetworkCapacity()));
            } else if (be instanceof WixieOrderTerminalBlockEntity) {
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.wixie_range.network", 16));
            } else if (be instanceof ArcaneOrderPedestalBlockEntity) {
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.wixie_range.pedestal", 16));
            } else if (be instanceof MatrixCoreBlockEntity core) {
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.matrix.status",
                        Component.translatable(core.isActive() ? "message.ars_arcane_matrix.state.active" : "message.ars_arcane_matrix.state.unformed")));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.matrix.source", core.getSource(), core.getMaxSource()));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.matrix.frames",
                        core.getFrameBlockCount(), core.getMaximumFrameBlocks(), core.getSourceGenerationPerSecond(), core.getAmplifierCount()));
            } else if (be instanceof ArcaneMineCoreBlockEntity mine) {
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
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.mine.status", Component.translatable(statusKey)));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.mine.operation",
                        mine.getCompletedLayers(), mine.getMaterialPoints(), mine.getCooldownTicks(), mine.getAmplifierCount()));
                if (mine.getCompletedLayers() >= MatrixConfig.mineLayerSizes().size()) {
                    tooltip.add(Component.translatable("jade.ars_arcane_matrix.mine.amplifier_pity",
                            mine.getAmplifierPityMaterialPoints(), mine.getAmplifierPityLimit()));
                }
                if (mine.getTargetMaterialCost() > 0 || mine.getTargetSourceCost() > 0) {
                    tooltip.add(Component.translatable("jade.ars_arcane_matrix.mine.requirement", mine.getTargetMaterialCost(), mine.getTargetSourceCost()));
                }
                if (mine.getPendingByproductCount() > 0) {
                    tooltip.add(Component.translatable("jade.ars_arcane_matrix.mine.byproduct_buffer", mine.getPendingByproductCount()));
                }
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.mine.links",
                        mine.getMaterialContainerCount(),
                        Component.translatable(mine.hasOutputContainer() ? "message.ars_arcane_matrix.state.bound" : "message.ars_arcane_matrix.state.unbound")));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.mine.tuning", mine.getWhitelistCount(), mine.getBlacklistCount()));
            } else if (be instanceof ArcaneImbuementCoreBlockEntity core) {
                String statusKey = switch (core.getOperatingState()) {
                    case UNLINKED -> "message.ars_arcane_matrix.state.unlinked";
                    case REDSTONE_PAUSED -> "message.ars_arcane_matrix.state.redstone_paused";
                    case IDLE -> "message.ars_arcane_matrix.state.idle";
                    case SOURCE_STARVED -> "message.ars_arcane_matrix.state.source_starved";
                    case OUTPUT_BLOCKED -> "message.ars_arcane_matrix.state.output_blocked";
                    case PROCESSING -> "message.ars_arcane_matrix.state.processing";
                };
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.imbuement.status", Component.translatable(statusKey)));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.imbuement.batch_source_cost", core.getDisplayedBatchSourceCost()));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.imbuement.mode", Component.translatable(core.getOutputMode().translationKey())));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.imbuement.operation",
                        core.getInputCount(), core.getOutputCount(), Math.ceilDiv(core.getProgressTicks(), 20)));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.imbuement.links",
                        Component.translatable(core.hasInputContainer() ? "message.ars_arcane_matrix.state.bound" : "message.ars_arcane_matrix.state.unbound"),
                        Component.translatable(core.hasOutputContainer() ? "message.ars_arcane_matrix.state.bound" : "message.ars_arcane_matrix.state.unbound")));
            } else if (be instanceof ArcaneProcessorCoreBlockEntity processor) {
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.arcane_processor.status", Component.translatable(processor.getState().translationKey())));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.arcane_processor.operation",
                        processor.getInputCount(), processor.getProgressSeconds(), processor.getCycleSeconds(), processor.getWorkTimeSeconds()));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.arcane_processor.crystal", processor.getSpecialPity(), processor.getSpecialWork()));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.arcane_processor.links",
                        Component.translatable(processor.hasInputContainer() ? "message.ars_arcane_matrix.state.bound" : "message.ars_arcane_matrix.state.unbound"),
                        Component.translatable(processor.hasOutputContainer() ? "message.ars_arcane_matrix.state.bound" : "message.ars_arcane_matrix.state.unbound"),
                        Component.translatable(processor.hasConsumableContainer() ? "message.ars_arcane_matrix.state.present" : "message.ars_arcane_matrix.state.missing"),
                        processor.getBufferedItemCount()));
            } else if (be instanceof ArcaneSmelterCoreBlockEntity smelter) {
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.arcane_smelter.status", Component.translatable(smelter.getState().translationKey())));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.arcane_smelter.operation",
                        smelter.getInputCount(), smelter.getProgressTicks() / 20, smelter.getFuelItemsRemaining(), smelter.getBufferedItemCount()));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.arcane_smelter.crystal", smelter.getSpecialPity(), smelter.getSpecialWork()));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.arcane_smelter.links",
                        Component.translatable(smelter.hasInputContainer() ? "message.ars_arcane_matrix.state.bound" : "message.ars_arcane_matrix.state.unbound"),
                        Component.translatable(smelter.hasOutputContainer() ? "message.ars_arcane_matrix.state.bound" : "message.ars_arcane_matrix.state.unbound"),
                        Component.translatable(smelter.hasConsumableContainer() ? "message.ars_arcane_matrix.state.present" : "message.ars_arcane_matrix.state.missing")));
            } else if (be instanceof ArcaneCrusherCoreBlockEntity crusher) {
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.arcane_crusher.status", Component.translatable(crusher.getState().translationKey())));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.arcane_crusher.operation",
                        crusher.getInputCount(), crusher.getProgressTicks() / 20, crusher.getBufferedItemCount()));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.arcane_crusher.crystal", crusher.getWaterPity(), crusher.getWaterWork()));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.arcane_crusher.links",
                        Component.translatable(crusher.hasInputContainer() ? "message.ars_arcane_matrix.state.bound" : "message.ars_arcane_matrix.state.unbound"),
                        Component.translatable(crusher.hasOutputContainer() ? "message.ars_arcane_matrix.state.bound" : "message.ars_arcane_matrix.state.unbound"),
                        Component.translatable(crusher.hasConsumableContainer() ? "message.ars_arcane_matrix.state.present" : "message.ars_arcane_matrix.state.missing")));
            } else if (be instanceof SourceStoneGeneratorBlockEntity generator) {
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.source_stone_generator.status", Component.translatable(generator.getOperatingState().translationKey())));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.source_stone_generator.output", generator.getOutputDescription(), generator.getCurrentOutput().getCount()));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.source_stone_generator.progress", generator.getProgress(), generator.getProcessingCost(), generator.getCurrentEfficiency()));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.source_stone_generator.structure", generator.getBufferedItemCount()));
            } else if (be instanceof DrygmyArenaBlockEntity arena) {
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.drygmy_arena.status", Component.translatable(arena.getOperatingState().translationKey())));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.drygmy_arena.target", arena.getTargetDescription()));
                tooltip.add(Component.translatable("jade.ars_arcane_matrix.drygmy_arena.operation",
                        Math.min(arena.getProgressTicks(), arena.getCycleTicks()) / 20,
                        Math.ceilDiv(arena.getCycleTicks(), 20),
                        arena.getBufferedItemCount(),
                        arena.getCatalystPoints(),
                        arena.getRequiredPoints()));
            }
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
