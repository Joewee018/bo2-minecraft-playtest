package local.bo2beta;

import com.google.gson.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;

public final class OriginalGrenadeView {
    public static JsonObject state;
    public static int pinFrames,throwFrames,worldFrames,itemFrames;
    private static long receivedTick;
    private static JsonObject bundle;
    private static OriginalMesh hands,weapon,projectile;
    public static void reset(){state=null;}
    public static void accept(JsonObject message){
        if(message.has("grenade_error")){reset();return;}
        if(!message.has("grenade"))return;
        state=message.getAsJsonObject("grenade");var client=Minecraft.getInstance();receivedTick=client.level==null?0:client.level.getGameTime();
    }
    public static boolean cooking(){return state!=null && state.get("active").getAsBoolean();}
    public static boolean animating(){return cooking() || state!=null && state.get("phase").getAsString().equals("throw") && elapsed(0)<365;}
    private static float elapsed(float partial){var level=Minecraft.getInstance().level;return state==null?0:state.get("phase_ms").getAsFloat()+(level==null?0:Math.max(0,level.getGameTime()-receivedTick+partial)*50);}
    private static void load(){
        if(bundle!=null)return;
        try(var input=Minecraft.getInstance().getResourceManager().getResourceOrThrow(new ResourceLocation("bo2beta","viewmodels/grenade.json")).open()){
            bundle=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(java.io.IOException error){throw new IllegalStateException("Original grenade resources missing",error);}
        hands=new OriginalMesh(new ResourceLocation(bundle.get("hands").getAsString()));
        weapon=new OriginalMesh(new ResourceLocation(bundle.get("weapon").getAsString()));
        projectile=new OriginalMesh(new ResourceLocation(bundle.get("projectile").getAsString()));projectile.sampleBind();
    }
    public static void drawProjectile(PoseStack pose,MultiBufferSource buffers,int light){load();projectile.draw(pose,buffers,light);worldFrames++;}
    public static boolean render(PoseStack pose,MultiBufferSource buffers,int light,float delta){
        if(!OriginalView.enabled)return false;load();
        String phase=state==null?"idle":state.get("phase").getAsString();int index=0;float seconds=0;boolean loop=true;
        if(animating()){
            if(phase.equals("throw")){index=2;throwFrames++;seconds=elapsed(delta)/365;}
            else {index=1;pinFrames++;seconds=Math.min(1,elapsed(delta)/550);}
            loop=false;
        }
        String clip=bundle.getAsJsonArray("clips").get(index).getAsString();
        if(!loop)seconds*=hands.duration(clip);
        else seconds=(float)(ViewMovement.seconds()%hands.duration(clip));
        hands.sample(clip,seconds,loop,null,0,false);weapon.sample(clip,seconds,loop,null,0,false);
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(90));pose.scale(.0254f,.0254f,.0254f);ViewMovement.apply("frag_grenade",0,pose);var visible=PlayerCharacterClient.hands(hands);if(visible!=hands)visible.followPose(hands);visible.draw(pose,buffers,light);
        var attachment=hands.attachment("tag_weapon");pose.mulPoseMatrix(attachment);pose.last().normal().mul(new Matrix3f(attachment));weapon.draw(pose,buffers,light);pose.popPose();return true;
    }
}
