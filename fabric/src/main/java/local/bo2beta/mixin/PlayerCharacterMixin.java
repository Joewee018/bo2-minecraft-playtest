package local.bo2beta.mixin;
import local.bo2beta.PlayerCharacterRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerRenderer.class)
public abstract class PlayerCharacterMixin {
    @Inject(method="render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",at=@At("HEAD"),cancellable=true)
    private void customCharacter(AbstractClientPlayer p,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo ci){if(PlayerCharacterRenderer.render(p,yaw,partial,pose,buffers,light))ci.cancel();}
}
