package local.bo2beta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
/** Original DSR overlay; M82 artwork and native optical projection remain pending. */
public final class ScopeScreen {
    public static int frames;
    public static void render(GuiGraphics g){
        var window=Minecraft.getInstance().getWindow();int w=window.getGuiScaledWidth(),h=window.getGuiScaledHeight(),cx=w/2,cy=h/2,r=(int)(h*.47);
        var player=Minecraft.getInstance().player;
        if(player!=null && player.getMainHandItem().getItem() instanceof Beta.Gun gun && gun.id.equals("sniper_dsr50")){
            int left=(w-h)/2;g.fill(0,0,left,h,0xff000000);g.fill(left+h,0,w,h,0xff000000);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            g.blit(new net.minecraft.resources.ResourceLocation("bo2beta","textures/hud/scope_overlay_dsr50_720.png"),left,0,h,h,0,0,720,720,720,720);frames++;return;
        }
        for(int y=0;y<h;y++){
            int dy=y-cy;int half=Math.abs(dy)>=r?0:(int)Math.sqrt((double)r*r-(double)dy*dy);
            g.fill(0,y,cx-half,y+1,0xFF000000);g.fill(cx+half,y,w,y+1,0xFF000000);
        }
        g.fill(cx-r,cy,cx+r,cy+1,0xFF000000);g.fill(cx,cy-r,cx+1,cy+r,0xFF000000);
        g.fill(cx-r,cy-1,cx-r/3,cy+2,0xFF000000);g.fill(cx+r/3,cy-1,cx+r,cy+2,0xFF000000);
        g.fill(cx-1,cy+r/3,cx+2,cy+r,0xFF000000);frames++;
    }
}
