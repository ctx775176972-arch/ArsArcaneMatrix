package dev.arsmatrix.network;

import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.menu.WixieOrderTerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Extracts the item that was actually clicked instead of a refresh-sensitive list index. */
public record StorageExtractionPayload(
        int containerId,
        ItemStack template,
        int mode
) implements CustomPacketPayload {
    public static final int ONE = 0;
    public static final int STACK = 1;
    public static final int ALL_FITTING = 2;

    public static final Type<StorageExtractionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "storage_extract"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageExtractionPayload> STREAM_CODEC =
            StreamCodec.of(StorageExtractionPayload::encode, StorageExtractionPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, StorageExtractionPayload payload) {
        buffer.writeVarInt(payload.containerId);
        ItemStack.STREAM_CODEC.encode(buffer, payload.template.copyWithCount(1));
        buffer.writeByte(Math.max(ONE, Math.min(ALL_FITTING, payload.mode)));
    }

    private static StorageExtractionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new StorageExtractionPayload(
                buffer.readVarInt(), ItemStack.STREAM_CODEC.decode(buffer), buffer.readByte());
    }

    public static void handle(StorageExtractionPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof WixieOrderTerminalMenu menu
                && menu.containerId == payload.containerId) {
            menu.extractStorageMatching(context.player(), payload.template, payload.mode);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
