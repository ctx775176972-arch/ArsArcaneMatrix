package dev.arsmatrix.network;

import dev.arsmatrix.ArsArcaneMatrix;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Only the looked-at target is synchronized, not the whole acceleration map. */
public record AccelerationStatusPayload(ResourceLocation dimension, BlockPos pos,
        int multiplier, int remainingTicks) implements CustomPacketPayload {
    public static final Type<AccelerationStatusPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "acceleration_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AccelerationStatusPayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> {
                buf.writeResourceLocation(value.dimension);
                buf.writeBlockPos(value.pos);
                buf.writeVarInt(value.multiplier);
                buf.writeVarInt(value.remainingTicks);
            }, buf -> new AccelerationStatusPayload(buf.readResourceLocation(), buf.readBlockPos(),
                    buf.readVarInt(), buf.readVarInt()));
    public static void handle(AccelerationStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> dev.arsmatrix.client.AccelerationStatusHud.accept(payload));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
