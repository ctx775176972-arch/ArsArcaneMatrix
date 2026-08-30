package dev.arsmatrix.source;

import com.hollingsworth.arsnouveau.api.item.IWandable;
import dev.arsmatrix.blockentity.AdvancedStorageLecternBlockEntity;
import dev.arsmatrix.blockentity.ArcaneSourceJarBlockEntity;
import dev.arsmatrix.blockentity.IntegratedSourceRelayBlockEntity;
import dev.arsmatrix.blockentity.MatrixCoreBlockEntity;
import dev.arsmatrix.blockentity.SuperSourceJarCoreBlockEntity;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Source-network behavior exposed through Ars Nouveau's standard Dominion Wand callbacks. */
public final class SourceNetworkLinking {
    private SourceNetworkLinking() {}

    public static IWandable.Result connect(BlockEntity endpoint, GlobalPos target, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || endpoint.getLevel() == null || target == null) {
            return IWandable.Result.FAIL;
        }
        var server = serverPlayer.getServer();
        if (server == null) return IWandable.Result.FAIL;
        var endpointLevel = endpoint.getLevel();
        if (endpointLevel == null) return IWandable.Result.FAIL;
        EndpointKind endpointKind = kindOf(endpoint);
        GlobalPos endpointPos = GlobalPos.of(endpointLevel.dimension(), endpoint.getBlockPos());
        if (endpointKind == null || endpointPos.equals(target)) return IWandable.Result.FAIL;

        ServerLevel targetLevel = server.getLevel(target.dimension());
        if (targetLevel == null || !targetLevel.hasChunkAt(target.pos())) {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.source_network.target_unloaded"), true);
            return IWandable.Result.FAIL;
        }
        EndpointKind targetKind = kindOf(targetLevel.getBlockEntity(target.pos()));
        if (targetKind == null) {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.source_network.invalid_pair"), true);
            return IWandable.Result.FAIL;
        }

        Endpoint first = new Endpoint(endpointPos, endpointKind);
        Endpoint second = new Endpoint(target, targetKind);
        Endpoint jar = endpoint(first, second, EndpointKind.JAR);
        Endpoint relay = endpoint(first, second, EndpointKind.RELAY);
        Endpoint gateway = endpoint(first, second, EndpointKind.GATEWAY);
        Endpoint matrix = endpoint(first, second, EndpointKind.MATRIX);
        SourceNetworkSavedData data = SourceNetworkSavedData.get(server);

        if (jar != null && relay != null) {
            if (!jar.pos().dimension().equals(relay.pos().dimension())) {
                player.displayClientMessage(Component.translatable(
                        "message.ars_arcane_matrix.source_network.gateway_required"), true);
                return IWandable.Result.FAIL;
            }
            data.connectJarToRelay(jar.pos(), relay.pos());
        } else if (jar != null && gateway != null) {
            data.connectJarToGateway(jar.pos(), gateway.pos());
        } else if (relay != null && gateway != null) {
            data.connectRelayToGateway(relay.pos(), gateway.pos());
        } else if (relay != null && matrix != null) {
            if (!relay.pos().dimension().equals(matrix.pos().dimension())) {
                player.displayClientMessage(Component.translatable(
                        "message.ars_arcane_matrix.source_network.gateway_required"), true);
                return IWandable.Result.FAIL;
            }
            data.connectRelayToGateway(relay.pos(), matrix.pos());
        } else {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.source_network.invalid_pair"), true);
            return IWandable.Result.FAIL;
        }

        player.displayClientMessage(Component.translatable(
                "message.ars_arcane_matrix.source_network.connected"), true);
        return IWandable.Result.SUCCESS;
    }

    public static IWandable.Result clear(BlockEntity endpoint, Player player) {
        clear(endpoint);
        player.displayClientMessage(Component.translatable(
                "message.ars_arcane_matrix.source_network.cleared"), true);
        return IWandable.Result.SUCCESS;
    }

    public static void clear(BlockEntity endpoint) {
        var endpointLevel = endpoint.getLevel();
        if (endpointLevel == null || endpointLevel.getServer() == null) return;
        EndpointKind kind = kindOf(endpoint);
        if (kind == null) return;
        GlobalPos pos = GlobalPos.of(endpointLevel.dimension(), endpoint.getBlockPos());
        SourceNetworkSavedData data = SourceNetworkSavedData.get(endpointLevel.getServer());
        if (kind == EndpointKind.GATEWAY) data.unlinkGateway(pos);
        else data.unlinkNode(pos);
    }

    public static boolean isSourceEndpoint(GlobalPos target, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || target == null
                || serverPlayer.getServer() == null) return false;
        ServerLevel targetLevel = serverPlayer.getServer().getLevel(target.dimension());
        return targetLevel != null && targetLevel.hasChunkAt(target.pos())
                && kindOf(targetLevel.getBlockEntity(target.pos())) != null;
    }

    public static void remove(ServerLevel level, BlockEntity blockEntity) {
        EndpointKind kind = kindOf(blockEntity);
        if (kind == null) return;
        GlobalPos pos = GlobalPos.of(level.dimension(), blockEntity.getBlockPos());
        SourceNetworkSavedData data = SourceNetworkSavedData.get(level.getServer());
        if (kind == EndpointKind.GATEWAY) data.unlinkGateway(pos);
        else data.unlinkNode(pos);
    }

    private static Endpoint endpoint(Endpoint first, Endpoint second, EndpointKind kind) {
        return first.kind() == kind ? first : second.kind() == kind ? second : null;
    }

    private static EndpointKind kindOf(BlockEntity blockEntity) {
        if (blockEntity instanceof ArcaneSourceJarBlockEntity) return EndpointKind.JAR;
        if (blockEntity instanceof SuperSourceJarCoreBlockEntity) return EndpointKind.JAR;
        if (blockEntity instanceof IntegratedSourceRelayBlockEntity) return EndpointKind.RELAY;
        if (blockEntity instanceof AdvancedStorageLecternBlockEntity) return EndpointKind.GATEWAY;
        if (blockEntity instanceof MatrixCoreBlockEntity) return EndpointKind.MATRIX;
        return null;
    }

    private enum EndpointKind { JAR, RELAY, GATEWAY, MATRIX }
    private record Endpoint(GlobalPos pos, EndpointKind kind) {}
}
