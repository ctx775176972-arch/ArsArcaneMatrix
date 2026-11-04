package dev.arsmatrix.network;

import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.menu.ArcaneFluidReservoirMenu;
import dev.arsmatrix.menu.WixieOrderTerminalMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.List;

/** Full fluid identities, including potion components, for an open menu. */
public record FluidContentsPayload(int containerId, List<FluidStack> fluids) implements CustomPacketPayload {
    public static final Type<FluidContentsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "fluid_contents"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FluidContentsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarInt(value.containerId);
                buf.writeVarInt(value.fluids.size());
                value.fluids.forEach(stack -> FluidStack.OPTIONAL_STREAM_CODEC.encode(buf, stack));
            }, buf -> {
                int id = buf.readVarInt();
                int count = buf.readVarInt();
                if (count < 0 || count > 16) throw new IllegalArgumentException("Invalid fluid entry count");
                List<FluidStack> stacks = new ArrayList<>(count);
                for (int i = 0; i < count; i++) stacks.add(FluidStack.OPTIONAL_STREAM_CODEC.decode(buf));
                return new FluidContentsPayload(id, stacks);
            });
    public static boolean same(List<FluidStack> left, List<FluidStack> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            FluidStack a = left.get(i), b = right.get(i);
            if (a.isEmpty() && b.isEmpty()) continue;
            if (a.getAmount() != b.getAmount() || !FluidStack.isSameFluidSameComponents(a, b)) return false;
        }
        return true;
    }
    public static void handle(FluidContentsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var menu = context.player().containerMenu;
            if (menu.containerId != payload.containerId) return;
            if (menu instanceof ArcaneFluidReservoirMenu reservoir) reservoir.applyFluidContents(payload.fluids);
            else if (menu instanceof WixieOrderTerminalMenu terminal) terminal.applyFluidContents(payload.fluids);
        });
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
