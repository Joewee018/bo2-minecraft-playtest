package local.bo2beta.mixin;
import local.bo2beta.Bo2Hud;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Close weapon surfaces need a smaller near plane in the separate hand pass. */
@Mixin(GameRenderer.class)
public abstract class ViewModelProjection {
    @Unique private boolean bo2beta$handPass;
    @Inject(method="renderItemInHand",at=@At("HEAD"))
    private void begin(PoseStack pose,Camera camera,float delta,CallbackInfo ci){bo2beta$handPass=true;}
    @Inject(method="renderItemInHand",at=@At("RETURN"))
    private void end(PoseStack pose,Camera camera,float delta,CallbackInfo ci){bo2beta$handPass=false;local.bo2beta.MuzzleEffects.endHandFrame();}
    @ModifyConstant(method="getProjectionMatrix",constant=@Constant(floatValue=.05f))
    private float weaponNear(float original){return bo2beta$handPass && Bo2Hud.active()?.001f:original;}
}
