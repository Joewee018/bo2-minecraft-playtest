package local.bo2beta.mixin;
import local.bo2beta.Bo2Hud;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Gui.class)
public abstract class Bo2HudMixin {
    @Inject(method="renderHotbar",at=@At("HEAD"),cancellable=true)
    private void bo2beta$hotbar(CallbackInfo callback){
        if(Bo2Hud.active()){Bo2Hud.hiddenHotbar++;callback.cancel();}else Bo2Hud.restoredHotbar++;
    }
    @Inject(method={"renderSelectedItemName","renderPlayerHealth","renderExperienceBar","renderCrosshair","renderJumpMeter","renderVehicleHealth","renderEffects"},at=@At("HEAD"),cancellable=true)
    private void bo2beta$nativeHud(CallbackInfo callback){if(Bo2Hud.active())callback.cancel();}
}
