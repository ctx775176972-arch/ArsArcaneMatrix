package dev.arsmatrix;

import com.mojang.logging.LogUtils;
import dev.arsmatrix.client.ClientModEvents;
import dev.arsmatrix.client.StructurePreviewRenderer;
import dev.arsmatrix.client.WixieRangeRenderer;
import dev.arsmatrix.compat.arsnouveau.ModDocumentation;
import dev.arsmatrix.compat.arsnouveau.AmethystGolemEnhancements;
import dev.arsmatrix.compat.arsnouveau.AlakarkinosExpeditions;
import dev.arsmatrix.config.MatrixConfig;
import dev.arsmatrix.data.ArcaneMineOreManager;
import dev.arsmatrix.data.SourceStoneGeneratorRecipeManager;
import dev.arsmatrix.data.ArcaneHuntingRuleManager;
import dev.arsmatrix.data.AlakarkinosExpeditionManager;
import dev.arsmatrix.data.ArcaneReactionManager;
import dev.arsmatrix.ritual.ModRituals;
import dev.arsmatrix.event.StarbuncleLogisticsProtectionEvents;
import dev.arsmatrix.registry.ModBlockEntities;
import dev.arsmatrix.registry.ModBlocks;
import dev.arsmatrix.registry.ModCapabilities;
import dev.arsmatrix.registry.ModCreativeTabs;
import dev.arsmatrix.registry.ModDataComponents;
import dev.arsmatrix.registry.ModItems;
import dev.arsmatrix.registry.ModMenus;
import dev.arsmatrix.spell.ModGlyphs;
import dev.arsmatrix.world.ModChunkLoading;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(ArsArcaneMatrix.MOD_ID)
public class ArsArcaneMatrix {

    public static final String MOD_ID = "ars_arcane_matrix";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArsArcaneMatrix(IEventBus modBus, ModContainer modContainer) {
        ModGlyphs.register();
        // 🟢 核心修复：在此处注册数据同步附件，让 NeoForge 在启动时强制绑定槽位，彻底消除 Unbound 崩溃
        ModDataComponents.register(modBus);
        ModBlocks.register(modBus);
        ModRituals.register();
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModCreativeTabs.register(modBus);
        modBus.addListener(ModChunkLoading::registerControllers);
        modBus.addListener(ModCapabilities::register);
        NeoForge.EVENT_BUS.addListener(ArcaneMineOreManager::registerReloadListener);
        NeoForge.EVENT_BUS.addListener(SourceStoneGeneratorRecipeManager::registerReloadListener);
        NeoForge.EVENT_BUS.addListener(ArcaneHuntingRuleManager::registerReloadListener);
        NeoForge.EVENT_BUS.addListener(AlakarkinosExpeditionManager::registerReloadListener);
        NeoForge.EVENT_BUS.addListener(ArcaneReactionManager::registerReloadListener);
        NeoForge.EVENT_BUS.addListener(AmethystGolemEnhancements::onEntityTick);
        NeoForge.EVENT_BUS.addListener(AlakarkinosExpeditions::onEntityTick);
        NeoForge.EVENT_BUS.addListener(StarbuncleLogisticsProtectionEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(StarbuncleLogisticsProtectionEvents::onLevelSound);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(ClientModEvents::clientSetup);
            modBus.addListener(ClientModEvents::registerLayerDefinitions);
            modBus.addListener(ClientModEvents::registerRenderers);
            modBus.addListener(ClientModEvents::registerBlockColors);
            modBus.addListener(ClientModEvents::registerItemColors);
            modBus.addListener(ClientModEvents::registerMenuScreens);
            NeoForge.EVENT_BUS.addListener(ModDocumentation::addEntries);
            NeoForge.EVENT_BUS.addListener(StructurePreviewRenderer::onRightClickBlock);
            NeoForge.EVENT_BUS.addListener(WixieRangeRenderer::onRightClickBlock);
            NeoForge.EVENT_BUS.addListener(WixieRangeRenderer::onRenderLevelStage);
        }
        modContainer.registerConfig(ModConfig.Type.SERVER, MatrixConfig.SPEC, "ars_arcane_matrix-server.toml");

        LOGGER.info("Ars Arcane Matrix initialized.");
    }
}
