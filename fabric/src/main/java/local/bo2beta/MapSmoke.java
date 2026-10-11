package local.bo2beta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.Screenshot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.*;
/** Exercises the shared match core against a separately imported disposable map. */
public final class MapSmoke {
    private static int stage,ticks,wait,mode;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static final MatchDirector.Mode[] modes=MatchDirector.Mode.values();
    private static Map<UUID,Vec3> positions;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void task(Minecraft c,Runnable r){ready=false;c.getSingleplayerServer().execute(()->{try{r.run();}catch(Throwable e){failure=e;}finally{ready=true;}});}
    private static net.minecraft.server.level.ServerPlayer player(Minecraft c){return c.getSingleplayerServer().getPlayerList().getPlayers().get(0);}
    public static void tick(Minecraft c){
        c.options.pauseOnLostFocus=false;
        if(failure!=null)throw new AssertionError("Map core failed",failure);
        if(++ticks>3600)throw new AssertionError("Map test timeout stage "+stage);
        if(stage==0 && c.screen instanceof TitleScreen){c.createWorldOpenFlows().loadLevel(c.screen,System.getProperty("bo2beta.mapWorld"));stage=1;return;}
        if(c.getSingleplayerServer()==null || c.player==null || c.level==null)return;
        if(stage==1 && ++wait>100){c.setScreen(null);task(c,()->{var p=player(c);check(MatchDirector.mapName.equals(System.getProperty("bo2beta.mapName")),"Wrong map profile");check(MatchDirector.nuketown(p.serverLevel()),"Map core unavailable");p.setGameMode(GameType.CREATIVE);MatchDirector.enterArena(p);check(MatchDirector.withinMovementBounds(p.position()),"Initial spawn outside map");});stage=2;wait=0;return;}
        if(stage==2 && ready){task(c,()->{var p=player(c);check(MatchDirector.start(p,modes[mode],4,100,0,BotDifficulty.REGULAR),"Start "+modes[mode]);MatchDirector.session.countdownEnd=0;MatchDirector.tick(p.server);for(var e:MatchDirector.session.roster){check(e.entity.getMaxHealth()==200 && e.entity.getHealth()==200,"Health "+e.name);check(MatchDirector.safe(p.serverLevel(),e.entity,e.lastSpawn),"Unsafe spawn "+e.lastSpawn);if(e.entity instanceof Bo2Bot b)b.setNoAi(true);}var at=p.position();var kit=p.getMainHandItem().getItem();for(int i=0;i<80;i++)MatchDirector.tick(p.server);check(p.position().distanceToSqr(at)<.001 && p.getMainHandItem().getItem()==kit,"Living player reset");var enemy=MatchDirector.session.roster.stream().filter(e->!e.human && (modes[mode]!=MatchDirector.Mode.TDM || e.team!=0)).findFirst().orElseThrow();var old=enemy.lastSpawn;enemy.entity.invulnerableTime=0;check(enemy.entity.hurt(Beta.bulletDamage(p),1000),"Enemy damage");check(enemy.deathPending,"Death did not schedule respawn");enemy.respawnAt=0;MatchDirector.tick(p.server);check(enemy.entity.isAlive() && enemy.entity.getHealth()==200 && enemy.lastSpawn.distanceToSqr(old)>=9,"Random bot respawn");if(modes[mode]==MatchDirector.Mode.GUN_GAME)check(MatchDirector.session.roster.get(0).tier==1,"Gun progression");p.invulnerableTime=0;check(p.hurt(p.damageSources().generic(),1000),"Player death rejected");check(!p.isAlive(),"Player did not die");for(var e:MatchDirector.session.roster)if(e.deathPending)e.respawnAt=0;MatchDirector.tick(p.server);p=player(c);check(p.isAlive() && p.getHealth()==200 && MatchDirector.safe(p.serverLevel(),p,p.position()),"Player random safe respawn");MatchDirector.stop(p);System.out.println("MAP MODE PASS: "+MatchDirector.mapName+" "+modes[mode]+"; 200 HP; loadouts; stable living position; collision; death-only player/bot respawns; scoring");});stage=3;wait=0;return;}
        if(stage==3 && ready && ++wait>15){mode++;if(mode<modes.length){stage=2;return;}task(c,()->{var p=player(c);check(MatchDirector.start(p,MatchDirector.Mode.FFA,6,100,0),"Live match start");MatchDirector.session.countdownEnd=0;MatchDirector.tick(p.server);positions=new HashMap<>();for(var e:MatchDirector.session.roster)if(!e.human)positions.put(e.id,e.entity.position());});stage=4;wait=0;return;}
        if(stage==4 && ready && ++wait>360){Screenshot.grab(c.gameDirectory,"map-"+System.getProperty("bo2beta.mapName")+".png",c.getMainRenderTarget(),m->{});task(c,()->{long moved=MatchDirector.session.roster.stream().filter(e->!e.human && e.entity.isAlive() && e.entity.position().distanceToSqr(positions.get(e.id))>.25).count();check(moved>0,"No autonomous navigation");for(var e:MatchDirector.session.roster)if(e.entity.isAlive())check(MatchDirector.withinMovementBounds(e.entity.position()),"Actor outside map");MatchDirector.stop(player(c));System.out.println("MAP CORE PASS: "+MatchDirector.mapName+"; all three modes; six live bots; navigation="+moved+"; confinement; cleanup");});stage=5;return;}
        if(stage==5 && ready)c.stop();
    }
}
