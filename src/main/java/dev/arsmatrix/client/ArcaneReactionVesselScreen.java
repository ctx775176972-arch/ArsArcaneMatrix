package dev.arsmatrix.client;

import dev.arsmatrix.blockentity.ArcaneReactionVesselBlockEntity;
import dev.arsmatrix.menu.ArcaneReactionVesselMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ArcaneReactionVesselScreen extends AbstractContainerScreen<ArcaneReactionVesselMenu> {
    public ArcaneReactionVesselScreen(ArcaneReactionVesselMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth=176; imageHeight=166; inventoryLabelY=73;
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x=leftPos,y=topPos; graphics.fill(x,y,x+imageWidth,y+imageHeight,0xFF21162E); graphics.fill(x+5,y+5,x+171,y+161,0xFF38264A);
        for(int[] slot:new int[][]{{44,35},{62,35},{116,35}}) { graphics.fill(x+slot[0]-1,y+slot[1]-1,x+slot[0]+17,y+slot[1]+17,0xFF17101E); graphics.fill(x+slot[0],y+slot[1],x+slot[0]+16,y+slot[1]+16,0xFF5A466A); }
        graphics.drawString(font,"+",x+84,y+39,0xD9B7FF,false); graphics.drawString(font,"→",x+93,y+39,0xD9B7FF,false);
        int width=88, filled=(int)(width*Math.min(1.0,menu.data(0)/(double)Math.max(1,menu.data(1))));
        graphics.fill(x+44,y+59,x+132,y+65,0xFF17101E); graphics.fill(x+44,y+59,x+44+filled,y+65,0xFFB45CFF);
        for(int row=0;row<4;row++) for(int col=0;col<9;col++){int sx=x+8+col*18,sy=y+84+row*18;graphics.fill(sx-1,sy-1,sx+17,sy+17,0xFF17101E);graphics.fill(sx,sy,sx+16,sy+16,0xFF5A466A);}
    }
    @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY){
        graphics.drawString(font,title,8,7,0xE5D5FF,false);
        graphics.drawString(font,Component.translatable("screen.ars_arcane_matrix.arcane_reaction_vessel.fluids",menu.data(2),menu.data(3)),8,68,0xD7C8E8,false);
        ArcaneReactionVesselBlockEntity.State[] states=ArcaneReactionVesselBlockEntity.State.values();
        graphics.drawString(font,Component.translatable("state.ars_arcane_matrix.arcane_reaction_vessel."+states[Math.floorMod(menu.data(6),states.length)].name().toLowerCase()),98,7,0xD7C8E8,false);
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick){renderBackground(graphics,mouseX,mouseY,partialTick);super.render(graphics,mouseX,mouseY,partialTick);renderTooltip(graphics,mouseX,mouseY);}
}
