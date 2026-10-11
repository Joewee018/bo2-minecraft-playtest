package local.bo2beta;

import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraft.sounds.SoundSource;

/** Minecraft server AI with Rust-authorized BO2 weapons. Native BO2 AI is not emulated. */
public final class Bo2Bot extends Monster {
    private static final EntityDataAccessor<String> WEAPON=SynchedEntityData.defineId(Bo2Bot.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> ALLY=SynchedEntityData.defineId(Bo2Bot.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RELOAD=SynchedEntityData.defineId(Bo2Bot.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SHOT=SynchedEntityData.defineId(Bo2Bot.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> RELOAD_TIME=SynchedEntityData.defineId(Bo2Bot.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> FIRE_TIME=SynchedEntityData.defineId(Bo2Bot.class,EntityDataSerializers.LONG);
    public float nativeWalkDistance,previousWalkDistance;
    private double nativeLastX,nativeLastZ;private boolean nativePositionInitialized;
    public long reloadTime(){return entityData.get(RELOAD_TIME);}
    public long fireTime(){return entityData.get(FIRE_TIME);}
    private static final EntityDataAccessor<String> CHARACTER=SynchedEntityData.defineId(Bo2Bot.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DEBUG_POSE=SynchedEntityData.defineId(Bo2Bot.class,EntityDataSerializers.INT);
    public String character(){return entityData.get(CHARACTER);}
    public int debugPose(){return entityData.get(DEBUG_POSE);}
    public void character(String id,int pose){if(!BotCharacters.valid(id) || pose<0 || pose>5)throw new IllegalArgumentException("Invalid bot character/pose");entityData.set(CHARACTER,id);entityData.set(DEBUG_POSE,pose);setNoAi(pose!=0);setCustomNameVisible(pose!=0);setCustomName(Component.literal("BO2 "+(ally()?"Ally":"Enemy")+" ("+BotCharacters.NAMES.get(id)+(pose==0?"":" / "+new String[]{"gameplay","idle","walk","reload","fire","death"}[pose])+")"));}
    private UUID owner;
    public UUID matchEntrant;
    private final BotAudio audio=new BotAudio();
    private boolean busy;
    private long sequence;
    private int loaded=1,reserve=1;
    private BotDifficulty difficulty=BotDifficulty.REGULAR;
    private UUID seenTarget;private Vec3 lastSeen;private long seenAt,reactAt,nextFireAt,lastStep=-1,nextRoute;
    public BotDifficulty difficulty(){return difficulty;}
    public void difficulty(BotDifficulty value){difficulty=Objects.requireNonNull(value);seenTarget=null;lastSeen=null;reactAt=nextFireAt=nextRoute=0;setTarget(null);navigation.stop();}
    public int acceptedShots,reloads;
    public int firstShotTick=-1,lastShotTick=-1,minShotGap=Integer.MAX_VALUE;
    public Bo2Bot(EntityType<? extends Monster> type,Level level){super(type,level);setPersistenceRequired();setCanPickUpLoot(false);}
    public static AttributeSupplier.Builder attributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,CombatRules.HEALTH).add(Attributes.MOVEMENT_SPEED,.28).add(Attributes.FOLLOW_RANGE,40).add(Attributes.ATTACK_DAMAGE,2);}
    protected void defineSynchedData(){super.defineSynchedData();entityData.define(CHARACTER,"cdc");entityData.define(DEBUG_POSE,0);entityData.define(WEAPON,"ar_ak47");entityData.define(ALLY,false);entityData.define(RELOAD,false);entityData.define(SHOT,0);entityData.define(RELOAD_TIME,0L);entityData.define(FIRE_TIME,0L);}
    protected void registerGoals(){goalSelector.addGoal(0,new net.minecraft.world.entity.ai.goal.FloatGoal(this));goalSelector.addGoal(5,new net.minecraft.world.entity.ai.goal.RandomStrollGoal(this,.7){public boolean canUse(){return matchEntrant==null && getTarget()==null && super.canUse();}});goalSelector.addGoal(6,new net.minecraft.world.entity.ai.goal.LookAtPlayerGoal(this,Player.class,8));}
    public String weapon(){return entityData.get(WEAPON);}
    public boolean ally(){return entityData.get(ALLY);}
    public boolean reloading(){return entityData.get(RELOAD);}
    public int shotSerial(){return entityData.get(SHOT);}
    public void configure(String gun,boolean ally,UUID owner){if(!Beta.GUNS.containsKey(gun))throw new IllegalArgumentException("Unknown BO2 weapon");sequence++;busy=false;lastStep=-1;nextFireAt=level().getGameTime();entityData.set(WEAPON,gun);entityData.set(ALLY,ally);this.owner=owner;loaded=1;reserve=1;entityData.set(RELOAD,false);setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Beta.GUNS.get(gun)));setDropChance(EquipmentSlot.MAINHAND,0);setDropChance(EquipmentSlot.OFFHAND,0);if(matchEntrant==null)setCustomName(Component.literal(ally?"BO2 Ally (CDC)":"BO2 Enemy (CDC)"));}
    public boolean validTarget(LivingEntity e){
        if(matchEntrant!=null)return MatchDirector.target(this,e);
        if(e==this || !e.isAlive() || e.isSpectator() || isAlliedTo(e))return false;
        if(e instanceof Player p)return !ally() && !p.isCreative() && !p.isSpectator();
        if(e instanceof Bo2Bot b)return ally()!=b.ally();
        return ally() && e instanceof net.minecraft.world.entity.monster.Enemy;
    }
    protected boolean shouldDespawnInPeaceful(){return false;}
    protected boolean isSunBurnTick(){return false;}
    public boolean removeWhenFarAway(double distance){return false;}
    public void tick(){
        previousWalkDistance=nativeWalkDistance;super.tick();
        if(nativePositionInitialized)nativeWalkDistance+=Math.min(.5f,(float)Math.hypot(getX()-nativeLastX,getZ()-nativeLastZ));nativeLastX=getX();nativeLastZ=getZ();nativePositionInitialized=true;if(!level().isClientSide && matchEntrant!=null && !MatchDirector.owns(this)){discard();return;}if(level().isClientSide || !isAlive() || isNoAi())return;
        if(matchEntrant!=null && !MatchDirector.owns(this)){discard();return;}
        var server=(ServerLevel)level();audio.tick(this);
        if(matchEntrant!=null && loaded==0 && reserve==0 && !busy && Beta.botBridge!=null){
            var request=new JsonObject();request.addProperty("op","player_reset");request.addProperty("player",getUUID().toString());Beta.botBridge.request(request);configure(weapon(),ally(),owner);
        }
        long now=level().getGameTime();
        var target=validTargetOrNull(getTarget());boolean visible=target!=null && hasLineOfSight(target);
        if(target==null || !visible && now-seenAt>difficulty.memoryTicks){setTarget(null);target=null;lastSeen=null;seenTarget=null;}
        if(target==null && tickCount%5==0){
            var targets=server.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(matchEntrant==null?32:96),this::validTarget);targets.sort(Comparator.comparingDouble(this::distanceToSqr));
            for(var candidate:targets){if(distanceToSqr(candidate)>=4096)continue;var direction=candidate.getEyePosition().subtract(getEyePosition()).normalize();
                boolean threat=candidate==getLastHurtByMob();if(!hasLineOfSight(candidate) || !threat && getLookAngle().dot(direction)<Math.cos(Math.toRadians(difficulty.fov*.5)))continue;
                target=candidate;setTarget(target);visible=true;break;}
        }
        if(target!=null && visible){
            if(!target.getUUID().equals(seenTarget)){seenTarget=target.getUUID();reactAt=now+(difficulty.reactionMin+random.nextInt(difficulty.reactionMax-difficulty.reactionMin+1)+49)/50;}
            seenAt=now;lastSeen=target.position().add(0,target.getBbHeight()*.68,0);
        }
        if(lastSeen!=null){var angles=AimAngles.of(getEyePosition(),lastSeen);setYRot(getYRot()+net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(angles[0]-getYRot()),-difficulty.turnDegrees,difficulty.turnDegrees));setXRot(getXRot()+net.minecraft.util.Mth.clamp(angles[1]-getXRot(),-difficulty.turnDegrees,difficulty.turnDegrees));yBodyRot=getYRot();setYHeadRot(getYRot());}
        else if(navigation.isDone())setYRot(getYRot()+2);
        if(now>=nextRoute){nextRoute=now+20;
            if(lastSeen!=null){
                boolean cover=difficulty.ordinal()>=2 && (reloading() || getHealth()<getMaxHealth()*.35f);
                var route=cover?MatchDirector.route(this,lastSeen,true):null;
                if(route!=null)navigation.moveTo(route.x,route.y,route.z,1);
                else if(!visible || position().distanceToSqr(lastSeen)>100)navigation.moveTo(lastSeen.x,lastSeen.y,lastSeen.z,1);
                else if(random.nextFloat()<difficulty.strafeChance){var side=lastSeen.subtract(position()).cross(new Vec3(0,1,0)).normalize().scale(random.nextBoolean()?3:-3);var at=position().add(side);
                    if(matchEntrant==null || MatchDirector.safe(server,this,at))navigation.moveTo(at.x,at.y,at.z,.7);}
                else navigation.stop();
            }else if(matchEntrant!=null && navigation.isDone()){var route=MatchDirector.route(this,null,false);if(route!=null)navigation.moveTo(route.x,route.y,route.z,.7);}
            else if(ally() && owner!=null){var player=server.getPlayerByUUID(owner);if(player!=null && distanceToSqr(player)>36)navigation.moveTo(player,1);}
        }
        boolean aligned=lastSeen!=null && getLookAngle().dot(lastSeen.subtract(getEyePosition()).normalize())>Math.cos(Math.toRadians(8));
        boolean attack=target!=null && visible && validTarget(target) && distanceToSqr(target)<4096 && now>=reactAt && now>=nextFireAt && aligned;
        if(tickCount%2!=0 || busy || Beta.botBridge==null || !attack && !reloading() && loaded>0)return;
        var gun=Beta.GUNS.get(weapon());if(gun==null)return;
        boolean reload=loaded==0 && reserve>0 && !reloading();
        var request=new JsonObject();request.addProperty("op",reload?"reload":"frame");request.addProperty("player",getUUID().toString());request.addProperty("weapon",gun.model);request.addProperty("held",attack);request.addProperty("pressed",attack);request.addProperty("infinite_ammo",false);request.addProperty("bot_fire",attack);request.addProperty("weapon_step_ms",lastStep<0?50:Math.min(250,Math.max(0,(now-lastStep)*50)));lastStep=now;request.addProperty("sequence",++sequence);
        long expected=sequence;busy=true;
        Beta.botBridge.request(request).whenComplete((reply,error)->server.getServer().execute(()->{
            busy=false;if(isRemoved() || !isAlive() || expected!=sequence || error!=null || reply==null || !reply.has("ok") || !reply.get("ok").getAsBoolean())return;
            if(!reply.has("sequence") || reply.get("sequence").getAsLong()!=expected || !reply.has("weapon") || !reply.get("weapon").getAsString().equals(gun.model))return;
            loaded=reply.get("loaded").getAsInt();reserve=reply.get("reserve").getAsInt();boolean loading=reply.get("reload").getAsFloat()>0;if(loading && !reloading()){reloads++;entityData.set(RELOAD_TIME,level().getGameTime());audio.begin(this);}entityData.set(RELOAD,loading);
            for(int i=0;i<Math.min(32,reply.get("shots").getAsInt());i++)if(weapon().equals(gun.id) && getTarget()!=null && validTarget(getTarget()) && hasLineOfSight(getTarget())){nextFireAt=level().getGameTime()+difficulty.shotTicks;fire(server,gun,getTarget());}
        }));
    }
    private LivingEntity validTargetOrNull(LivingEntity e){return e!=null && validTarget(e) && distanceToSqr(e)<4096?e:null;}
    private void fire(ServerLevel level,Beta.Gun gun,LivingEntity target){
        Vec3 from=getEyePosition(),direction=getLookAngle();
        var p=BulletCombat.profile(gun.id);BulletCombat.shoot(level,this,gun,from,direction,p.adsSpread()+difficulty.aimError);
        if(firstShotTick<0)firstShotTick=tickCount;if(lastShotTick>=0)minShotGap=Math.min(minShotGap,tickCount-lastShotTick);lastShotTick=tickCount;
        acceptedShots++;entityData.set(SHOT,shotSerial()+1);entityData.set(FIRE_TIME,level.getGameTime());
        Vec3 muzzle=from.add(direction.scale(.75));level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,muzzle.x,muzzle.y,muzzle.z,3,.03,.03,.03,.01);level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,muzzle.x,muzzle.y,muzzle.z,1,.02,.02,.02,.01);
        level.playSound(null,getX(),getEyeY(),getZ(),gun.fireSound,SoundSource.PLAYERS,.8f,1);
    }
    @Override public void remove(RemovalReason reason){
        super.remove(reason);
        if(!level().isClientSide && reason.shouldDestroy() && Beta.botBridge!=null){var request=new JsonObject();request.addProperty("op","player_reset");request.addProperty("player",getUUID().toString());Beta.botBridge.request(request);}
    }
    public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putString("Bo2Difficulty",difficulty.name());tag.putString("Bo2Weapon",weapon());tag.putString("Bo2Character",character());tag.putInt("Bo2DebugPose",debugPose());tag.putBoolean("Bo2Ally",ally());if(matchEntrant!=null)tag.putUUID("Bo2MatchEntrant",matchEntrant);if(owner!=null)tag.putUUID("Bo2Owner",owner);}
    public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);CombatRules.prepare(this);try{difficulty(BotDifficulty.valueOf(tag.getString("Bo2Difficulty")));}catch(IllegalArgumentException ignored){difficulty(BotDifficulty.REGULAR);}matchEntrant=tag.hasUUID("Bo2MatchEntrant")?tag.getUUID("Bo2MatchEntrant"):null;String skin=tag.getString("Bo2Character");String gun=tag.getString("Bo2Weapon");configure(Beta.GUNS.containsKey(gun)?gun:"ar_ak47",tag.getBoolean("Bo2Ally"),tag.hasUUID("Bo2Owner")?tag.getUUID("Bo2Owner"):null);character(BotCharacters.valid(skin)?skin:"cdc",Math.max(0,Math.min(5,tag.getInt("Bo2DebugPose"))));}
}
