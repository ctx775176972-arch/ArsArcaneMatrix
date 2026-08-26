package dev.arsmatrix.client;

import dev.arsmatrix.blockentity.StarbuncleLogisticsHubBlockEntity;
import dev.arsmatrix.menu.StarbuncleLogisticsHubMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public final class StarbuncleLogisticsHubScreen
        extends AbstractContainerScreen<StarbuncleLogisticsHubMenu> {
    private int selectedRoute;
    private Button filterTypeButton;
    private Button matchModeButton;
    private Button autoRecallButton;
    private Button stuckTeleportButton;

    public StarbuncleLogisticsHubScreen(
            StarbuncleLogisticsHubMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        selectedRoute = menu.getInitialSelectedRoute();
        imageWidth = 300;
        imageHeight = 234;
        inventoryLabelY = 143;
    }

    @Override protected void init() {
        super.init();
        filterTypeButton = addRenderableWidget(Button.builder(filterTypeLabel(), b -> {
            send(StarbuncleLogisticsHubMenu.TOGGLE_ALLOW_LIST);
            b.setMessage(Component.translatable(menu.isAllowList()
                    ? "screen.ars_arcane_matrix.starbuncle_hub.deny"
                    : "screen.ars_arcane_matrix.starbuncle_hub.allow"));
        }).bounds(leftPos + 88, topPos + 15, 38, 16).build());
        matchModeButton = addRenderableWidget(Button.builder(matchModeLabel(), b -> {
            send(StarbuncleLogisticsHubMenu.CYCLE_MATCH_MODE);
            var modes = dev.arsmatrix.item.HubFilterScrollItem.MatchMode.values();
            var next = modes[(menu.getMatchMode().ordinal() + 1) % modes.length];
            b.setMessage(Component.translatable("screen.ars_arcane_matrix.starbuncle_hub.match."
                    + next.name().toLowerCase(java.util.Locale.ROOT)));
        }).bounds(leftPos + 128, topPos + 15, 40, 16).build());

        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (!menu.getRoutes().isEmpty()) {
                selectedRoute = Math.floorMod(selectedRoute - 1, menu.getRoutes().size());
                send(StarbuncleLogisticsHubMenu.SELECT_ROUTE_BASE + selectedRoute);
            }
        }).bounds(leftPos + 180, topPos + 15, 22, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            if (!menu.getRoutes().isEmpty()) {
                selectedRoute = (selectedRoute + 1) % menu.getRoutes().size();
                send(StarbuncleLogisticsHubMenu.SELECT_ROUTE_BASE + selectedRoute);
            }
        }).bounds(leftPos + 274, topPos + 15, 22, 16).build());
        addRenderableWidget(Button.builder(Component.translatable(
                "screen.ars_arcane_matrix.starbuncle_hub.register_nearby"),
                b -> send(StarbuncleLogisticsHubMenu.REGISTER_NEARBY))
                .bounds(leftPos + 180, topPos + 125, 57, 16).build());
        addRenderableWidget(Button.builder(Component.translatable(
                "screen.ars_arcane_matrix.starbuncle_hub.clear_registrations"),
                b -> send(StarbuncleLogisticsHubMenu.CLEAR_REGISTRATIONS))
                .bounds(leftPos + 239, topPos + 125, 57, 16).build());
        addRenderableWidget(Button.builder(Component.translatable(
                "screen.ars_arcane_matrix.starbuncle_hub.recall_selected"),
                b -> send(StarbuncleLogisticsHubMenu.RECALL_SELECTED))
                .bounds(leftPos + 180, topPos + 143, 116, 16).build());
        addRenderableWidget(Button.builder(Component.translatable(
                "screen.ars_arcane_matrix.starbuncle_hub.recall_incomplete"),
                b -> send(StarbuncleLogisticsHubMenu.RECALL_INCOMPLETE))
                .bounds(leftPos + 180, topPos + 161, 116, 16).build());
        addRenderableWidget(Button.builder(Component.translatable(
                "screen.ars_arcane_matrix.starbuncle_hub.recall_all"),
                b -> send(StarbuncleLogisticsHubMenu.RECALL_ALL))
                .bounds(leftPos + 180, topPos + 179, 116, 16).build());
        autoRecallButton = addRenderableWidget(Button.builder(autoRecallLabel(), b -> {
            send(StarbuncleLogisticsHubMenu.TOGGLE_AUTO_RECALL);
            b.setMessage(Component.translatable(menu.isAutomaticRecall()
                    ? "screen.ars_arcane_matrix.starbuncle_hub.auto_off"
                    : "screen.ars_arcane_matrix.starbuncle_hub.auto_on"));
        }).bounds(leftPos + 180, topPos + 197, 116, 16).build());
        stuckTeleportButton = addRenderableWidget(Button.builder(stuckTeleportLabel(), b -> {
            send(StarbuncleLogisticsHubMenu.TOGGLE_STUCK_TELEPORT);
        }).bounds(leftPos + 180, topPos + 215, 116, 16).build());
    }

    @Override protected void containerTick() {
        super.containerTick();
        if (filterTypeButton != null) filterTypeButton.setMessage(filterTypeLabel());
        if (matchModeButton != null) matchModeButton.setMessage(matchModeLabel());
        if (autoRecallButton != null) autoRecallButton.setMessage(autoRecallLabel());
        if (stuckTeleportButton != null) stuckTeleportButton.setMessage(stuckTeleportLabel());
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
    private Component filterTypeLabel() {
        return Component.translatable(menu.isAllowList()
                ? "screen.ars_arcane_matrix.starbuncle_hub.allow"
                : "screen.ars_arcane_matrix.starbuncle_hub.deny");
    }
    private Component matchModeLabel() {
        return Component.translatable("screen.ars_arcane_matrix.starbuncle_hub.match."
                + menu.getMatchMode().name().toLowerCase(java.util.Locale.ROOT));
    }
    private Component autoRecallLabel() {
        return Component.translatable(menu.isAutomaticRecall()
                ? "screen.ars_arcane_matrix.starbuncle_hub.auto_on"
                : "screen.ars_arcane_matrix.starbuncle_hub.auto_off");
    }
    private Component stuckTeleportLabel() {
        return Component.translatable(menu.isTeleportOnStuck()
                ? "screen.ars_arcane_matrix.starbuncle_hub.teleport_on"
                : "screen.ars_arcane_matrix.starbuncle_hub.teleport_off");
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xED181027);
        g.fill(leftPos + 5, topPos + 14, leftPos + 173, topPos + 142, 0xD02A1D42);
        g.fill(leftPos + 5, topPos + 150, leftPos + 173, topPos + 231, 0xD0201730);
        g.fill(leftPos + 176, topPos + 4, leftPos + 298, topPos + 232, 0xD02A1D42);
        for (int r=0;r<3;r++) for(int c=0;c<9;c++) drawSlot(g,leftPos+7+c*18,topPos+34+r*18);
        for (int r=0;r<2;r++) for(int c=0;c<9;c++) drawSlot(g,leftPos+7+c*18,topPos+100+r*18);
        for (int r=0;r<3;r++) for(int c=0;c<9;c++) drawSlot(g,leftPos+7+c*18,topPos+153+r*18);
        for(int c=0;c<9;c++) drawSlot(g,leftPos+7+c*18,topPos+211);
    }
    private static void drawSlot(GuiGraphics g,int x,int y){
        g.fill(x,y,x+18,y+18,0xFF080610); g.fill(x+1,y+1,x+17,y+17,0xE0201830);
        g.fill(x+1,y+1,x+17,y+2,0xFF604878); g.fill(x+1,y+16,x+17,y+17,0xFF100C18);
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        renderBackground(g,x,y,partial); super.render(g,x,y,partial); renderTooltip(g,x,y);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){
        g.drawString(font,title,8,5,0xE8D9FF,false);
        g.drawString(font, Component.translatable(
                "screen.ars_arcane_matrix.starbuncle_hub.throughput",
                menu.getUpgradeTier(), menu.getSharedThroughput()), 92, 5, 0xAEEBFF, false);
        g.drawString(font,Component.translatable(
                "screen.ars_arcane_matrix.starbuncle_hub.nearby",menu.getNearbyOwned()),8,18,0xCBBCE3,false);
        Component filterHeading = Component.translatable(
                "screen.ars_arcane_matrix.starbuncle_hub.filters");
        if (!menu.getRoutes().isEmpty()) {
            int index = Math.min(selectedRoute, menu.getRoutes().size() - 1);
            filterHeading = Component.translatable(
                    "screen.ars_arcane_matrix.starbuncle_hub.filters_for",
                    menu.getRoutes().get(index).name());
        }
        g.drawString(font,filterHeading,8,27,0xCBBCE3,false);
        g.drawString(font,Component.translatable(
                "screen.ars_arcane_matrix.starbuncle_hub.buffer"),8,92,0xCBBCE3,false);
        g.drawString(font,playerInventoryTitle,8,143,0xCBBCE3,false);
        renderRoutePanel(g);
    }

    private void renderRoutePanel(GuiGraphics g) {
        g.drawString(font, Component.translatable(
                "screen.ars_arcane_matrix.starbuncle_hub.routes"), 180, 6, 0xE8D9FF, false);
        List<StarbuncleLogisticsHubBlockEntity.RouteSnapshot> routes = menu.getRoutes();
        if (routes.isEmpty()) {
            g.drawString(font, Component.translatable(
                    "screen.ars_arcane_matrix.starbuncle_hub.no_routes"), 181, 38, 0xCBBCE3, false);
            return;
        }
        selectedRoute = Math.min(selectedRoute, routes.size() - 1);
        var route = routes.get(selectedRoute);
        g.drawCenteredString(font, fit(route.name(), 68), 238, 19, 0xFFFFFF);
        int y = 38;
        y = renderTargets(g, Component.translatable(
                "screen.ars_arcane_matrix.starbuncle_hub.inputs"), route.inputs(), y);
        renderTargets(g, Component.translatable(
                "screen.ars_arcane_matrix.starbuncle_hub.outputs"), route.outputs(), y + 4);
    }

    private int renderTargets(GuiGraphics g, Component heading,
                              List<StarbuncleLogisticsHubBlockEntity.RouteTarget> targets, int y) {
        g.drawString(font, heading, 181, y, 0xD8B8FF, false);
        y += 10;
        if (targets.isEmpty()) {
            g.drawString(font, Component.literal("-"), 184, y, 0xFF8C8C, false);
            return y + 10;
        }
        int shown = Math.min(4, targets.size());
        for (int index = 0; index < shown; index++) {
            var target = targets.get(index);
            String line = target.blockName() + " " + target.pos().getX() + ","
                    + target.pos().getY() + "," + target.pos().getZ();
            g.drawString(font, fit(line, 112), 184, y, 0xCBBCE3, false);
            y += 10;
        }
        if (targets.size() > shown) {
            g.drawString(font, Component.literal("+" + (targets.size() - shown)), 184, y, 0x9F8AB8, false);
            y += 10;
        }
        return y;
    }

    private String fit(String value, int width) { return font.plainSubstrByWidth(value, width); }
}
