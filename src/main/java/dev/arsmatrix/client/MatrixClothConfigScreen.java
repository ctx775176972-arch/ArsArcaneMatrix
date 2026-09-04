package dev.arsmatrix.client;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import dev.arsmatrix.config.MatrixClientConfig;
import dev.arsmatrix.config.MatrixCommonConfig;
import dev.arsmatrix.config.MatrixConfig;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/** Loaded only on clients with Cloth Config installed; no replacement config files or sync protocol. */
public final class MatrixClothConfigScreen {
    private static final String PREFIX = "config.ars_arcane_matrix.";

    private MatrixClothConfigScreen() {}

    public static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (mod, parent) -> create(parent));
    }

    private static Component text(String key) {
        return Component.translatable(PREFIX + key);
    }

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create().setParentScreen(parent).setTitle(text("title"));
        ConfigEntryBuilder entries = builder.entryBuilder();
        var server = Minecraft.getInstance().getSingleplayerServer();
        boolean editServer = server != null && !server.isPublished() && MatrixConfig.SPEC.isLoaded();
        List<Runnable> clientChanges = new ArrayList<>();
        List<Runnable> serverChanges = new ArrayList<>();
        List<Runnable> commonChanges = new ArrayList<>();
        boolean editCommon = MatrixCommonConfig.SPEC.isLoaded()
                && (Minecraft.getInstance().level == null || server != null && !server.isPublished());
        ConfigCategory client = builder.getOrCreateCategory(text("client"));
        addEntries(client, entries, MatrixClientConfig.SPEC.getValues(), "",
                MatrixClientConfig.SPEC.isLoaded(), MatrixClientConfig.SPEC.isLoaded(), clientChanges);
        ConfigCategory gameplay = builder.getOrCreateCategory(text("server"));
        addEntries(gameplay, entries, MatrixConfig.SPEC.getValues(), "",
                editServer, MatrixConfig.SPEC.isLoaded(), serverChanges);
        ConfigCategory common = builder.getOrCreateCategory(text("common"));
        addEntries(common, entries, MatrixCommonConfig.SPEC.getValues(), "",
                editCommon, MatrixCommonConfig.SPEC.isLoaded(), commonChanges);
        builder.setSavingRunnable(() -> {
            if (editCommon && !commonChanges.isEmpty() && MatrixCommonConfig.SPEC.isLoaded()) {
                commonChanges.forEach(Runnable::run);
                MatrixCommonConfig.SPEC.save();
            }
            if (!clientChanges.isEmpty() && MatrixClientConfig.SPEC.isLoaded()) {
                clientChanges.forEach(Runnable::run);
                MatrixClientConfig.SPEC.save();
            }
            // Never edit synced remote-server values or mutate the running server from the render thread.
            if (editServer && !serverChanges.isEmpty() && server != null) {
                server.execute(() -> {
                    if (Minecraft.getInstance().getSingleplayerServer() == server
                            && !server.isPublished() && MatrixConfig.SPEC.isLoaded()) {
                        serverChanges.forEach(Runnable::run);
                        MatrixConfig.SPEC.save();
                    }
                });
            }
        });
        return builder.build();
    }

    private static void addEntries(ConfigCategory category, ConfigEntryBuilder entries,
                                   UnmodifiableConfig values, String prefix, boolean editable,
                                   boolean loaded, List<Runnable> changes) {
        for (var node : values.entrySet()) {
            String path = prefix + node.getKey();
            if (node.getValue() instanceof UnmodifiableConfig nested) {
                category.addEntry(entries.startTextDescription(text(path)).build());
                addEntries(category, entries, nested, path + ".", editable, loaded, changes);
            } else if (node.getValue() instanceof ModConfigSpec.ConfigValue<?> value) {
                addValue(category, entries, path, value, editable, loaded, changes);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addValue(ConfigCategory category, ConfigEntryBuilder entries, String path,
                                 ModConfigSpec.ConfigValue value, boolean editable, boolean loaded,
                                 List<Runnable> changes) {
        Object current = loaded ? value.get() : value.getDefault();
        ModConfigSpec.ValueSpec spec = value.getSpec();
        Component name = text(path);
        Component[] tooltip = {text(path + ".tooltip")};
        Function<Object, Optional<Component>> validation = candidate -> valid(path, spec, candidate)
                ? Optional.empty() : Optional.of(text("invalid"));
        Consumer<Object> save = candidate -> {
            if (editable && valid(path, spec, candidate) && !current.equals(candidate)) {
                Object snapshot = candidate instanceof List<?> list ? List.copyOf(list) : candidate;
                changes.add(() -> value.set(snapshot));
            }
        };
        AbstractConfigListEntry entry;
        if (current instanceof Boolean b) {
            entry = entries.startBooleanToggle(name, b).setDefaultValue((Boolean) value.getDefault())
                    .setTooltip(tooltip).setSaveConsumer(save::accept).build();
        } else if (current instanceof Integer i) {
            entry = entries.startIntField(name, i).setDefaultValue((Integer) value.getDefault())
                    .setTooltip(tooltip).setErrorSupplier(validation::apply).setSaveConsumer(save::accept).build();
        } else if (current instanceof Double d) {
            entry = entries.startDoubleField(name, d).setDefaultValue((Double) value.getDefault())
                    .setTooltip(tooltip).setErrorSupplier(validation::apply).setSaveConsumer(save::accept).build();
        } else if (current instanceof List<?> list) {
            entry = entries.startIntList(name, new ArrayList<>((List<Integer>) list))
                    .setDefaultValue((List<Integer>) value.getDefault()).setTooltip(tooltip)
                    .setErrorSupplier(validation::apply).setSaveConsumer(save::accept).build();
        } else {
            return;
        }
        entry.setEditable(editable);
        entry.setRequiresRestart(spec.restartType() != ModConfigSpec.RestartType.NONE);
        category.addEntry(entry);
    }

    private static boolean valid(String path, ModConfigSpec.ValueSpec spec, Object candidate) {
        if (!spec.test(candidate)) return false;
        if (candidate instanceof Double number && !Double.isFinite(number)) return false;
        if (candidate instanceof List<?> list) {
            if (list.size() != 4 || list.stream().anyMatch(v -> !(v instanceof Integer))) return false;
            int previous = 0;
            for (Object element : list) {
                int number = (Integer) element;
                if (path.endsWith("layerSizes")) {
                    if (number < 3 || number > 15 || number % 2 == 0 || number <= previous) return false;
                } else if (number < 1 || number > 72_000) return false;
                previous = number;
            }
        }
        return true;
    }
}
