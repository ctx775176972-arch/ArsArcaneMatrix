package com.tianxin.arsmatrix.blockentity;

import com.tianxin.arsmatrix.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MatrixCoreBlockEntity extends BlockEntity {

    /**
     * 是否已经形成多方块结构
     */
    private boolean formed = false;

    /**
     * 是否已经启动
     */
    private boolean active = false;

    /**
     * 当前储存的 Source
     */
    private long storedSource = 0;

    /**
     * 最大储量
     */
    private long maxSource = 10_000_000L;

    /**
     * Tick计数
     */
    private long tickCounter = 0;

    public MatrixCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MATRIX_CORE.get(), pos, state);
    }

    /**
     * 服务端Tick
     */
    public void serverTick() {

        tickCounter++;

        // 后续这里加入：
        // 多方块检测
        // 启动仪式
        // Source生产
        // 网络输出

        setChanged();
    }

    /**
     * 玩家右键显示状态
     */
    public Component getStatusComponent() {

        return Component.literal(
                "Arcane Matrix\n"
                        + "Formed : " + formed + "\n"
                        + "Active : " + active + "\n"
                        + "Source : " + storedSource + " / " + maxSource
        );
    }

    /*========================*/
    /*        Getter          */
    /*========================*/

    public boolean isFormed() {
        return formed;
    }

    public boolean isActive() {
        return active;
    }

    public long getStoredSource() {
        return storedSource;
    }

    public long getMaxSource() {
        return maxSource;
    }

    /*========================*/
    /*        Setter          */
    /*========================*/

    public void setFormed(boolean formed) {
        this.formed = formed;
        setChanged();
    }

    public void setActive(boolean active) {
        this.active = active;
        setChanged();
    }

    public void setStoredSource(long source) {

        this.storedSource = Math.max(0, Math.min(source, maxSource));

        setChanged();
    }

    public void addSource(long amount) {

        setStoredSource(storedSource + amount);
    }

    public boolean consumeSource(long amount) {

        if (storedSource < amount)
            return false;

        storedSource -= amount;

        setChanged();

        return true;
    }

    /*========================*/
    /*         NBT            */
    /*========================*/

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        tag.putBoolean("Formed", formed);
        tag.putBoolean("Active", active);
        tag.putLong("StoredSource", storedSource);
    }

    @Override
    protected void loadAdditional(CompoundTag tag) {
        super.loadAdditional(tag);

        formed = tag.getBoolean("Formed");
        active = tag.getBoolean("Active");
        storedSource = tag.getLong("StoredSource");
    }
}