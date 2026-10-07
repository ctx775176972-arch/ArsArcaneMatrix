package dev.arsmatrix.network;

import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.menu.WixieOrderTerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Commits a client-previewed guide chain. The menu validates the whole batch again server-side. */
public record GuideChainEncodePayload(
        int containerId,
        List<ResourceLocation> recipeIds
) implements CustomPacketPayload {
    public static final int MAX_RECIPES = 128;
    public static final Type<GuideChainEncodePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "guide_chain_encode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GuideChainEncodePayload> STREAM_CODEC =
            StreamCodec.of(GuideChainEncodePayload::encode, GuideChainEncodePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, GuideChainEncodePayload payload) {
        int size = Math.min(MAX_RECIPES, payload.recipeIds.size());
        buffer.writeVarInt(payload.containerId);
        buffer.writeVarInt(size);
        payload.recipeIds.stream().limit(size).forEach(buffer::writeResourceLocation);
    }

    private static GuideChainEncodePayload decode(RegistryFriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        int size = Math.min(MAX_RECIPES, buffer.readVarInt());
        List<ResourceLocation> recipeIds = new ArrayList<>(size);
        for (int index = 0; index < size; index++) recipeIds.add(buffer.readResourceLocation());
        return new GuideChainEncodePayload(containerId, List.copyOf(recipeIds));
    }

    public static void handle(GuideChainEncodePayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof WixieOrderTerminalMenu menu
                && menu.containerId == payload.containerId) {
            menu.encodeGuideChain(context.player(), payload.recipeIds);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
