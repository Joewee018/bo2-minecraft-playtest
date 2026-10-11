package local.bo2beta;
import com.google.gson.*;
import java.util.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;

public final class BotRenderer extends EntityRenderer<Bo2Bot> {
    private final Map<String,OriginalMesh> bodies=new HashMap<>();
    public static final Set<String> renderedCharacters=new HashSet<>();
    private JsonObject weapons,clips;
    public static int nativeFrames;
    private final Map<String,OriginalMesh> meshes=new HashMap<>();
    public static int frames;
    public BotRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=.4f;}
    public ResourceLocation getTextureLocation(Bo2Bot bot){return new ResourceLocation("bo2beta",BotCharacters.resource(bot.character()));}
    public void render(Bo2Bot bot,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        OriginalMesh body=bodies.computeIfAbsent(bot.character(),id->new OriginalMesh(getTextureLocation(bot)));
        if(weapons==null){try(var in=net.minecraft.client.Minecraft.getInstance().getResourceManager().getResourceOrThrow(new ResourceLocation("bo2beta","bots/catalog.json")).open()){weapons=JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}catch(java.io.IOException e){throw new IllegalStateException(e);}}
        boolean pistol=bot.weapon().startsWith("pistol_");
        boolean moving=bot.debugPose()==2 || (bot.debugPose()==0 && bot.walkAnimation.speed(partial)>.01f);
        String base=moving?(pistol?"pb_combatwalk_forward_loop_pistol":"pb_stand_shoot_walk_forward"):(pistol?"pb_stand_ads_pistol":"pb_stand_ads");
        if(clips==null)try(var in=net.minecraft.client.Minecraft.getInstance().getResourceManager().getResourceOrThrow(new ResourceLocation("bo2beta","bots/clips.json")).open()){clips=JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}catch(java.io.IOException e){throw new IllegalStateException(e);}
        var profile=clips.getAsJsonObject(base);float travel=profile.get("root_distance").getAsFloat();float distance=net.minecraft.util.Mth.lerp(partial,bot.previousWalkDistance,bot.nativeWalkDistance);
        float time=bot.debugPose()!=0?(bot.tickCount+partial)/20:moving && travel>0?distance/travel*body.duration(base):(bot.tickCount+partial)/20;
        if(bot.debugPose()==5)body.sampleNative("pb_death_flatonback",time%(body.duration("pb_death_flatonback")+2),false,false);
        else if(bot.deathTime>0)body.sampleNative("pb_death_flatonback",(bot.deathTime+partial)/20,false,false);
        else{
            body.sampleNative(base,time,true,false);
            if(bot.reloading() || bot.debugPose()==3){
                String reload=pistol?"pt_reload_stand_pistol":bot.weapon().startsWith("lmg_")?"pt_lmg_stand_reload":bot.weapon().startsWith("shotty_")?"pt_shot_stand_reload":"pt_reload_stand_rifle";
                body.sampleNative(reload,bot.debugPose()==3?(bot.tickCount+partial)/20:(bot.level().getGameTime()+partial-bot.reloadTime())/20,bot.debugPose()==3,true);
            }else if(bot.debugPose()==4 || (bot.fireTime()>0 && bot.level().getGameTime()+partial-bot.fireTime()<5)){body.sampleNative(pistol?"pt_stand_shoot_ads_pistol":"pt_stand_shoot_ads",bot.debugPose()==4?(bot.tickCount+partial)/20:(bot.level().getGameTime()+partial-bot.fireTime())/20,bot.debugPose()==4,true);}
        }
        nativeFrames++;
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(-(bot.debugPose()!=0?yaw:net.minecraft.util.Mth.rotLerp(partial,bot.yBodyRotO,bot.yBodyRot))-90));float scale=bot.character().equals("cdc")?.0254f:1;pose.scale(scale,scale,scale);
        body.draw(pose,buffers,light);
        if(weapons.has(bot.weapon())){
            OriginalMesh gun=meshes.computeIfAbsent(bot.weapon(),id->new OriginalMesh(new ResourceLocation(weapons.get(id).getAsString())));gun.sampleBind();pose.pushPose();var attachment=new org.joml.Matrix4f(body.attachment("tag_weapon_right"));var position=new org.joml.Vector3f();var orientation=new org.joml.Quaternionf();attachment.getTranslation(position);attachment.getUnnormalizedRotation(orientation);pose.translate(position.x,position.y,position.z);pose.mulPose(orientation);float gunScale=.0254f/scale;pose.scale(gunScale,gunScale,gunScale);gun.draw(pose,buffers,light);pose.popPose();
        }
        pose.popPose();frames++;renderedCharacters.add(bot.character());super.render(bot,yaw,partial,pose,buffers,light);
    }
}
