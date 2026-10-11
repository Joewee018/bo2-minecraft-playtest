package local.bo2beta;
import java.util.*;
import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
public final class PlayerCharacterRenderer {
    private static final Map<String,OriginalMesh> bodies=new HashMap<>(),guns=new HashMap<>();
    private static JsonObject catalog;
    public static int frames;
    public static boolean render(AbstractClientPlayer player,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        String skin=PlayerCharacterClient.skin(player.getUUID());if(!BotCharacters.valid(skin))return false;
        var body=bodies.computeIfAbsent(skin,id->new OriginalMesh(new ResourceLocation("bo2beta",BotCharacters.resource(id))));
        String weapon=player.getMainHandItem().getItem() instanceof Beta.Gun gun?gun.id:null;
        boolean pistol=weapon!=null && weapon.startsWith("pistol_");boolean moving=player.walkAnimation.speed(partial)>.01f;
        String base=moving?(pistol?"pb_combatwalk_forward_loop_pistol":"pb_stand_shoot_walk_forward"):(pistol?"pb_stand_ads_pistol":"pb_stand_ads");
        float time=(player.tickCount+partial)/20;
        if(player.deathTime>0)body.sampleNative("pb_death_flatonback",(player.deathTime+partial)/20,false,false);
        else {body.sampleNative(base,time,true,false);
            var a=PlayerCharacterClient.animation(player.getUUID());
            if(a!=null && a.reloading && java.util.Objects.equals(weapon,a.weapon))body.sampleNative(pistol?"pt_reload_stand_pistol":weapon!=null && weapon.startsWith("lmg_")?"pt_lmg_stand_reload":weapon!=null && weapon.startsWith("shotty_")?"pt_shot_stand_reload":"pt_reload_stand_rifle",(player.level().getGameTime()+partial-a.reloadTick)/20,false,true);
            else if(a!=null && a.fireTick>0 && player.level().getGameTime()+partial-a.fireTick<5)body.sampleNative(pistol?"pt_stand_shoot_ads_pistol":"pt_stand_shoot_ads",(player.level().getGameTime()+partial-a.fireTick)/20,false,true);
        }
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(-net.minecraft.util.Mth.rotLerp(partial,player.yBodyRotO,player.yBodyRot)-90));float scale=skin.equals("cdc")?.0254f:1;pose.scale(scale,scale,scale);body.draw(pose,buffers,light);
        if(weapon!=null){
            if(catalog==null)try(var in=Minecraft.getInstance().getResourceManager().getResourceOrThrow(new ResourceLocation("bo2beta","bots/catalog.json")).open()){catalog=JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
            if(catalog.has(weapon)){var gun=guns.computeIfAbsent(weapon,id->new OriginalMesh(new ResourceLocation(catalog.get(id).getAsString())));gun.sampleBind();pose.pushPose();var a=new org.joml.Matrix4f(body.attachment("tag_weapon_right"));var pos=new org.joml.Vector3f();var rot=new org.joml.Quaternionf();a.getTranslation(pos);a.getUnnormalizedRotation(rot);pose.translate(pos.x,pos.y,pos.z);pose.mulPose(rot);float gs=.0254f/scale;pose.scale(gs,gs,gs);gun.draw(pose,buffers,light);pose.popPose();}
        }
        pose.popPose();
        var c=Minecraft.getInstance();if(player!=c.player && !player.isInvisible() && c.getEntityRenderDispatcher().distanceToSqr(player)<4096){
            pose.pushPose();pose.translate(0,2.1,0);pose.mulPose(c.getEntityRenderDispatcher().cameraOrientation());pose.scale(-.025f,-.025f,.025f);var name=player.getDisplayName();float x=-c.font.width(name)/2f;
            c.font.drawInBatch(name,x,0,0xFFFFFFFF,false,pose.last().pose(),buffers,net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,0x50000000,light);pose.popPose();
        }
        frames++;return true;
    }
}
