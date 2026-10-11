package local.bo2beta.mixin;
import local.bo2beta.Beta;
import local.bo2beta.OriginalView;
import local.bo2beta.OriginalGrenadeView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class OriginalViewMixin {
    @Inject(method="renderArmWithItem",at=@At("HEAD"),cancellable=true)
    private void originalView(AbstractClientPlayer player,float delta,float pitch,InteractionHand hand,float swing,ItemStack stack,float equip,PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo callback){
        if(hand==InteractionHand.OFF_HAND && OriginalView.enabled && (player.getMainHandItem().getItem() instanceof Beta.Gun || player.getMainHandItem().getItem()==Beta.GRENADE || OriginalGrenadeView.animating())){callback.cancel();return;}
        if(hand==InteractionHand.MAIN_HAND && (stack.getItem()==Beta.GRENADE || OriginalGrenadeView.animating()) && OriginalGrenadeView.render(pose,buffers,light,delta)){callback.cancel();return;}
        if(hand==InteractionHand.MAIN_HAND && stack.getItem() instanceof Beta.Gun gun && OriginalView.render(gun,pose,buffers,light))callback.cancel();
    }
}
