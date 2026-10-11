package local.bo2beta;
import java.util.*;
import com.google.gson.JsonObject;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraft.core.particles.DustParticleOptions;
/** Original Fabric utilities; no Bukkit plugin or Arduino project is loaded. */
public final class UtilityGuns {
    public static final class UtilityItem extends Item {
        public final String id;
        UtilityItem(String id){super(new Item.Properties().stacksTo(1));this.id=id;}
        @Override public InteractionResultHolder<ItemStack> use(Level l,net.minecraft.world.entity.player.Player p,InteractionHand hand){return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand),l.isClientSide);}
    }
    public static final UtilityItem GRAVITY=new UtilityItem("gravity_gun"),PORTAL=new UtilityItem("portal_gun");
    private record Hold(Entity target,boolean gravity,boolean noAi){}
    static record Portal(ServerLevel level,Vec3 center,Vec3 normal,Vec3 up,Vec3 right){}
    private static final Map<UUID,Hold> holds=new HashMap<>();
    private static final Map<UUID,Portal[]> portals=new HashMap<>();
    private static final Map<UUID,Long> travelCooldown=new HashMap<>(),actionCooldown=new HashMap<>();
    public static int grabs,launches,travels,placements;
    public static void init(){Registry.register(BuiltInRegistries.ITEM,new ResourceLocation("bo2beta","gravity_gun"),GRAVITY);Registry.register(BuiltInRegistries.ITEM,new ResourceLocation("bo2beta","portal_gun"),PORTAL);}
    public static Item item(String id){return id.equals("gravity_gun")?GRAVITY:id.equals("portal_gun")?PORTAL:null;}
    public static void reset(){for(var h:holds.values())restore(h);holds.clear();portals.clear();travelCooldown.clear();actionCooldown.clear();}
    private static void restore(Hold h){h.target.setNoGravity(h.gravity);if(h.target instanceof Mob m)m.setNoAi(h.noAi || h.target instanceof Bo2Bot && !MatchDirector.playing((Bo2Bot)h.target));}
    public static void release(ServerPlayer p){var h=holds.remove(p.getUUID());if(h!=null)restore(h);}
    public static void clear(ServerPlayer p){release(p);portals.remove(p.getUUID());actionCooldown.remove(p.getUUID());}
    public static void action(ServerPlayer p,String action){
        if(!p.isAlive() || !MatchDirector.playing(p) || !(p.getMainHandItem().getItem() instanceof UtilityItem gun))return;
        if(action.equals("utility_reset")){clear(p);message(p,"Utility reset");return;}
        long now=p.serverLevel().getGameTime();if(now<actionCooldown.getOrDefault(p.getUUID(),0L))return;actionCooldown.put(p.getUUID(),now+6);
        if(gun==GRAVITY){
            if(action.equals("utility_left")){var h=holds.remove(p.getUUID());if(h!=null){restore(h);h.target.setDeltaMovement(p.getLookAngle().scale(2.6));h.target.hurtMarked=true;launches++;feedback(p,gun);}}
            else if(action.equals("utility_right")){if(holds.containsKey(p.getUUID())){release(p);message(p,"Released");}else if(!p.isCrouching())grab(p);}
        }else if(action.equals("utility_left") || action.equals("utility_right"))place(p,action.equals("utility_left")?0:1);
    }
    static boolean grab(ServerPlayer p){
        Vec3 from=p.getEyePosition(),end=from.add(p.getLookAngle().scale(16));var wall=p.serverLevel().clip(new ClipContext(from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(wall.getType()!=HitResult.Type.MISS)end=wall.getLocation();
        Entity picked=null;double nearest=from.distanceToSqr(end);
        for(var entity:p.serverLevel().getEntities(p,p.getBoundingBox().expandTowards(end.subtract(from)).inflate(2),e->e.isAlive() && !e.isSpectator() && !(e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon) && !(e instanceof net.minecraft.world.entity.boss.wither.WitherBoss))){
            if(holds.values().stream().anyMatch(h->h.target==entity))continue;var hit=entity.getBoundingBox().inflate(.1).clip(from,end);if(hit.isPresent() && hit.get().distanceToSqr(from)<nearest){picked=entity;nearest=hit.get().distanceToSqr(from);}
        }
        if(picked==null){message(p,"Aim at a player, bot, mob or loose entity within 16 blocks");return false;}
        var h=new Hold(picked,picked.isNoGravity(),picked instanceof Mob m && m.isNoAi());holds.put(p.getUUID(),h);picked.setNoGravity(true);if(picked instanceof Mob m){m.setNoAi(true);m.getNavigation().stop();}grabs++;feedback(p,GRAVITY);message(p,"Holding — right click to drop, left click to launch");return true;
    }
    private static void feedback(ServerPlayer p,UtilityItem gun){
        var data=new JsonObject();data.addProperty("id",p.getUUID().toString());data.addProperty("weapon",gun.id);data.addProperty("reload",false);data.addProperty("fire",true);data.addProperty("tick",p.serverLevel().getGameTime());var packet=new JsonObject();packet.add("playerAnimation",data);for(var viewer:p.serverLevel().players())Beta.send(viewer,packet);
        var event=new JsonObject();event.addProperty("utility_fire",gun.id);Beta.send(p,event);p.serverLevel().playSound(null,p.blockPosition(),net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,net.minecraft.sounds.SoundSource.PLAYERS,.4f,gun==GRAVITY?1.2f:1.7f);
    }
    private static void message(ServerPlayer p,String text){p.displayClientMessage(Component.literal(text),true);}
    static boolean place(ServerPlayer p,int color){
        Vec3 from=p.getEyePosition(),end=from.add(p.getLookAngle().scale(64));var hit=p.serverLevel().clip(new ClipContext(from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(hit.getType()==HitResult.Type.MISS){message(p,"Aim at a clear solid surface");return false;}
        var normal=Vec3.atLowerCornerOf(hit.getDirection().getNormal());var up=Math.abs(normal.y)>.5?Vec3.atLowerCornerOf(p.getDirection().getNormal()):new Vec3(0,1,0);var right=up.cross(normal).normalize();
        var center=Vec3.atCenterOf(hit.getBlockPos()).add(normal.scale(.501));var portal=new Portal(p.serverLevel(),center,normal,up,right);
        if(!valid(portal) || MatchDirector.nuketown(p.serverLevel()) && !MatchDirector.withinMovementBounds(center)){message(p,"Portal needs a solid 2 x 3 surface with clear space inside the map");return false;}
        portals.computeIfAbsent(p.getUUID(),id->new Portal[2])[color]=portal;placements++;feedback(p,PORTAL);message(p,color==0?"Blue portal placed":"Orange portal placed");return true;
    }
    private static boolean valid(Portal p){
        for(double x:new double[]{-.5,.5})for(double y:new double[]{-1,0,1}){
            var at=p.center.add(p.right.scale(x)).add(p.up.scale(y));var back=BlockPos.containing(at.subtract(p.normal.scale(.1)));var front=BlockPos.containing(at.add(p.normal.scale(.1)));
            if(!p.level.getWorldBorder().isWithinBounds(back) || !p.level.getBlockState(back).isSolidRender(p.level,back) || !p.level.getBlockState(front).getCollisionShape(p.level,front).isEmpty() || !p.level.getBlockState(front).getFluidState().isEmpty())return false;
        }return true;
    }
    public static void tick(MinecraftServer server){
        for(var id:new ArrayList<>(holds.keySet())){
            var p=server.getPlayerList().getPlayer(id);var h=holds.get(id);
            if(p==null || !p.isAlive() || !h.target.isAlive() || h.target.level()!=p.level() || p.getMainHandItem().getItem()!=GRAVITY || !MatchDirector.playing(p) || h.target.distanceToSqr(p)>24*24){if(p==null){restore(h);holds.remove(id);}else release(p);continue;}
            var goal=p.getEyePosition().add(p.getLookAngle().scale(3)).add(0,-h.target.getBbHeight()*.5,0);
            var wall=p.serverLevel().clip(new ClipContext(p.getEyePosition(),goal,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(wall.getType()!=HitResult.Type.MISS)goal=wall.getLocation().subtract(p.getLookAngle().scale(.7));
            if(MatchDirector.nuketown(p.serverLevel()) && !MatchDirector.withinMovementBounds(goal)){release(p);continue;}
            var velocity=goal.subtract(h.target.position()).scale(.45);if(velocity.length()>.8)velocity=velocity.normalize().scale(.8);h.target.fallDistance=0;h.target.setDeltaMovement(velocity);h.target.hurtMarked=true;
        }
        for(var owner:new ArrayList<>(portals.keySet())){
            if(server.getPlayerList().getPlayer(owner)==null){portals.remove(owner);continue;}
            var pair=portals.get(owner);for(int i=0;i<2;i++)if(pair[i]!=null && !valid(pair[i]))pair[i]=null;
            for(int i=0;i<2;i++)if(pair[i]!=null){var p=pair[i];if(p.level.getGameTime()%4==0)ring(p,i);if(pair[1-i]!=null)for(var entity:p.level.getEntities((Entity)null,new AABB(p.center,p.center).inflate(2),e->e.isAlive() && !e.isSpectator()))tryTravel(entity,p,pair[1-i]);}
        }
        if(server.getTickCount()%200==0)travelCooldown.entrySet().removeIf(e->e.getValue()<server.overworld().getGameTime());
    }
    static boolean tryTravel(Entity entity,Portal from,Portal to){
        long now=from.level.getGameTime();if(now<travelCooldown.getOrDefault(entity.getUUID(),0L))return false;
        var center=entity.getBoundingBox().getCenter();var delta=center.subtract(from.center);double normal=delta.dot(from.normal),u=delta.dot(from.right),v=delta.dot(from.up);
        double half=Math.abs(from.normal.y)>.5?entity.getBbHeight()*.5:entity.getBbWidth()*.5;
        if(normal<-.1 || normal>half+.08 || Math.abs(u)>1-entity.getBbWidth()*.5 || Math.abs(v)>1.5-(Math.abs(from.up.y)>.5?entity.getBbHeight()*.5:entity.getBbWidth()*.5) || entity.getDeltaMovement().dot(from.normal)>.02)return false;
        double outHalf=Math.abs(to.normal.y)>.5?entity.getBbHeight()*.5:entity.getBbWidth()*.5;
        var out=to.center.add(to.right.scale(u)).add(to.up.scale(v)).add(to.normal.scale(outHalf+.12)).add(0,-entity.getBbHeight()*.5,0);
        if(MatchDirector.nuketown(to.level) && !MatchDirector.withinMovementBounds(out) || !to.level.noCollision(entity,entity.getBoundingBox().move(out.subtract(entity.position())).deflate(.01)))return false;
        var velocity=transform(entity.getDeltaMovement(),from,to);var look=transform(entity.getLookAngle(),from,to);float yaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z)),pitch=(float)Math.toDegrees(Math.atan2(-look.y,Math.sqrt(look.x*look.x+look.z*look.z)));
        if(entity instanceof ServerPlayer p){p.connection.teleport(out.x,out.y,out.z,yaw,pitch);message(p,"Portal travel");}else entity.moveTo(out.x,out.y,out.z,yaw,pitch);
        entity.setDeltaMovement(velocity);entity.hurtMarked=true;entity.fallDistance=0;travelCooldown.put(entity.getUUID(),now+20);travels++;return true;
    }
    private static Vec3 transform(Vec3 v,Portal from,Portal to){return to.right.scale(v.dot(from.right)).add(to.up.scale(v.dot(from.up))).subtract(to.normal.scale(v.dot(from.normal)));}
    private static void ring(Portal p,int color){var dust=new DustParticleOptions(new org.joml.Vector3f(color==0?.05f:1,color==0?.55f:.35f,color==0?1:.05f),1.2f);for(int i=0;i<28;i++){double t=i*Math.PI*2/28;var at=p.center.add(p.right.scale(Math.cos(t)*.95)).add(p.up.scale(Math.sin(t)*1.45)).add(p.normal.scale(.03));p.level.sendParticles(dust,at.x,at.y,at.z,1,0,0,0,0);}}
}
