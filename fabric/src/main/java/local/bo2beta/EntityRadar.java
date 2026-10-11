package local.bo2beta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
public final class EntityRadar {
    public static int markers,lastVisible;
    public static void render(GuiGraphics g){
        var c=Minecraft.getInstance();lastVisible=0;
        if(!BetaSettings.entityRadar || c.level==null || c.player==null || TerrainMinimap.texture()==null)return;
        for(var entity:c.level.entitiesForRendering()){
            if(!(entity instanceof LivingEntity) || entity==c.player || !entity.isAlive() || entity.isSpectator())continue;
            double dx=entity.getX()-TerrainMinimap.centerX,dz=entity.getZ()-TerrainMinimap.centerZ;
            if(Math.abs(dx)>62 || Math.abs(dz)>62)continue;
            int x=(int)HudLayout.mapX(entity.getX()),y=(int)HudLayout.mapY(entity.getZ());
            int color=entity instanceof Enemy?0xffff514b:entity instanceof Player?(entity.isAlliedTo(c.player)?0xff6de49a:0xff7fdaff):0xffffcb67;
            g.fill(x-3,y-3,x+4,y+4,0xff101819);g.fill(x-2,y-2,x+3,y+3,color);
            double height=entity.getY()-c.player.getY();if(Math.abs(height)>4){int at=height>0?y-6:y+5;g.fill(x-1,at,x+2,at+2,color);}
            markers++;lastVisible++;
        }
    }
}
