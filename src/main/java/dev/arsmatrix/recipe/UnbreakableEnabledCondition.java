package dev.arsmatrix.recipe;

import com.mojang.serialization.MapCodec;
import dev.arsmatrix.config.MatrixCommonConfig;
import net.neoforged.neoforge.common.conditions.ICondition;

/** Evaluated on data loading, not against the client's unsynchronized common configuration. */
public record UnbreakableEnabledCondition() implements ICondition {
    public static final MapCodec<UnbreakableEnabledCondition> CODEC =
            MapCodec.unit(new UnbreakableEnabledCondition());

    @Override
    public boolean test(IContext context) {
        return MatrixCommonConfig.ENABLE_UNBREAKABLE_REFINEMENT.get();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
