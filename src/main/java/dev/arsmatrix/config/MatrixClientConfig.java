package dev.arsmatrix.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Local presentation preferences; never used for machine or network decisions. */
public final class MatrixClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue MINE_BEAM;
    public static final ModConfigSpec.DoubleValue STRUCTURE_PREVIEW_OPACITY;
    public static final ModConfigSpec.DoubleValue WIXIE_RANGE_OPACITY;
    public static final ModConfigSpec.BooleanValue WIXIE_HIGHLIGHT_DEVICES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Local visual settings only. These do not change machine operation.",
                "仅影响本机显示，不改变机器运行或其他玩家的设置。").push("visuals");
        MINE_BEAM = builder.comment("Show the Arcane Mine scanning beam. / 显示奥术矿井扫描光束。")
                .translation("config.ars_arcane_matrix.visuals.showMineBeam")
                .define("showMineBeam", true);
        STRUCTURE_PREVIEW_OPACITY = builder.comment(
                "Missing-block projection opacity. / 多方块缺失方块投影的不透明度。")
                .translation("config.ars_arcane_matrix.visuals.structurePreviewOpacity")
                .defineInRange("structurePreviewOpacity", 0.42D, 0.1D, 1.0D);
        WIXIE_RANGE_OPACITY = builder.comment(
                "Wixie network range outline opacity. / 薇克精网络范围线框的不透明度。")
                .translation("config.ars_arcane_matrix.visuals.wixieRangeOpacity")
                .defineInRange("wixieRangeOpacity", 0.9D, 0.1D, 1.0D);
        WIXIE_HIGHLIGHT_DEVICES = builder.comment(
                "Highlight detected devices while viewing Wixie ranges. Disabling also skips the local scan.",
                "查看薇克精工作范围时高亮关联设备；关闭后也停止这项本地扫描。")
                .translation("config.ars_arcane_matrix.visuals.highlightWixieDevices")
                .define("highlightWixieDevices", true);
        builder.pop();
        SPEC = builder.build();
    }

    private MatrixClientConfig() {}
}
