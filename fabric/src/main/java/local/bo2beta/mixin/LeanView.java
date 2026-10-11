package local.bo2beta.mixin;
import local.bo2beta.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public abstract class LeanView {
    @Inject(method="renderLevel",at=@At(value="INVOKE",target="Lnet/minecraft/client/Camera;getXRot()F"))
    private void roll(float delta,long time,PoseStack pose,CallbackInfo ci){
        var c=Minecraft.getInstance();if(c.player==null || !c.options.getCameraType().isFirstPerson() || !(c.player.getMainHandItem().getItem() instanceof Beta.Gun))return;
        float lean=CornerPeek.effective(c.player,c.player.getEyePosition(),CornerPeek.sample(delta));pose.mulPose(Axis.ZP.rotationDegrees(12*lean));
    }
}
