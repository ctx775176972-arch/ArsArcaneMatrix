package dev.arsmatrix.registry;

import dev.arsmatrix.ArsArcaneMatrix;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.function.Supplier;

public class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ArsArcaneMatrix.MOD_ID);

    public record WhirlisprigData(int modeId, int ancientSpecies) {
        public static final Codec<WhirlisprigData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("mode_id").forGetter(WhirlisprigData::modeId),
                Codec.INT.fieldOf("ancient_species").forGetter(WhirlisprigData::ancientSpecies)
        ).apply(instance, WhirlisprigData::new));
    }

    // 保留最基础的空结构，确保游戏主类在执行初始化注册时 100% 能够解析通过，绝不引发未绑定崩溃
    public static final Supplier<AttachmentType<WhirlisprigData>> WHIRLISPRIG_DATA =
            ATTACHMENT_TYPES.register("whirlisprig_data", () -> AttachmentType.builder(() -> new WhirlisprigData(0, 0))
                    .serialize(WhirlisprigData.CODEC)
                    .build());
}
