package dev.arsmatrix.network;

import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.menu.WixieOrderTerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Sends only storage entries whose displayed amount changed. */
public record StorageEntriesDeltaPayload(
        int containerId,
        List<WixieOrderTerminalMenu.StorageEntry> entries
) implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 512;

    public static final Type<StorageEntriesDeltaPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "storage_entries_delta"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageEntriesDeltaPayload> STREAM_CODEC =
            StreamCodec.of(StorageEntriesDeltaPayload::encode, StorageEntriesDeltaPayload::decode);

    public StorageEntriesDeltaPayload {
        entries = List.copyOf(entries);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, StorageEntriesDeltaPayload payload) {
        int size = Math.min(MAX_ENTRIES, payload.entries.size());
        buffer.writeVarInt(payload.containerId);
        buffer.writeVarInt(size);
        for (int index = 0; index < size; index++) {
            WixieOrderTerminalMenu.StorageEntry entry = payload.entries.get(index);
            ItemStack.STREAM_CODEC.encode(buffer, entry.stack());
            buffer.writeVarInt(Math.max(0, entry.count()));
        }
    }

    private static StorageEntriesDeltaPayload decode(RegistryFriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        int size = Math.min(MAX_ENTRIES, buffer.readVarInt());
        List<WixieOrderTerminalMenu.StorageEntry> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            entries.add(new WixieOrderTerminalMenu.StorageEntry(
                    ItemStack.STREAM_CODEC.decode(buffer), buffer.readVarInt()));
        }
        return new StorageEntriesDeltaPayload(containerId, entries);
    }

    public static void handle(StorageEntriesDeltaPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof WixieOrderTerminalMenu menu
                && menu.containerId == payload.containerId) {
            menu.applyStorageDelta(payload.entries);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
