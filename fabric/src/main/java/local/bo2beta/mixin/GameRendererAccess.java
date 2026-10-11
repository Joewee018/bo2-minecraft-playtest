package local.bo2beta.mixin;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(GameRenderer.class)
public interface GameRendererAccess {
    @Invoker("getFov") double bo2beta$getFov(net.minecraft.client.Camera camera,float delta,boolean world);
    @Invoker("loadEffect") void bo2beta$loadEffect(ResourceLocation effect);
}
