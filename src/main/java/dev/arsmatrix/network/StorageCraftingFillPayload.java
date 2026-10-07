package dev.arsmatrix.network;

import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.menu.WixieOrderTerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Requests a server-authoritative JEI fill of the advanced lectern's manual grid. */
public record StorageCraftingFillPayload(
        int containerId,
        ResourceLocation recipeId,
        boolean maxTransfer
) implements CustomPacketPayload {
    public static final Type<StorageCraftingFillPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "storage_crafting_fill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageCraftingFillPayload> STREAM_CODEC =
            StreamCodec.of(StorageCraftingFillPayload::encode, StorageCraftingFillPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, StorageCraftingFillPayload payload) {
        buffer.writeVarInt(payload.containerId);
        buffer.writeResourceLocation(payload.recipeId);
        buffer.writeBoolean(payload.maxTransfer);
    }

    private static StorageCraftingFillPayload decode(RegistryFriendlyByteBuf buffer) {
        return new StorageCraftingFillPayload(
                buffer.readVarInt(), buffer.readResourceLocation(), buffer.readBoolean());
    }

    public static void handle(StorageCraftingFillPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof WixieOrderTerminalMenu menu
                && menu.containerId == payload.containerId) {
            menu.fillStorageCraftingFromJei(context.player(), payload.recipeId, payload.maxTransfer);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
