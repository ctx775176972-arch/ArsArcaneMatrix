package dev.arsmatrix.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {
    private ModNetworking() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                StorageEntriesDeltaPayload.TYPE,
                StorageEntriesDeltaPayload.STREAM_CODEC,
                StorageEntriesDeltaPayload::handle);
        registrar.playToClient(
                OrderDiagnosticsPayload.TYPE,
                OrderDiagnosticsPayload.STREAM_CODEC,
                OrderDiagnosticsPayload::handle);
        registrar.playToServer(
                StorageCraftingFillPayload.TYPE,
                StorageCraftingFillPayload.STREAM_CODEC,
                StorageCraftingFillPayload::handle);
        registrar.playToServer(
                StorageExtractionPayload.TYPE,
                StorageExtractionPayload.STREAM_CODEC,
                StorageExtractionPayload::handle);
        registrar.playToServer(
                GuideChainEncodePayload.TYPE,
                GuideChainEncodePayload.STREAM_CODEC,
                GuideChainEncodePayload::handle);
    }
}
