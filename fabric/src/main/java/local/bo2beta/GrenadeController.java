package local.bo2beta;

import com.google.gson.*;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;

/** One server-owned cook state per player; the Rust companion authorizes timing and release. */
public final class GrenadeController {
    public static final ResourceKey<DamageType> VISUAL_BLAST=ResourceKey.create(Registries.DAMAGE_TYPE,new ResourceLocation("bo2beta","visual_blast"));
    private static final Map<UUID,Cook> cooks=new HashMap<>();
    public static JsonObject spec;
    private static Bridge bridge;
    private static long generation;
    private static final class Cook {boolean held=true,busy;int lastInput,lastStep;long sequence;boolean reserved,pinPlayed;ItemStack stack;}
    public static void start(Bridge connection){
        bridge=connection;cooks.clear();generation++;
        try(var reader=new java.io.InputStreamReader(Objects.requireNonNull(GrenadeController.class.getResourceAsStream("/data/bo2beta/grenade.json")),java.nio.charset.StandardCharsets.UTF_8)){
            spec=JsonParser.parseReader(reader).getAsJsonObject();
        }catch(java.io.IOException error){throw new IllegalStateException(error);}
        var reset=new JsonObject();reset.addProperty("op","grenade_reset");bridge.request(reset);
    }
    public static void stop(){generation++;cooks.clear();bridge=null;}
    public static boolean active(ServerPlayer player){return cooks.containsKey(player.getUUID());}
    public static void hold(ServerPlayer player,boolean held){var c=cooks.get(player.getUUID());if(c!=null){c.held=held;c.lastInput=player.server.getTickCount();}}
    public static void cancel(ServerPlayer player){
        cooks.remove(player.getUUID());var packet=new JsonObject();packet.addProperty("grenade_cancelled",true);Beta.send(player,packet);if(bridge==null)return;
        var request=new JsonObject();request.addProperty("op","grenade_cancel");request.addProperty("player",player.getUUID().toString());bridge.request(request);
    }
    public static boolean begin(ServerPlayer player,ItemStack stack){
        if(bridge==null || !MatchDirector.playing(player) || active(player) || stack.isEmpty())return false;
        var c=new Cook();c.stack=stack;c.lastInput=c.lastStep=player.server.getTickCount();cooks.put(player.getUUID(),c);
        request(player,c,"grenade_begin",0);return true;
    }
    public static void tick(MinecraftServer server){
        for(var entry:new ArrayList<>(cooks.entrySet())){
            var player=server.getPlayerList().getPlayer(entry.getKey());var c=entry.getValue();
            if(player==null)continue;
            if(!player.isAlive()){cancel(player);continue;}
            if(server.getTickCount()-c.lastInput>5)c.held=false;
            if(c.busy)continue;
            int tick=server.getTickCount(),elapsed=Math.min(1000,Math.max(0,tick-c.lastStep)*50);c.lastStep=tick;
            request(player,c,"grenade_frame",elapsed);
        }
    }
    private static void request(ServerPlayer player,Cook c,String op,int elapsed){
        var request=new JsonObject();request.addProperty("op",op);request.addProperty("player",player.getUUID().toString());
        request.addProperty("held",c.held);request.addProperty("step_ms",elapsed);request.addProperty("sequence",++c.sequence);
        c.busy=true;long sequence=c.sequence,session=generation;
        bridge.request(request).whenComplete((response,error)->player.server.execute(()->{
            if(session!=generation || cooks.get(player.getUUID())!=c)return;c.busy=false;
            if(error!=null || !response.has("ok") || !response.get("ok").getAsBoolean()){
                cancel(player);var fail=new JsonObject();fail.addProperty("grenade_error","Rust grenade companion unavailable");Beta.send(player,fail);return;
            }
            if(!response.has("sequence") || response.get("sequence").getAsLong()!=sequence || response.get("protocol").getAsInt()!=1){cancel(player);return;}
            if(!c.reserved){
                if(c.stack.isEmpty() || c.stack.getItem()!=Beta.GRENADE){cancel(player);return;}
                if(!player.getAbilities().instabuild)c.stack.shrink(1);c.reserved=true;
            }
            var state=response.getAsJsonObject("grenade");Beta.send(player,response);
            if(!c.pinPlayed && state.get("elapsed_ms").getAsInt()>=275){c.pinPlayed=true;player.serverLevel().playSound(null,player.blockPosition(),Beta.GRENADE_PIN,net.minecraft.sounds.SoundSource.PLAYERS,.8f,1);}
            if(state.get("spawn").getAsBoolean()){
                var entity=new GrenadeEntity(Beta.GRENADE_ENTITY,player.serverLevel());entity.setOwner(player);
                entity.setPos(player.getEyePosition().add(player.getLookAngle().scale(.25)).add(0,-.12,0));
                entity.setRemaining(state.get("remaining_ms").getAsInt());
                Vec3 forward=player.getLookAngle(),right=new Vec3(-Math.cos(Math.toRadians(player.getYRot())),0,-Math.sin(Math.toRadians(player.getYRot())));
                Vec3 up=right.cross(forward).normalize();
                entity.setDeltaMovement(forward.scale(spec.get("speed_units").getAsDouble()*.0254/20)
                    .add(up.scale(spec.get("relative_up_units").getAsDouble()*.0254/20)));
                player.serverLevel().addFreshEntity(entity);cooks.remove(player.getUUID());player.getCooldowns().addCooldown(Beta.GRENADE,8);
            }else if(state.get("explode").getAsBoolean()){
                cooks.remove(player.getUUID());explode(player.serverLevel(),player,player.getEyePosition().add(0,-.15,0));
            }
        }));
    }
    public static void explode(Level level,Entity owner,Vec3 at){
        double radius=spec.get("blast_radius_units").getAsDouble()*.0254;
        DamageSource damage=level.damageSources().explosion(null,owner);
        // Native scalar falloff with Minecraft visibility; vanilla blast visuals and block destruction follow.
        for(var entity:level.getEntities((Entity)null,new AABB(at,at).inflate(radius),e->e instanceof LivingEntity && e.isAlive())){
            double distance=entity.getBoundingBox().getCenter().distanceTo(at);if(distance>radius)continue;
            double exposure=Explosion.getSeenPercent(at,entity);if(exposure<=0)continue;
            float strength=(float)((spec.get("inner_damage").getAsDouble()+(spec.get("outer_damage").getAsDouble()-spec.get("inner_damage").getAsDouble())*distance/radius)*exposure);
            entity.invulnerableTime=0;entity.hurt(damage,strength);
        }
        // This damage type is rejected by our ALLOW_DAMAGE hook, preventing a second TNT damage calculation.
        var visual=new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(VISUAL_BLAST),owner);
        level.explode(owner,visual,null,at.x,at.y,at.z,(float)(radius/2),false,owner instanceof net.minecraft.world.entity.LivingEntity living && MatchDirector.owns(living)?Level.ExplosionInteraction.NONE:Level.ExplosionInteraction.TNT);
    }
}
