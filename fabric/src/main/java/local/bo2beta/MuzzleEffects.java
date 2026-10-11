package local.bo2beta;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
/** Minecraft particles are emitted only for shots accepted by the Rust companion. */
public final class MuzzleEffects {
    private static String model="";
    private static long count;
    public static int emitted;
    public static int firstPersonFrames;
    private static double flashUntil;
    private static String capturedShot="";
    private static String muzzleModel="";
    private static final org.joml.Vector3f muzzle=new org.joml.Vector3f();
    public static void reset(){model="";count=0;flashUntil=0;capturedShot="";muzzleModel="";}
    public static void capture(String model,org.joml.Matrix4f transform){muzzleModel=model;transform.getTranslation(muzzle);}
    public static void accept(JsonObject state){
        var c=Minecraft.getInstance();if(c.player==null || c.level==null || !state.has("weapon") || !state.has("total_shots"))return;
        String next=state.get("weapon").getAsString();long total=state.get("total_shots").getAsLong();
        int shots;
        if(!next.equals(model)){model=next;shots=state.has("shots")?state.get("shots").getAsInt():0;flashUntil=0;}
        else shots=(int)Math.min(32,Math.max(0,total-count));count=total;
        if(!(c.player.getMainHandItem().getItem() instanceof Beta.Gun gun) || !gun.model.equals(next))return;
        var look=c.player.getLookAngle();var position=c.player.getEyePosition().add(look.scale(.85)).add(0,-.12,0);
        if(muzzleModel.equals(next) && OriginalView.enabled){
            var camera=c.gameRenderer.getMainCamera();var renderer=(local.bo2beta.mixin.GameRendererAccess)c.gameRenderer;
            double world=renderer.bo2beta$getFov(camera,0,true),hand=renderer.bo2beta$getFov(camera,0,false);
            float ratio=(float)(Math.tan(Math.toRadians(world/2))/Math.tan(Math.toRadians(hand/2)));
            var local=new org.joml.Vector3f(muzzle.x*ratio,muzzle.y*ratio,muzzle.z).rotate(camera.rotation());
            position=camera.getPosition().add(local.x,local.y,local.z);
        }
        for(int i=0;i<shots;i++){
            c.level.addParticle(ParticleTypes.FLAME,true,position.x+look.x*.08,position.y+look.y*.08,position.z+look.z*.08,look.x*.015,look.y*.015,look.z*.015);
            c.level.addParticle(ParticleTypes.SMOKE,true,position.x,position.y,position.z,0,.01,0);emitted++;
        }
        if(shots>0)flashUntil=ViewMovement.seconds()+.15;
    }
    /** Depth-tested muzzle sprite: gun and hands occlude the particle naturally. */
    public static void drawFirstPerson(net.minecraft.client.renderer.MultiBufferSource buffers,boolean scoped){
        if(scoped || ViewMovement.seconds()>flashUntil || !model.equals(muzzleModel) || flashUntil==0)return;
        var pose=new com.mojang.blaze3d.vertex.PoseStack();pose.translate(muzzle.x,muzzle.y,muzzle.z+.012);
        var type=OriginalRenderTypes.flash(new net.minecraft.resources.ResourceLocation("minecraft","textures/particle/flame.png"));
        var out=buffers.getBuffer(type);
        float size=.09f;
        float[][] corners={{-size,-size,0,1},{size,-size,1,1},{size,size,1,0},{-size,size,0,0}};
        for(var v:corners)out.vertex(pose.last().pose(),v[0],v[1],0).color(255,255,255,255).uv(v[2],v[3])
            .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(15728880).normal(0,0,1).endVertex();
        if(buffers instanceof net.minecraft.client.renderer.MultiBufferSource.BufferSource source)source.endBatch(type);
        firstPersonFrames++;
    }
    public static void endHandFrame(){
        if(!Boolean.getBoolean("bo2beta.smoke") || ViewMovement.seconds()>flashUntil || flashUntil==0)return;
        var c=Minecraft.getInstance();if(!OriginalView.enabled || !c.options.getCameraType().isFirstPerson() || c.player==null || !(c.player.getMainHandItem().getItem() instanceof Beta.Gun gun) || gun.id.startsWith("sniper_"))return;
        String key=model+":"+count;if(key.equals(capturedShot))return;capturedShot=key;
        System.out.println("BETA MUZZLE FRAME: "+gun.id+" | local="+muzzle);
        net.minecraft.client.Screenshot.grab(c.gameDirectory,"muzzle-"+gun.id+".png",c.getMainRenderTarget(),message->{});
    }
}
