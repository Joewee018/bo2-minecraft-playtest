package local.bo2beta;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;

/** Minecraft draws original content; accepted shots and reload stages come from Rust. */
public final class OriginalView {
    public static boolean enabled=true;
    public static int renderedFrames;
    public static int renderedReloadFrames,renderedFireFrames;
    public static float aimAmount;
    public static String lastRenderedGun="";
    private record Set(OriginalMesh weapon,OriginalMesh magazine,OriginalMesh scope,JsonObject profile){}
    private static final Map<String,Set> loaded=new HashMap<>();
    private static JsonObject catalog;
    private static OriginalMesh hands;
    private static String model="";
    private static long shots,receivedTick;
    private static double animationSeconds,previousSeconds,fireSeconds=-1;
    private static int previousAmmo,fireClip=1;
    private static boolean emptyReload;
    private static float previousReload,aim;
    private static JsonObject latest;
    public static void reset(){model="";shots=0;fireSeconds=-1;aim=0;previousReload=0;latest=null;previousSeconds=0;animationSeconds=0;AnimationAudio.reset();}
    public static void accept(JsonObject state){
        if(!state.has("weapon") || !state.has("loaded"))return;
        String current=state.get("weapon").getAsString();int ammo=state.get("loaded").getAsInt();float reload=state.get("reload").getAsFloat();
        long count=state.has("total_shots")?state.get("total_shots").getAsLong():shots+state.get("shots").getAsInt();
        if(!current.equals(model)){model=current;shots=count;fireSeconds=state.has("shots") && state.get("shots").getAsInt()>0?animationSeconds:-1;fireClip=ammo==0?4:1;aim=0;previousAmmo=ammo;previousReload=0;}
        else if(count>shots){fireSeconds=animationSeconds;fireClip=ammo==0?4:1;shots=count;}
        if(previousReload==0 && reload>0)emptyReload=previousAmmo==0;
        previousAmmo=ammo;previousReload=reload;latest=state;var level=Minecraft.getInstance().level;receivedTick=level==null?0:level.getGameTime();
    }
    private static Set load(Beta.Gun gun){
        if(catalog==null){
            try(var input=Minecraft.getInstance().getResourceManager().getResourceOrThrow(new ResourceLocation("bo2beta","viewmodels/catalog.json")).open()){
                catalog=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            }catch(IOException error){throw new IllegalStateException("Original viewmodel catalog unavailable",error);}
            hands=new OriginalMesh(new ResourceLocation(catalog.get("hands").getAsString()));
        }
        return loaded.computeIfAbsent(gun.id,id->{var p=catalog.getAsJsonObject("guns").getAsJsonObject(id);return new Set(new OriginalMesh(new ResourceLocation(p.get("weapon").getAsString())),p.has("magazine")?new OriginalMesh(new ResourceLocation(p.get("magazine").getAsString())):null,p.has("scope")?new OriginalMesh(new ResourceLocation(p.get("scope").getAsString())):null,p);});
    }
    public static boolean render(Beta.Gun gun,PoseStack pose,MultiBufferSource buffers,int light){return present(gun,pose,buffers,light,true);}
    public static void tickAudio(Beta.Gun gun){present(gun,null,null,0,false);}
    private static boolean present(Beta.Gun gun,PoseStack pose,MultiBufferSource buffers,int light,boolean draw){
        
        var set=load(gun);var profile=set.profile;var client=Minecraft.getInstance();
        double clock=ViewMovement.seconds();float delta=previousSeconds==0?0:(float)Math.max(0,Math.min(.1,clock-previousSeconds));
        if(client.isPaused())delta=0;previousSeconds=clock;animationSeconds+=delta;
        boolean valid=latest!=null && latest.get("weapon").getAsString().equals(gun.model);
        float remaining=valid?latest.get("reload").getAsFloat():0;boolean reloading=remaining>0;
        if(reloading && client.level!=null)remaining=Math.max(0,remaining-Math.max(0,client.level.getGameTime()-receivedTick+client.getFrameTime())*.05f);
        boolean aiming=!reloading && client.screen==null && client.options.keyUse.isDown();
        var aimProfile=profile.getAsJsonObject("aim");float interval=aimProfile.get(aiming?"enter":"exit").getAsFloat();
        aim=Math.max(0,Math.min(1,aim+(aiming?delta:-delta)/interval));aimAmount=aim;
        String clip;float time;boolean loop=false;
        if(reloading){
            int stage=latest.get("stage").getAsInt();float duration;
            if(stage>=8 && stage<=10 && profile.getAsJsonArray("shell_clips").size()==3){
                clip=profile.getAsJsonArray("shell_clips").get(stage-8).getAsString();duration=profile.get(stage==8?"shell_start":stage==10?"shell_end":"loaded").getAsFloat();
            }else {clip=profile.getAsJsonArray("clips").get(emptyReload?5:2).getAsString();duration=profile.get(emptyReload?"empty":"loaded").getAsFloat();}
            time=hands.duration(clip)*Math.max(0,Math.min(1,(duration-remaining)/duration));
        }else {
            int idle=valid && latest.get("loaded").getAsInt()==0?6:client.player!=null && client.player.isSprinting() && aim<.05f && client.player.onGround()?3:0;
            clip=profile.getAsJsonArray("clips").get(idle).getAsString();time=idle==3?(float)((ViewMovement.phase()%(Math.PI*2))/(Math.PI*2))*hands.duration(clip):(float)(animationSeconds%hands.duration(clip));loop=true;
            String fire=aim>.9?aimProfile.get("fire").getAsString():profile.getAsJsonArray("clips").get(fireClip).getAsString();
            float fired=(float)(animationSeconds-fireSeconds);
            if(fireSeconds>=0 && model.equals(gun.model) && fired<hands.duration(fire)){clip=fire;time=fired;loop=false;renderedFireFrames++;}
        }
        String cycle=reloading?"reload:"+latest.get("reload_cycle")+":"+latest.get("stage"):loop?"loop":"fire:"+shots;
        AnimationAudio.sample(gun.id+":"+clip+":"+cycle,clip,time,hands.duration(clip),loop);
        if(!draw || !enabled || !client.options.getCameraType().isFirstPerson())return false;
        boolean scoped=gun.id.startsWith("sniper_") && aim>.95f;
        hands.sample(clip,time,loop,reloading?null:aimProfile,aim,!aiming);set.weapon.sample(clip,time,loop,null,0,false);
        if(set.magazine!=null)set.magazine.sample(clip,time,loop,null,0,false);
        if(set.scope!=null){if(set.scope.hasClip(clip))set.scope.sample(clip,time,loop,null,0,false);else set.scope.sampleBind();}
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(12*CornerPeek.effective(client.player,client.player.getEyePosition(),CornerPeek.sample(client.getFrameTime()))));
        pose.mulPose(Axis.YP.rotationDegrees(90));pose.scale(.0254f,.0254f,.0254f);ViewMovement.apply(gun.id,aim,pose);
        if(!scoped){var visible=PlayerCharacterClient.hands(hands);if(visible!=hands)visible.followPose(hands);visible.draw(pose,buffers,light);if(visible!=hands)PlayerCharacterClient.handFrames++;}
        var attachment=hands.attachment("tag_weapon");pose.mulPoseMatrix(attachment);pose.last().normal().mul(new Matrix3f(attachment));
        MuzzleEffects.capture(gun.model,new org.joml.Matrix4f(pose.last().pose()).mul(set.weapon.attachment("tag_flash")));
        if(!scoped)set.weapon.draw(pose,buffers,light);
        if(!scoped && set.scope!=null){pose.pushPose();var offset=profile.getAsJsonArray("scope_offset");pose.translate(offset.get(0).getAsFloat(),offset.get(1).getAsFloat(),offset.get(2).getAsFloat());set.scope.draw(pose,buffers,light);pose.popPose();}
        if(!scoped && set.magazine!=null){var offset=profile.getAsJsonArray("mag_offset");pose.translate(offset.get(0).getAsFloat(),offset.get(1).getAsFloat(),offset.get(2).getAsFloat());set.magazine.draw(pose,buffers,light);}
        if(buffers instanceof MultiBufferSource.BufferSource source)source.endBatch();
        MuzzleEffects.drawFirstPerson(buffers,scoped);
        pose.popPose();renderedFrames++;if(reloading)renderedReloadFrames++;lastRenderedGun=gun.id;return true;
    }
}
