package local.bo2beta.mixin;
import local.bo2beta.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(GameRenderer.class)
public abstract class Bo2AimMixin {
    @Inject(method="bobView",at=@At("HEAD"),cancellable=true)
    private void bo2MovementBob(com.mojang.blaze3d.vertex.PoseStack pose,float delta,org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback){
        if(Bo2Hud.active())callback.cancel();
    }
    @Inject(method="getFov",at=@At("RETURN"),cancellable=true)
    private void originalAimFov(Camera camera,float delta,boolean world,CallbackInfoReturnable<Double> callback){
        var c=Minecraft.getInstance();if(!world || !OriginalView.enabled || c.player==null || !(c.player.getMainHandItem().getItem() instanceof Beta.Gun gun))return;
        callback.setReturnValue(ViewMovement.aimedFov(gun.id,callback.getReturnValue(),OriginalView.aimAmount));
    }
}
