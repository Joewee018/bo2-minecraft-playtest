package local.bo2beta.mixin;
import local.bo2beta.*;
import net.minecraft.client.Camera;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Camera.class)
public abstract class PeekCamera {
    @Shadow protected abstract void setPosition(Vec3 position);
    @Inject(method="setup",at=@At("TAIL"))
    private void peek(BlockGetter level,Entity entity,boolean thirdPerson,boolean reverse,float delta,CallbackInfo ci){
        if(thirdPerson || !(entity instanceof Player p) || !(p.getMainHandItem().getItem() instanceof Beta.Gun))return;
        Vec3 position=((Camera)(Object)this).getPosition();float lean=CornerPeek.sample(delta);
        setPosition(position.add(CornerPeek.offset(p,position,lean)));
        ((Camera)(Object)this).rotation().rotateZ((float)Math.toRadians(-12*CornerPeek.effective(p,position,lean)));
    }
}
