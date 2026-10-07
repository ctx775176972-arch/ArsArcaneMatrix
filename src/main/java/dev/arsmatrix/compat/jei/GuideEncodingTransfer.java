package dev.arsmatrix.compat.jei;

import dev.arsmatrix.client.WixieOrderTerminalScreen;
import dev.arsmatrix.menu.WixieOrderTerminalMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** Shared JEI transfer behavior: click encodes one guide, Shift-click previews the full chain. */
final class GuideEncodingTransfer {
    private GuideEncodingTransfer() {}

    static void submit(WixieOrderTerminalMenu menu, Player player, ResourceLocation recipeId) {
        Minecraft minecraft = Minecraft.getInstance();
        WixieOrderTerminalScreen terminalScreen = WixieOrderTerminalScreen.forMenu(menu);
        if (Screen.hasShiftDown()
                && terminalScreen != null
                && terminalScreen.openGuideChainPreview(recipeId)) {
            return;
        }
        int buttonId = WixieOrderTerminalMenu.recipeEncodingButton(recipeId);
        menu.clickMenuButton(player, buttonId);
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }
}
