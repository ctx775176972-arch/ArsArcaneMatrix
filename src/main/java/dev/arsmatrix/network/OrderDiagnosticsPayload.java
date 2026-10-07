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

/** Low-frequency, server-authoritative diagnostics for an open Wixie order screen. */
public record OrderDiagnosticsPayload(
        int containerId,
        ItemStack target,
        int requested,
        int produced,
        String stateKey,
        String detail,
        int providers,
        int activeWorkers,
        int bufferedItems,
        long elapsedTicks,
        int craftOperations,
        int sourceSpent,
        List<ItemStack> missingItems,
        List<String> activeJobs
) implements CustomPacketPayload {
    private static final int MAX_LIST_SIZE = 16;

    public static final Type<OrderDiagnosticsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "order_diagnostics"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrderDiagnosticsPayload> STREAM_CODEC =
            StreamCodec.of(OrderDiagnosticsPayload::encode, OrderDiagnosticsPayload::decode);

    public OrderDiagnosticsPayload {
        target = target.copy();
        missingItems = missingItems.stream().limit(MAX_LIST_SIZE).map(ItemStack::copy).toList();
        activeJobs = activeJobs.stream().limit(MAX_LIST_SIZE).toList();
    }

    private static void encode(RegistryFriendlyByteBuf buffer, OrderDiagnosticsPayload payload) {
        buffer.writeVarInt(payload.containerId);
        buffer.writeBoolean(!payload.target.isEmpty());
        if (!payload.target.isEmpty()) ItemStack.STREAM_CODEC.encode(buffer, payload.target);
        buffer.writeVarInt(Math.max(0, payload.requested));
        buffer.writeVarInt(Math.max(0, payload.produced));
        buffer.writeUtf(payload.stateKey, 128);
        buffer.writeUtf(payload.detail, 512);
        buffer.writeVarInt(Math.max(0, payload.providers));
        buffer.writeVarInt(Math.max(0, payload.activeWorkers));
        buffer.writeVarInt(Math.max(0, payload.bufferedItems));
        buffer.writeVarLong(Math.max(0L, payload.elapsedTicks));
        buffer.writeVarInt(Math.max(0, payload.craftOperations));
        buffer.writeVarInt(Math.max(0, payload.sourceSpent));
        buffer.writeVarInt(payload.missingItems.size());
        payload.missingItems.forEach(stack -> ItemStack.STREAM_CODEC.encode(buffer, stack));
        buffer.writeVarInt(payload.activeJobs.size());
        payload.activeJobs.forEach(line -> buffer.writeUtf(line, 512));
    }

    private static OrderDiagnosticsPayload decode(RegistryFriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        ItemStack target = buffer.readBoolean() ? ItemStack.STREAM_CODEC.decode(buffer) : ItemStack.EMPTY;
        int requested = buffer.readVarInt();
        int produced = buffer.readVarInt();
        String stateKey = buffer.readUtf(128);
        String detail = buffer.readUtf(512);
        int providers = buffer.readVarInt();
        int activeWorkers = buffer.readVarInt();
        int bufferedItems = buffer.readVarInt();
        long elapsedTicks = buffer.readVarLong();
        int craftOperations = buffer.readVarInt();
        int sourceSpent = buffer.readVarInt();
        int missingSize = Math.min(MAX_LIST_SIZE, Math.max(0, buffer.readVarInt()));
        List<ItemStack> missingItems = new ArrayList<>(missingSize);
        for (int index = 0; index < missingSize; index++) {
            missingItems.add(ItemStack.STREAM_CODEC.decode(buffer));
        }
        int jobsSize = Math.min(MAX_LIST_SIZE, Math.max(0, buffer.readVarInt()));
        List<String> activeJobs = new ArrayList<>(jobsSize);
        for (int index = 0; index < jobsSize; index++) {
            activeJobs.add(buffer.readUtf(512));
        }
        return new OrderDiagnosticsPayload(containerId, target, requested, produced, stateKey, detail,
                providers, activeWorkers, bufferedItems, elapsedTicks, craftOperations, sourceSpent,
                missingItems, activeJobs);
    }

    public static void handle(OrderDiagnosticsPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof WixieOrderTerminalMenu menu
                && menu.containerId == payload.containerId) {
            menu.applyDiagnostics(payload);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
