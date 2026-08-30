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
    }
}
