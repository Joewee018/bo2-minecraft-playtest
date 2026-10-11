package local.bo2beta.mixin;
import local.bo2beta.Bo2Hud;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ToastComponent.class)
public abstract class Bo2ToastMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void bo2beta$hideVanillaToasts(CallbackInfo callback){if(Bo2Hud.active())callback.cancel();}
}
