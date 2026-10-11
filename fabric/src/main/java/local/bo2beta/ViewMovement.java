package local.bo2beta;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;

/** Original weapon offsets with a tick-driven bridge gait. Native gait math remains unverified. */
public final class ViewMovement {
    private static JsonObject profiles;
    private static double previousX,previousZ,phase,previousPhase,clockSeconds;
    private static boolean initialized;
    private static float amount,strafe;
    private static final float[] position=new float[3],angles=new float[3];
    public static int walkingFrames,grenadeWalkingFrames;
    public static int jumpFrames,grenadeJumpFrames;
    public static void tick(){
        var c=Minecraft.getInstance();if(c.player==null){JumpMotion.reset();initialized=false;amount=strafe=0;phase=previousPhase=clockSeconds=0;return;}
        if(c.isPaused())return;
        var p=c.player;double x=p.getX(),z=p.getZ();
        JumpMotion.tick(p.onGround(),!p.isAlive()||p.getAbilities().flying||p.isPassenger()||p.isFallFlying(),p.getY(),p.getDeltaMovement().y);
        if(!initialized){previousX=x;previousZ=z;initialized=true;}
        double dx=x-previousX,dz=z-previousZ;previousX=x;previousZ=z;double step=Math.hypot(dx,dz);
        // Teleports, flight, knockback without movement input and menus cannot drive footsteps.
        boolean moving=c.screen==null && p.onGround() && !p.getAbilities().flying && step<.8 && (Math.abs(p.zza)+Math.abs(p.xxa)>.01);
        previousPhase=phase;
        // One complete walk cycle per 20 simulation ticks (one Minecraft second).
        if(moving)phase+=Math.PI*2/(p.isCrouching()?24:p.isSprinting()?16:20);
        float target=moving?(float)Math.min(1,step/.215):0;
        amount+=(target-amount)*.35f;strafe+=(moving?p.xxa-strafe:-strafe)*.35f;
    }
    private static JsonObject profile(String id){
        if(profiles==null)try(var input=Minecraft.getInstance().getResourceManager().getResourceOrThrow(new net.minecraft.resources.ResourceLocation("bo2beta","viewmodels/movement.json")).open()){
            profiles=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(IOException e){throw new IllegalStateException(e);}
        return profiles.getAsJsonObject(id);
    }
    public static void apply(String id,float aim,PoseStack pose){
        var p=profile(id);var player=Minecraft.getInstance().player;
        float partial=Minecraft.getInstance().getFrameTime();double interpolated=previousPhase+(phase-previousPhase)*partial;
        float bob=(float)Math.sin(interpolated),doubleBob=(float)Math.sin(interpolated*2);
        float weight=amount*(1-aim*(1-p.getAsJsonArray("ads_bob").get(0).getAsFloat()));
        var move=p.getAsJsonArray(player.isCrouching()?"crouch_move":"stand_move");var rot=p.getAsJsonArray(player.isCrouching()?"crouch_rot":"stand_rot");
        var sm=p.getAsJsonArray("strafe_move");var sr=p.getAsJsonArray("strafe_rot");
        for(int i=0;i<3;i++){position[i]=weight*move.get(i).getAsFloat()*(i==2?doubleBob:bob)+strafe*(1-aim)*sm.get(i).getAsFloat();angles[i]=weight*rot.get(i).getAsFloat()*(i==0?doubleBob:bob)+strafe*(1-aim)*sr.get(i).getAsFloat();}
        pose.translate(position[0],position[2],-position[1]);
        pose.mulPose(Axis.YP.rotationDegrees(angles[1]));pose.mulPose(Axis.ZP.rotationDegrees(-angles[0]));pose.mulPose(Axis.XP.rotationDegrees(angles[2]));
        float jump=JumpMotion.dip(partial),jumpWeight=1-.75f*aim;
        pose.translate(0,-jump*jumpWeight,0);
        pose.mulPose(Axis.XP.rotationDegrees(JumpMotion.pitch(partial)*jumpWeight));
        if(Math.abs(jump)>.01){jumpFrames++;if(id.equals("frag_grenade"))grenadeJumpFrames++;}
        if(amount>.1){walkingFrames++;if(id.equals("frag_grenade"))grenadeWalkingFrames++;}
    }
    public static float amount(){return amount;}
    public static double phase(){return phase;}
    public static double seconds(){var c=Minecraft.getInstance();if(c.level==null)return 0;if(!c.isPaused())clockSeconds=(c.level.getGameTime()+c.getFrameTime())*.05;return clockSeconds;}
    public static double aimedFov(String id,double hip,float amount){
        var p=profile(id);if(!p.has("ads_fov"))return hip;
        // FOV numbers are native WeaponVariant values; projection basis comparison remains pending.
        double target=p.get("ads_fov").getAsDouble();return hip+(target-hip)*amount;
    }
}
