package dev.arsmatrix;

import com.mojang.logging.LogUtils;
import dev.arsmatrix.client.ClientModEvents;
import dev.arsmatrix.compat.arsnouveau.ModDocumentation;
import dev.arsmatrix.config.MatrixConfig;
import dev.arsmatrix.data.ArcaneMineOreManager;
import dev.arsmatrix.registry.ModBlockEntities;
import dev.arsmatrix.registry.ModBlocks;
import dev.arsmatrix.registry.ModCapabilities;
import dev.arsmatrix.registry.ModCreativeTabs;
import dev.arsmatrix.registry.ModItems;
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
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModCreativeTabs.register(modBus);
        modBus.addListener(ModCapabilities::register);
        NeoForge.EVENT_BUS.addListener(ArcaneMineOreManager::registerReloadListener);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(ClientModEvents::registerLayerDefinitions);
            modBus.addListener(ClientModEvents::registerRenderers);
            NeoForge.EVENT_BUS.addListener(ModDocumentation::addEntries);
        }
        modContainer.registerConfig(ModConfig.Type.SERVER, MatrixConfig.SPEC, "ars_arcane_matrix-server.toml");

        LOGGER.info("Ars Arcane Matrix initialized.");
    }
}
