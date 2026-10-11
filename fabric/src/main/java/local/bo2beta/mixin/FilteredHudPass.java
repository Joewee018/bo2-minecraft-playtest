package local.bo2beta.mixin;
import local.bo2beta.DebugFilters;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The selected BO2 filter runs once after the world, HUD and screen have drawn. */
@Mixin(GameRenderer.class)
public abstract class FilteredHudPass {
    @Shadow private boolean effectActive;
    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/PostChain;process(F)V"))
    private void deferOwnFilter(PostChain chain,float delta){if(DebugFilters.current()==DebugFilters.Preset.OFF)chain.process(delta);}
    @Inject(method="render",at=@At("TAIL"))
    private void filterWholeFrame(float delta,long time,boolean renderLevel,CallbackInfo ci){
        var c=Minecraft.getInstance();if(!effectActive || DebugFilters.current()==DebugFilters.Preset.OFF)return;
        var chain=c.gameRenderer.currentEffect();if(chain==null)return;
        RenderSystem.disableDepthTest();RenderSystem.resetTextureMatrix();chain.process(delta);c.getMainRenderTarget().bindWrite(true);
        DebugFilters.filteredHudFrames++;
    }
}
