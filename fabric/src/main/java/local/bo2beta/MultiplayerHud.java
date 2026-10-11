package local.bo2beta;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import java.util.*;

/** Original HUD artwork with real Minecraft/Rust state; full native layout/fonts remain unverified. */
public final class MultiplayerHud {
    private record Feed(String text,long tick){}
    private static final ArrayDeque<Feed> feed=new ArrayDeque<>();
    private static int kills;
    private static long hitTick=-100,startTick=-1;
    private static boolean hitPending;
    public static int frames,hitFrames,killEvents,feedFrames,compassFrames,ammoFrames;
    public static void reset(){feed.clear();kills=0;hitTick=-100;startTick=-1;hitPending=false;}
    public static void accept(JsonObject state){
        var c=Minecraft.getInstance();if(c.level==null)return;
        if(state.has("hit") && state.get("hit").getAsBoolean()){hitTick=c.level.getGameTime();hitPending=true;}
        if(state.has("kills"))kills=state.get("kills").getAsInt();
        if(state.has("kill") && state.get("kill").getAsBoolean()){
            killEvents++;
            feed.addLast(new Feed(state.get("killer").getAsString()+"  >  "+state.get("target").getAsString(),c.level.getGameTime()));
            while(feed.size()>4)feed.removeFirst();
        }
    }
    private static ResourceLocation art(String name){return new ResourceLocation("bo2beta","textures/hud/"+name+".png");}
    private static void texture(GuiGraphics g,String name,int x,int y,int w,int h,int tw,int th){
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();g.blit(art(name),x,y,w,h,0,0,tw,th,tw,th);
    }
    private static void frame(GuiGraphics g,String name,int x,int y,int w,int h){
        g.flush();RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ONE);
        g.blit(art(name),x,y,w,h,0,0,256,256,256,256);RenderSystem.defaultBlendFunc();
    }
    private static void label(GuiGraphics g,String text,int x,int y,int color){
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(1.6f,1.6f,1);
        g.drawString(Minecraft.getInstance().font,text,0,0,color,true);g.pose().popPose();
    }
    // Native HUDDigit slot metrics, with a temporary Minecraft font until original
    // foreground/background digit materials have been recovered.
    private static float digits(GuiGraphics g,String text,float right,boolean small,int color){
        float size=small?.75f:1,spacing=22*size,fontScale=3.5f*size;
        for(int i=text.length()-1;i>=0;i--){
            String digit=text.substring(i,i+1);
            float slotWidth=32*size,slotHeight=64*size;
            float x=right-slotWidth+(slotWidth-Minecraft.getInstance().font.width(digit)*fontScale)/2;
            float y=(small?HudLayout.AMMO_SMALL_TOP:HudLayout.AMMO_TOP)+(slotHeight-9*fontScale)/2;
            g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(fontScale,fontScale,1);
            g.drawString(Minecraft.getInstance().font,digit,0,0,color,true);g.pose().popPose();right-=spacing;
        }
        return right;
    }
    private static void compass(GuiGraphics g,float yaw){
        int x=HudLayout.MAP_X,y=HudLayout.COMPASS_Y,width=HudLayout.MAP_SIZE,height=HudLayout.COMPASS_HEIGHT;
        g.fill(x,y,x+width,y+height,0x40000000);
        g.fill(x,y,x+width,y+1,0x40ffffff);g.fill(x,y+height-1,x+width,y+height,0x40ffffff);
        float heading=net.minecraft.util.Mth.wrapDegrees(yaw+180);
        String[] names={"N","E","S","W"};
        for(int degree=0;degree<360;degree+=15){
            float distance=net.minecraft.util.Mth.wrapDegrees(degree-heading);
            int at=Math.round(x+width/2f+distance*width/90f);
            if(at<x+5 || at>x+width-5)continue;
            boolean major=degree%90==0;g.fill(at,y+height-(major?7:4),at+1,y+height-1,0xffaabcbc);
            if(major)g.drawCenteredString(Minecraft.getInstance().font,names[degree/90],at,y+4,0xffeafafa);
        }
        g.fill(x+width/2-1,y,x+width/2+2,y+3,0xffeafafa);compassFrames++;
    }
    public static void render(GuiGraphics g){
        var c=Minecraft.getInstance();if(c.player==null || c.level==null)return;
        long tick=c.level.getGameTime();if(startTick<0)startTick=tick;
        while(!feed.isEmpty() && tick-feed.peekFirst().tick>120)feed.removeFirst();
        float scale=c.getWindow().getGuiScaledHeight()/720f;int w=(int)(c.getWindow().getGuiScaledWidth()/scale);
        g.pose().pushPose();g.pose().scale(scale,scale,1);
        // 720-line safe-area anchors; original bitmap glyph/font matching is still pending.
        int right=HudLayout.ammoRight(w),base=HudLayout.AMMO_TOP;
        String loaded="--",reserve="--";
        var state=BetaClient.state;var item=c.player.getMainHandItem().getItem();
        if(item instanceof Beta.Gun gun){
            String name=Component.translatable("item.bo2beta."+gun.id).getString().toUpperCase(Locale.ROOT);
            int labelWidth=Math.round(c.font.width(name)*1.6f);
            label(g,name,right-labelWidth,base-22,0xffe6eeee);
            if(state!=null && state.has("weapon") && state.get("weapon").getAsString().equals(gun.model)){
                loaded=state.get("loaded").getAsString();reserve=state.get("reserve").getAsString();
                if(state.has("infinite_ammo") && state.get("infinite_ammo").getAsBoolean())reserve="INF";
                String prompt=state.get("reload").getAsFloat()>0?"RELOADING":state.get("loaded").getAsInt()==0?"R  RELOAD":"";
                if(!prompt.isEmpty())label(g,prompt,Math.round(w/2f-c.font.width(prompt)*.8f),450,0xffdbe9e9);
            }
            float digitRight=digits(g,reserve,right,true,0xffc4d0d0);
            digitRight=digits(g,"/",digitRight,true,0xffc4d0d0);
            digits(g,loaded,digitRight,false,loaded.equals("0")?0xffed7d66:0xffeef6f6);ammoFrames++;
        }
        int grenades=0;for(int i=0;i<c.player.getInventory().getContainerSize();i++){var stack=c.player.getInventory().getItem(i);if(stack.is(Beta.GRENADE))grenades+=stack.getCount();}
        // Native OffhandIcons: 24px images, 24px spacing, lethal anchor at AmmoArea.Right-40.
        int icons=Math.min(4,grenades),iconRight=w-34-28-40,iconBottom=720-20-7;
        for(int i=0;i<icons;i++){g.setColor(1,1,1,(i+1f)/icons);texture(g,"grenadeicon_32",iconRight-24*(i+1),iconBottom-24,24,24,32,32);}g.setColor(1,1,1,1);
        if(grenades>4)g.drawString(c.font,"+"+(grenades-4),iconRight+3,iconBottom-14,0xffe6eeee,true);
        var map=TerrainMinimap.texture();
        if(map!=null){RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();g.blit(map,HudLayout.MAP_X,HudLayout.MAP_Y,HudLayout.MAP_SIZE,HudLayout.MAP_SIZE,0,0,64,64,64,64);}
        frame(g,"menu_vis_bracket_minimap",HudLayout.MAP_X-8,HudLayout.MAP_Y-8,HudLayout.MAP_SIZE+16,HudLayout.MAP_SIZE+16);
        EntityRadar.render(g);
        // North-up live map. The center marker rotates with the actual player heading.
        g.pose().pushPose();g.pose().translate(HudLayout.mapX(c.player.getX()),HudLayout.mapY(c.player.getZ()),0);g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(c.player.getYRot()+180));
        g.fill(-2,-6,3,5,0xffeafafa);g.fill(-4,-3,5,0,0xffeafafa);g.pose().popPose();
        compass(g,c.player.getYRot());
        long seconds=Math.max(0,tick-startTick)/20;
        label(g,String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60),HudLayout.SAFE_LEFT+36,HudLayout.SAFE_BOTTOM-94,0xffd2e2e2);
        label(g,"KILLS  "+kills,34,648,0xffe6eeee);
        int y=588;for(var line:feed){label(g,line.text,34,y,0xffd8e4e4);y+=22;feedFrames++;}
        g.pose().popPose();
        if(hitPending){hitTick=tick;hitPending=false;}
        if(tick-hitTick<=5){
            int x=c.getWindow().getGuiScaledWidth()/2,yc=c.getWindow().getGuiScaledHeight()/2;
            for(int i=3;i<=7;i++){g.fill(x+i,yc+i,x+i+1,yc+i+1,0xfff1eeee);g.fill(x-i,yc+i,x-i+1,yc+i+1,0xfff1eeee);g.fill(x+i,yc-i,x+i+1,yc-i+1,0xfff1eeee);g.fill(x-i,yc-i,x-i+1,yc-i+1,0xfff1eeee);}hitFrames++;
        }
        frames++;
    }
}
