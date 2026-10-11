package local.bo2beta;
import com.google.gson.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
/** Real client targeting and bridge gunfire in the disposable smoke world only. */
public final class AimSmoke {
    private static int stage,ticks;private static volatile boolean ready;private static volatile Throwable failure;
    private static UUID id;private static Bo2Bot serverBot;private static final Map<BlockPos,BlockState> blocks=new HashMap<>();
    private static JsonObject saved;private static boolean enabled,fire,head;private static int fov,range;private static float speed;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void task(Minecraft c,Runnable r){ready=false;c.getSingleplayerServer().execute(()->{try{r.run();}catch(Throwable e){failure=e;}finally{ready=true;}});}
    private static Bo2Bot bot(Minecraft c){return (Bo2Bot)c.level.getEntitiesOfClass(Bo2Bot.class,c.player.getBoundingBox().inflate(128)).stream().filter(b->b.getUUID().equals(id)).findFirst().orElse(null);}
    private static void face(Minecraft c,Bo2Bot b,float offset){var a=AimAssist.angles(c.player.getEyePosition(),AimAssist.point(b));c.player.setYRot(a[0]+offset);c.player.setXRot(a[1]);}
    public static boolean tick(Minecraft c){
        if(failure!=null)throw new AssertionError("Aim smoke server fixture",failure);
        if(stage==0){saved=MatchClient.state.deepCopy();enabled=AimAssist.enabled;fire=AimAssist.autoFire;head=AimAssist.head;fov=AimAssist.fov;range=AimAssist.range;speed=AimAssist.smoothing;AimAssist.enabled=false;c.setScreen(null);
            task(c,()->{var p=c.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.setHealth(20);p.connection.teleport(1630.5,20,-792.5,0,0);p.getInventory().setItem(0,new net.minecraft.world.item.ItemStack(Beta.GUNS.get("ar_ak47")));p.getInventory().selected=0;Beta.resetWeapon(p);
                serverBot=Beta.BOT_ENTITY.create(p.serverLevel());check(serverBot!=null,"Bot fixture create");serverBot.configure("ar_ak47",false,p.getUUID());serverBot.setNoAi(true);serverBot.setNoGravity(true);serverBot.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);serverBot.setHealth(1000);
                Vec3 spot=null;for(int x=-8;x<=8 && spot==null;x++)for(int z=-8;z<=8 && spot==null;z++){var v=p.position().add(x,0,z);if(v.distanceToSqr(p.position())<16 || !MatchDirector.safe(p.serverLevel(),serverBot,v))continue;var end=v.add(0,serverBot.getEyeHeight(),0);if(p.serverLevel().clip(new ClipContext(p.getEyePosition(),end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS)spot=v;}
                check(spot!=null,"Clear Nuketown aim fixture");serverBot.moveTo(spot.x,spot.y,spot.z,0,0);id=serverBot.getUUID();p.serverLevel().addFreshEntity(serverBot);
            });stage=1;ticks=0;return false;}
        if(!ready)return false;
        var b=bot(c);if(stage<6 && b==null){check(++ticks<200,"Client bot synchronization");return false;}
        if(stage==1){if(++ticks<20)return false;AimAssist.head=true;AimAssist.fov=90;AimAssist.range=64;AimAssist.smoothing=.35f;face(c,b,20);float yaw=c.player.getYRot();AimAssist.tick(c);check(c.player.getYRot()==yaw && AimAssist.target==null,"Disabled aim changed view");AimAssist.enabled=true;face(c,b,80);AimAssist.tick(c);check(AimAssist.target==null,"Outside FOV selected");AimAssist.range=1;face(c,b,0);AimAssist.tick(c);check(AimAssist.target==null,"Outside range selected");AimAssist.range=64;
            var s=new JsonObject();s.addProperty("phase","PLAYING");s.addProperty("mode","TDM");var rows=new JsonArray();var p=new JsonObject();p.addProperty("id",c.player.getUUID().toString());p.addProperty("team",0);rows.add(p);var r=new JsonObject();r.addProperty("id",UUID.randomUUID().toString());r.addProperty("entity",id.toString());r.addProperty("team",0);rows.add(r);s.add("roster",rows);MatchClient.state=s;check(!AimAssist.opponent(c,b),"TDM teammate selected");r.addProperty("team",1);check(AimAssist.opponent(c,b),"TDM opponent rejected");s.addProperty("mode","FFA");r.addProperty("team",0);check(AimAssist.opponent(c,b),"FFA target rejected");MatchClient.state=saved;
            c.setScreen(new AimScreen());AimAssist.tick(c);check(AimAssist.target==null && !AimAssist.firing,"Menu did not suppress aim");Screenshot.grab(c.gameDirectory,"bot-aim-menu.png",c.getMainRenderTarget(),m->{});stage=2;ticks=0;return false;}
        if(stage==2){if(++ticks<10)return false;Screenshot.grab(c.gameDirectory,"bot-aim-menu.png",c.getMainRenderTarget(),m->{});c.setScreen(null);face(c,b,20);stage=3;ticks=0;}
        if(stage==3){AimAssist.tick(c);if(++ticks<20)return false;check(AimAssist.target==b && AimAssist.error(c.player.getYRot(),c.player.getXRot(),AimAssist.angles(c.player.getEyePosition(),AimAssist.point(b)))<1,"Smoothed aim did not align");AimAssist.head=false;check(AimAssist.point(b).y<b.getEyeY(),"Body point");AimAssist.head=true;AimAssist.enabled=false;
            task(c,()->{var p=c.getSingleplayerServer().getPlayerList().getPlayers().get(0);var mid=BlockPos.containing(p.getEyePosition().add(serverBot.getEyePosition()).scale(.5));for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){var at=mid.offset(x,y,z);blocks.put(at,p.serverLevel().getBlockState(at));p.serverLevel().setBlock(at,Blocks.STONE.defaultBlockState(),3);}});stage=4;ticks=0;return false;}
        if(stage==4){if(++ticks<15)return false;AimAssist.enabled=true;AimAssist.autoFire=true;face(c,b,0);AimAssist.tick(c);check(AimAssist.target==null && !AimAssist.firing,"Wall did not block target and auto-fire");AimAssist.enabled=false;task(c,()->{var level=serverBot.level();blocks.forEach((at,state)->level.setBlock(at,state,3));blocks.clear();});stage=5;ticks=0;return false;}
        if(stage==5){if(++ticks==15){face(c,b,10);AimAssist.enabled=true;AimAssist.autoFire=true;}if(ticks<65)return false;AimAssist.enabled=false;task(c,()->{check(serverBot.getHealth()<1000,"Auto-fire caused no real bridge damage");System.out.println("MATCH AIM PASS: disabled; FOV; range; TDM/FFA; head/body; menu suppression; smoothing; wall occlusion; real auto-fire damage="+(1000-serverBot.getHealth()));serverBot.discard();});stage=6;return false;}
        if(stage==6){MatchClient.state=saved;AimAssist.enabled=enabled;AimAssist.autoFire=fire;AimAssist.head=head;AimAssist.fov=fov;AimAssist.range=range;AimAssist.smoothing=speed;AimAssist.target=null;AimAssist.firing=false;return true;}
        return false;
    }
}
