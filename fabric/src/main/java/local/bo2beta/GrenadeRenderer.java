package local.bo2beta;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
public final class GrenadeRenderer extends EntityRenderer<GrenadeEntity> {
    public GrenadeRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=.08f;}
    @Override public ResourceLocation getTextureLocation(GrenadeEntity entity){return new ResourceLocation("bo2beta","viewmodels/grenade-projectile.glb");}
    @Override public void render(GrenadeEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();pose.scale(.0254f,.0254f,.0254f);
        if(entity.getDeltaMovement().lengthSqr()>.001){pose.mulPose(Axis.XP.rotationDegrees((entity.tickCount+partial)*23));pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount+partial)*11));}
        OriginalGrenadeView.drawProjectile(pose,buffers,light);pose.popPose();super.render(entity,yaw,partial,pose,buffers,light);
    }
}
