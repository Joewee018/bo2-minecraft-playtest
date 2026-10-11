package local.bo2beta;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
/** Shared player/bot hitscan; native MP distance tables and pellet counts. */
public final class BulletCombat {
    public record Profile(float[] damage,double[] ranges,int pellets,float hipMin,float hipMax,float adsSpread,float head,float torso){}
    public record Hit(Entity entity,float damage,boolean killed){}
    public record Result(List<Hit> hits,BlockHitResult impact){}
    private static final Map<String,Profile> profiles=new HashMap<>();
    public static Profile profile(String id){
        if(profiles.isEmpty())try(var r=new InputStreamReader(Objects.requireNonNull(BulletCombat.class.getResourceAsStream("/data/bo2beta/combat.json")),StandardCharsets.UTF_8)){
            for(var entry:JsonParser.parseReader(r).getAsJsonObject().entrySet()){var p=entry.getValue().getAsJsonObject();float[] damage=new float[6];double[] ranges=new double[6];for(int i=0;i<6;i++){damage[i]=p.getAsJsonArray("damage").get(i).getAsFloat();ranges[i]=p.getAsJsonArray("ranges").get(i).getAsDouble();}profiles.put(entry.getKey(),new Profile(damage,ranges,p.get("pellets").getAsInt(),p.get("hip_min").getAsFloat(),p.get("hip_max").getAsFloat(),p.get("ads_spread").getAsFloat(),p.get("head_multiplier").getAsFloat(),p.get("torso_multiplier").getAsFloat()));}
        }catch(IOException e){throw new IllegalStateException(e);}
        return Objects.requireNonNull(profiles.get(id),id);
    }
    public static float damageAt(Profile p,double distance){
        int previous=0;if(distance<=p.ranges[0])return p.damage[0];
        for(int i=1;i<6;i++){if(p.ranges[i]<=p.ranges[previous] || p.damage[i]<=0)continue;
            if(distance<=p.ranges[i]){double t=(distance-p.ranges[previous])/(p.ranges[i]-p.ranges[previous]);return (float)(p.damage[previous]+(p.damage[i]-p.damage[previous])*t);}previous=i;}
        return p.damage[previous];
    }
    public static float location(Profile p,Entity e,Vec3 at){
        double fraction=(at.y-e.getY())/e.getBbHeight();return fraction>=.83?p.head:fraction>=.5?p.torso:.75f;
    }
    public static Vec3 spread(Vec3 aim,float degrees,RandomSource rng){
        aim=aim.normalize();if(degrees<=0)return aim;
        Vec3 right=aim.cross(new Vec3(0,1,0));if(right.lengthSqr()<1e-5)right=new Vec3(1,0,0);right=right.normalize();var up=right.cross(aim).normalize();
        double radius=Math.sqrt(rng.nextDouble())*Math.tan(Math.toRadians(degrees)),angle=rng.nextDouble()*Math.PI*2;
        return aim.add(right.scale(radius*Math.cos(angle))).add(up.scale(radius*Math.sin(angle))).normalize();
    }
    public static Result shoot(ServerLevel level,LivingEntity shooter,Beta.Gun gun,Vec3 from,Vec3 direction,float spread){
        var p=profile(gun.id);Map<Entity,Float> amounts=new LinkedHashMap<>();BlockHitResult impact=null;
        for(int pellet=0;pellet<p.pellets;pellet++){
            var ray=spread(direction,spread,shooter.getRandom());var end=from.add(ray.scale(128));
            var block=level.clip(new ClipContext(from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,shooter));if(block.getType()!=HitResult.Type.MISS)end=block.getLocation();
            Entity closest=null;Vec3 point=null;double nearest=from.distanceToSqr(end);
            for(var entity:level.getEntities(shooter,new AABB(from,end).inflate(.5),e->e.isAlive() && e.isPickable() && !e.isSpectator())){
                var at=entity.getBoundingBox().inflate(entity.getPickRadius()).clip(from,end);
                if(at.isPresent() && from.distanceToSqr(at.get())<=nearest){closest=entity;point=at.get();nearest=from.distanceToSqr(point);}
            }
            if(closest!=null)amounts.merge(closest,damageAt(p,Math.sqrt(nearest))*location(p,closest,point),Float::sum);
            else if(block.getType()==HitResult.Type.BLOCK)impact=block;
        }
        List<Hit> hits=new ArrayList<>();
        for(var item:amounts.entrySet()){var e=item.getKey();if(shooter instanceof Bo2Bot bot && e instanceof LivingEntity living && !bot.validTarget(living))continue;e.invulnerableTime=0;float before=e instanceof LivingEntity living?living.getHealth():0;
            var velocity=e.getDeltaMovement();if(e.hurt(Beta.bulletDamage(shooter),item.getValue())){e.setDeltaMovement(velocity);hits.add(new Hit(e,e instanceof LivingEntity living?before-living.getHealth():item.getValue(),!e.isAlive()));}}
        return new Result(hits,impact);
    }
}
