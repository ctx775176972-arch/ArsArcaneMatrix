package com.tianxin.arsmatrix;

import com.mojang.logging.LogUtils;
import com.tianxin.arsmatrix.registry.ModBlockEntities;
import com.tianxin.arsmatrix.registry.ModBlocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(ArsArcaneMatrix.MOD_ID)
public class ArsArcaneMatrix {

    public static final String MOD_ID = "ars_arcane_matrix";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArsArcaneMatrix(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);

        LOGGER.info("Ars Arcane Matrix initialized.");
    }
}