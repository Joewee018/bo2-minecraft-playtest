package local.bo2beta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
public final class Bo2Hud {
    public static int hiddenHotbar,restoredHotbar;
    public static boolean active(){
        var client=Minecraft.getInstance();
        return OriginalView.enabled && client.player!=null && (client.player.getMainHandItem().getItem() instanceof Beta.Gun || client.player.getMainHandItem().getItem()==Beta.GRENADE || OriginalGrenadeView.animating());
    }
    public static void render(GuiGraphics graphics){
        if(!active())return;var client=Minecraft.getInstance();
        if(client.player.getMainHandItem().getItem() instanceof Beta.Gun scoped && scoped.id.startsWith("sniper_") && OriginalView.aimAmount>.95f)ScopeScreen.render(graphics);
        MultiplayerHud.render(graphics);
        int width=client.getWindow().getGuiScaledWidth(),height=client.getWindow().getGuiScaledHeight();
        if(client.player.getMainHandItem().getItem() instanceof Beta.Gun gun){
            if(OriginalView.aimAmount<.9f){
                int x=width/2,y=height/2;
                graphics.fill(x-9,y,x-4,y+1,0xBBFFFFFF);graphics.fill(x+5,y,x+10,y+1,0xBBFFFFFF);
                graphics.fill(x,y-9,x+1,y-4,0xBBFFFFFF);graphics.fill(x,y+5,x+1,y+10,0xBBFFFFFF);
            }
        }
    }
}
