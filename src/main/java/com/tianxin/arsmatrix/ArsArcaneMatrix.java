package com.tianxin.arsmatrix;

import com.mojang.logging.LogUtils;
import com.tianxin.arsmatrix.compat.arsnouveau.ModDocumentation;
import com.tianxin.arsmatrix.config.MatrixConfig;
import com.tianxin.arsmatrix.data.ArcaneMineOreManager;
import com.tianxin.arsmatrix.registry.ModBlockEntities;
import com.tianxin.arsmatrix.registry.ModBlocks;
import com.tianxin.arsmatrix.registry.ModCapabilities;
import com.tianxin.arsmatrix.registry.ModCreativeTabs;
import com.tianxin.arsmatrix.registry.ModItems;
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
            NeoForge.EVENT_BUS.addListener(ModDocumentation::addEntries);
        }
        modContainer.registerConfig(ModConfig.Type.SERVER, MatrixConfig.SPEC, "ars_arcane_matrix-server.toml");

        LOGGER.info("Ars Arcane Matrix initialized.");
    }
}
