package local.bo2beta;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.RenderType;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import java.util.ArrayDeque;
public final class BulletHoles {
    private record Mark(Vec3 point,Direction face,BlockPos block,BlockState state,long tick){}
    private static final ArrayDeque<Mark> marks=new ArrayDeque<>();
    public static int received,rendered;
    public static void accept(JsonObject j){
        var c=Minecraft.getInstance();if(c.level==null)return;
        var b=new BlockPos(j.get("bx").getAsInt(),j.get("by").getAsInt(),j.get("bz").getAsInt());
        if(marks.size()>=256)marks.removeFirst();
        marks.addLast(new Mark(new Vec3(j.get("x").getAsDouble(),j.get("y").getAsDouble(),j.get("z").getAsDouble()),Direction.from3DDataValue(j.get("face").getAsInt()),b,c.level.getBlockState(b),c.level.getGameTime()));received++;
    }
    public static void tick(){var c=Minecraft.getInstance();if(c.level==null){marks.clear();return;}
        marks.removeIf(m->c.level.getGameTime()-m.tick>1200 || c.level.getBlockState(m.block)!=m.state);
    }
    public static void init(){WorldRenderEvents.AFTER_ENTITIES.register(context->{
        var c=Minecraft.getInstance();if(c.level==null || context.consumers()==null)return;
        var out=context.consumers().getBuffer(RenderType.debugQuads());var pose=context.matrixStack();
        for(var m:marks){
            Vec3 normal=Vec3.atLowerCornerOf(m.face.getNormal());
            Vec3 u=m.face.getAxis()==Direction.Axis.Y?new Vec3(1,0,0):new Vec3(0,1,0);
            Vec3 v=normal.cross(u);Vec3 center=m.point.add(normal.scale(.003)).subtract(context.camera().getPosition());
            if(center.lengthSqr()>4096)continue;
            float[][] chips={{0,0,.027f,.027f},{.026f,0,.017f,.012f},{-.024f,.005f,.012f,.018f},{0,.027f,.011f,.016f},{.008f,-.023f,.016f,.011f}};
            for(var chip:chips)for(int i=0;i<4;i++){
                double x=chip[0]+(i==0||i==3?-chip[2]:chip[2]),y=chip[1]+(i<2?-chip[3]:chip[3]);
                Vec3 p=center.add(u.scale(x)).add(v.scale(y));
                out.vertex(pose.last().pose(),(float)p.x,(float)p.y,(float)p.z).color(18,15,12,210).endVertex();
            }
            rendered++;
        }
    });}
}
