package local.bo2beta;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
/** Optional client aiming for this sandbox's bots. Normal server weapon rules still apply. */
public final class AimAssist {
    public static boolean enabled,autoFire,head=true;
    public static int fov=90,range=64;
    public static float smoothing=.35f;
    public static Bo2Bot target;
    public static boolean firing;
    public static boolean opponent(Minecraft c,Bo2Bot bot){
        var state=MatchClient.state;
        if(!state.has("roster"))return !bot.ally();
        if(!state.has("phase") || !state.get("phase").getAsString().equals("PLAYING"))return false;
        Integer ours=null,theirs=null;
        for(var row:state.getAsJsonArray("roster")){var r=row.getAsJsonObject();
            if(r.get("id").getAsString().equals(c.player.getUUID().toString()))ours=r.get("team").getAsInt();
            if(r.has("entity") && r.get("entity").getAsString().equals(bot.getUUID().toString()))theirs=r.get("team").getAsInt();
        }
        return ours!=null && theirs!=null && (!state.get("mode").getAsString().equals("TDM") || !ours.equals(theirs));
    }
    public static Vec3 point(Bo2Bot b){return head?b.getEyePosition():b.position().add(0,b.getBbHeight()*.55,0);}
    public static float[] angles(Vec3 from,Vec3 to){var d=to.subtract(from);return new float[]{(float)(Math.toDegrees(Math.atan2(d.z,d.x))-90),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)))};}
    public static double error(float yaw,float pitch,float[] aim){return Math.hypot(Mth.wrapDegrees(aim[0]-yaw),aim[1]-pitch);}
    public static void tick(Minecraft c){
        target=null;firing=false;
        if(!enabled || c.player==null || c.level==null || !c.player.isAlive() || c.screen!=null || !(c.player.getMainHandItem().getItem() instanceof Beta.Gun))return;
        var eye=c.player.getEyePosition();double best=fov*.5;
        for(var b:c.level.getEntitiesOfClass(Bo2Bot.class,c.player.getBoundingBox().inflate(range))){
            if(!b.isAlive() || b.isRemoved() || !opponent(c,b) || eye.distanceToSqr(point(b))>range*range)continue;
            var a=angles(eye,point(b));double distance=error(c.player.getYRot(),c.player.getXRot(),a);
            if(distance>best || c.level.clip(new ClipContext(eye,point(b),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,c.player)).getType()!=HitResult.Type.MISS)continue;
            best=distance;target=b;
        }
        if(target==null)return;
        var a=angles(eye,point(target));
        c.player.setYRot(c.player.getYRot()+Mth.wrapDegrees(a[0]-c.player.getYRot())*smoothing);
        c.player.setXRot(Mth.clamp(c.player.getXRot()+(a[1]-c.player.getXRot())*smoothing,-90,90));
        firing=autoFire && error(c.player.getYRot(),c.player.getXRot(),a)<1;
    }
}
