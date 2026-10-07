package dev.arsmatrix.menu;

import dev.arsmatrix.blockentity.StarbuncleLogisticsHubBlockEntity;
import net.minecraft.world.inventory.ContainerData;

/**
 * Server-side menu data for the Starbuncle logistics hub.
 *
 * <p>This intentionally lives in a top-level class. Some module/class-loader
 * combinations fail to resolve the anonymous {@code Menu$1} class that javac
 * creates for an inline ContainerData implementation.</p>
 */
final class StarbuncleLogisticsHubContainerData implements ContainerData {
    private final StarbuncleLogisticsHubBlockEntity hub;

    StarbuncleLogisticsHubContainerData(StarbuncleLogisticsHubBlockEntity hub) {
        this.hub = hub;
    }

    @Override
    public int get(int index) {
        return switch (index) {
            case 0 -> hub.getNearbyOwned();
            case 1 -> hub.getState().ordinal();
            case 2 -> hub.isAllowList() ? 1 : 0;
            case 3 -> hub.getMatchMode().ordinal();
            case 4 -> hub.isAutomaticRecall() ? 1 : 0;
            case 5 -> hub.isTeleportOnStuck() ? 1 : 0;
            case 6 -> hub.getUpgradeTier();
            case 7 -> hub.getSharedThroughput();
            default -> 0;
        };
    }

    @Override
    public void set(int index, int value) {
    }

    @Override
    public int getCount() {
        return 8;
    }
}
