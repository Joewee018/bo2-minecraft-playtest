package local.bo2beta;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;

/** Original M67 content and scalar flight speed, with Minecraft surface collisions. */
public final class GrenadeEntity extends Projectile {
    private static final EntityDataAccessor<Integer> FUSE=SynchedEntityData.defineId(GrenadeEntity.class,EntityDataSerializers.INT);
    public int bounces;
    public GrenadeEntity(EntityType<? extends GrenadeEntity> type,Level level){super(type,level);}
    @Override protected void defineSynchedData(){entityData.define(FUSE,3500);}
    public int remaining(){return entityData.get(FUSE);}
    public void setRemaining(int milliseconds){entityData.set(FUSE,Math.max(1,milliseconds));}
    @Override protected void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putInt("Bo2FuseMs",remaining());}
    @Override protected void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);setRemaining(tag.getInt("Bo2FuseMs"));}
    @Override public void tick(){
        super.tick();
        if(!level().isClientSide){
            entityData.set(FUSE,Math.max(0,remaining()-50));
            if(remaining()==0){GrenadeController.explode(level(),getOwner(),position());discard();return;}
        }
        // Gravity and material restitution are bridge approximations; native comparison is pending.
        Vec3 velocity=getDeltaMovement().add(0,-20.32/400,0);
        Vec3 from=position(),to=from.add(velocity);
        var hit=level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        if(hit.getType()!=HitResult.Type.MISS){
            Vec3 normal=Vec3.atLowerCornerOf(hit.getDirection().getNormal());
            double speed=velocity.dot(normal);
            setPos(hit.getLocation().add(normal.scale(.035)));
            if(speed<0){
                Vec3 tangent=velocity.subtract(normal.scale(speed));
                velocity=tangent.scale(.72).subtract(normal.scale(speed*.45));bounces++;
                if(!level().isClientSide && Math.abs(speed)>.15)level().playSound(null,getX(),getY(),getZ(),Beta.GRENADE_BOUNCE,net.minecraft.sounds.SoundSource.PLAYERS,.65f,1);
                if(hit.getDirection()==net.minecraft.core.Direction.UP && Math.abs(velocity.y)<.08)velocity=new Vec3(velocity.x,0,velocity.z);
            }
        }else setPos(to);
        if(isInWater())velocity=velocity.scale(.8);
        setDeltaMovement(velocity);
        if(getY()<level().getMinBuildHeight()-64)discard();
    }
}
