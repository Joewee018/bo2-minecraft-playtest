package local.bo2beta.mixin;
import local.bo2beta.BetaSettings;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
@Mixin(SoundEngine.class)
public abstract class Bo2Volume {
    @ModifyExpressionValue(method="play",at=@At(value="INVOKE",target="Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
    private float newSoundVolume(float original,SoundInstance sound){
        if(!sound.getLocation().getNamespace().equals("bo2beta"))return original;
        BetaSettings.newSoundsMixed++;
        return bo2Volume(original);
    }
    private static float bo2Volume(float original){
        float gain=Math.max(0,Math.min(1,original))*BetaSettings.volume;
        BetaSettings.lastMixedVolume=gain;BetaSettings.soundMixed++;return gain;
    }
    @Inject(method="calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",at=@At("RETURN"),cancellable=true)
    private void volume(SoundInstance sound,CallbackInfoReturnable<Float> ci){
        if(sound.getLocation().getNamespace().equals("bo2beta")){
            // Other sound mods can return a gain above vanilla's [0,1] range.
            // Preserve the slider as an actual output ceiling for BO2 sounds.
            ci.setReturnValue(bo2Volume(ci.getReturnValueF()));
        }
    }
}
