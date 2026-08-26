package dev.arsmatrix.source;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Persistent, order-independent links between Source storage, relays, and lectern gateways. */
public final class SourceNetworkSavedData extends SavedData {
    private static final String DATA_NAME = "ars_arcane_matrix_source_network";
    private final Map<GlobalPos, GlobalPos> jarTargets = new HashMap<>();
    private final Map<GlobalPos, GlobalPos> relaySources = new HashMap<>();

    public static SourceNetworkSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(SourceNetworkSavedData::new, SourceNetworkSavedData::load), DATA_NAME);
    }

    public static SourceNetworkSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SourceNetworkSavedData data = new SourceNetworkSavedData();
        readLinks(tag.getList("JarTargets", CompoundTag.TAG_COMPOUND), data.jarTargets);
        readLinks(tag.getList("RelaySources", CompoundTag.TAG_COMPOUND), data.relaySources);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("JarTargets", writeLinks(jarTargets));
        tag.put("RelaySources", writeLinks(relaySources));
        return tag;
    }

    public void connectJarToRelay(GlobalPos jar, GlobalPos relay) {
        unlinkNode(jar);
        unlinkNode(relay);
        jarTargets.put(jar, relay);
        relaySources.put(relay, jar);
        setDirty();
    }

    public void connectJarToGateway(GlobalPos jar, GlobalPos gateway) {
        unlinkNode(jar);
        jarTargets.put(jar, gateway);
        setDirty();
    }

    public void connectRelayToGateway(GlobalPos relay, GlobalPos gateway) {
        unlinkNode(relay);
        relaySources.put(relay, gateway);
        setDirty();
    }

    public GlobalPos sourceForRelay(GlobalPos relay) {
        return relaySources.get(relay);
    }

    public GlobalPos targetForJar(GlobalPos jar) {
        return jarTargets.get(jar);
    }

    public List<GlobalPos> jarsForGateway(GlobalPos gateway) {
        List<GlobalPos> result = new ArrayList<>();
        jarTargets.forEach((jar, target) -> { if (target.equals(gateway)) result.add(jar); });
        return List.copyOf(result);
    }

    public List<GlobalPos> relaysForGateway(GlobalPos gateway) {
        List<GlobalPos> result = new ArrayList<>();
        relaySources.forEach((relay, source) -> { if (source.equals(gateway)) result.add(relay); });
        return List.copyOf(result);
    }

    public void unlinkNode(GlobalPos node) {
        boolean changed = jarTargets.remove(node) != null || relaySources.remove(node) != null;
        changed |= jarTargets.entrySet().removeIf(entry -> entry.getValue().equals(node));
        changed |= relaySources.entrySet().removeIf(entry -> entry.getValue().equals(node));
        if (changed) setDirty();
    }

    public void unlinkGateway(GlobalPos gateway) {
        boolean changed = jarTargets.entrySet().removeIf(entry -> entry.getValue().equals(gateway));
        changed |= relaySources.entrySet().removeIf(entry -> entry.getValue().equals(gateway));
        if (changed) setDirty();
    }

    private static ListTag writeLinks(Map<GlobalPos, GlobalPos> links) {
        ListTag list = new ListTag();
        links.forEach((from, to) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("From", saveGlobalPos(from));
            entry.put("To", saveGlobalPos(to));
            list.add(entry);
        });
        return list;
    }

    private static void readLinks(ListTag list, Map<GlobalPos, GlobalPos> output) {
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            GlobalPos from = loadGlobalPos(entry.getCompound("From"));
            GlobalPos to = loadGlobalPos(entry.getCompound("To"));
            if (from != null && to != null) output.put(from, to);
        }
    }

    private static CompoundTag saveGlobalPos(GlobalPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", pos.dimension().location().toString());
        tag.putLong("Pos", pos.pos().asLong());
        return tag;
    }

    private static GlobalPos loadGlobalPos(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (id == null || !tag.contains("Pos")) return null;
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, id);
        return GlobalPos.of(dimension, BlockPos.of(tag.getLong("Pos")));
    }
}
