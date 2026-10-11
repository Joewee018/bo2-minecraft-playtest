package local.bo2beta;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** A server-authoritative Nuketown match lifecycle. Minecraft remains the simulation. */
public final class MatchDirector {
    public enum Mode { TDM("Team Deathmatch"), FFA("Free-for-all"), GUN_GAME("Gun Game");
        public final String label;Mode(String label){this.label=label;}}
    public enum Phase { COUNTDOWN, PLAYING, FINISHED }
    public static final int MAX_COMBATANTS=18;
    public static final double BOT_MOVEMENT_SPEED=.21;
    // The bridge currently lacks the retail launchers/crossbow/ballistic knife.
    // This explicitly supported 20-stage ladder must not be called the retail ladder.
    public static final List<String> GUN_LADDER=List.of("pistol_b2023r","pistol_kard","pistol_python",
        "shotty_saiga","shotty_srm1216","smg_ak74u","smg_mp5","smg_chicom","ar_m4","ar_xm8",
        "ar_saritch","lmg_rpd","lmg_m60","sniper_m82","sniper_dsr50","ar_type95","lmg_hamr",
        "ar_ak47","ar_fal","pistol_fiveseven");
    static final Map<UUID,Entry> entities=new HashMap<>();
    static Session session;
    private static final Random random=new Random();
    private static List<Vec3> spawnPoints;
    private static List<double[]> arena;
    private static final Map<UUID,Vec3> lastArenaPosition=new HashMap<>();
    private static final Map<UUID,Integer> outsideTicks=new HashMap<>();
    public static final class Entry {
        public final UUID id;
        public final boolean human;
        public final String name;
        public int team,kills,deaths,score,tier;
        public boolean deathPending;
        public String skin,primary,secondary;
        public LivingEntity entity;
        public Vec3 lastSpawn;
        public long respawnAt=-1,lastMelee=-100;
        final ListTag inventory;
        final GameType previousMode;
        final Vec3 previousPosition;
        final float previousYaw,previousPitch,previousHealth;
        final int previousFood;
        final float previousSaturation;
        final boolean previousNoGravity;
        final int previousXP,previousLevel;final float previousProgress;
        Entry(ServerPlayer player){
            id=player.getUUID();human=true;name=player.getGameProfile().getName();entity=player;
            inventory=player.getInventory().save(new ListTag());previousMode=player.gameMode.getGameModeForPlayer();
            previousPosition=player.position();previousYaw=player.getYRot();previousPitch=player.getXRot();previousHealth=player.getHealth();
            previousFood=player.getFoodData().getFoodLevel();previousSaturation=player.getFoodData().getSaturationLevel();previousNoGravity=player.isNoGravity();previousXP=player.totalExperience;previousLevel=player.experienceLevel;previousProgress=player.experienceProgress;
        }
        Entry(int number){id=UUID.randomUUID();human=false;name="Bot "+number;inventory=null;previousMode=null;previousPosition=null;previousYaw=previousPitch=previousHealth=previousSaturation=0;previousFood=0;previousNoGravity=false;previousXP=previousLevel=0;previousProgress=0;}
    }
    public static final class Session {
        public final UUID host;
        public final ServerLevel level;
        public final Mode mode;
        public BotDifficulty difficulty=BotDifficulty.REGULAR;
        public final int botCount,scoreLimit,timeLimit;
        public final List<Entry> roster=new ArrayList<>();
        public final int[] teamScores={0,0};
        public Phase phase=Phase.COUNTDOWN;
        public long countdownEnd,startTick,endTick;
        public String result="";
        Session(ServerPlayer host,Mode mode,int bots,int limit,int minutes){this.host=host.getUUID();this.level=host.serverLevel();this.mode=mode;botCount=bots;scoreLimit=mode==Mode.GUN_GAME?GUN_LADDER.size():limit;timeLimit=minutes*60*20;countdownEnd=level.getGameTime()+60;}
    }
    public static void reset(){session=null;entities.clear();lastArenaPosition.clear();outsideTicks.clear();}
    public static boolean owns(LivingEntity entity){return entities.containsKey(entity.getUUID());}
    public static boolean playing(LivingEntity entity){return !owns(entity) || session!=null && session.phase==Phase.PLAYING;}
    public static boolean target(LivingEntity attacker,LivingEntity victim){
        var a=entities.get(attacker.getUUID());var b=entities.get(victim.getUUID());
        return session!=null && session.phase==Phase.PLAYING && a!=null && b!=null && a!=b && victim.isAlive()
            && (session.mode!=Mode.TDM || a.team!=b.team);
    }
    public static boolean damageAllowed(LivingEntity victim,DamageSource source){
        var v=entities.get(victim.getUUID());var actor=source.getEntity();
        if(v!=null && session.phase!=Phase.PLAYING)return false;
        if(actor instanceof LivingEntity attacker && (v!=null || owns(attacker)))return target(attacker,victim);
        return true;
    }
    public static boolean canHost(ServerPlayer p){return p.hasPermissions(2) || p.isCreative() || p.server.isSingleplayerOwner(p.getGameProfile());}
    public static boolean start(ServerPlayer host,Mode mode,int bots,int limit,int minutes){return start(host,mode,bots,limit,minutes,BotDifficulty.REGULAR);}
    public static boolean start(ServerPlayer host,Mode mode,int bots,int limit,int minutes,BotDifficulty difficulty){
        if(!host.isAlive()){message(host,"Respawn before starting a match.");return false;}
        if(session!=null){message(host,"A match is already running. End it before starting another.");return false;}
        if(!canHost(host)){message(host,"Only the host/operator can start a match.");return false;}
        if(mode==null || bots<0 || bots>=MAX_COMBATANTS || limit<1 || limit>1000 || minutes<0 || minutes>60)return false;
        var level=host.serverLevel();var origin=level.getSharedSpawnPos();
        if(Math.abs(origin.getX()-1633)>100 || Math.abs(origin.getZ()+784)>100){message(host,"Open the Nuketown world to start this lobby.");return false;}
        var humans=new ArrayList<>(level.players());humans.sort(Comparator.comparing(p->!p.getUUID().equals(host.getUUID())));
        if(humans.size()+bots>MAX_COMBATANTS){message(host,"Nuketown supports up to 18 combatants, including players.");return false;}
        if(humans.size()+bots<2){message(host,"Choose at least one opponent.");return false;}
        var next=new Session(host,mode,bots,limit,minutes);next.difficulty=difficulty;session=next;
        for(var player:humans){var e=new Entry(player);next.roster.add(e);entities.put(player.getUUID(),e);}
        for(int i=0;i<bots;i++)next.roster.add(new Entry(i+1));
        int[] teams={0,0};
        for(var e:next.roster){e.team=mode==Mode.TDM?(teams[0]<=teams[1]?0:1):next.roster.indexOf(e);if(mode==Mode.TDM)teams[e.team]++;}
        // Remove existing debug bots from the arena, not unrelated mobs/worlds.
        for(var b:level.getEntitiesOfClass(Bo2Bot.class,new net.minecraft.world.phys.AABB(1550,0,-870,1715,100,-700)))if(!owns(b))b.discard();
        try{for(var e:next.roster)spawn(e);}catch(IllegalStateException ex){stop(host.server,"Match could not start: "+ex.getMessage());message(host,ex.getMessage());return false;}
        broadcast(host.server);return true;
    }
    private static void loadSpawns(){
        if(spawnPoints!=null)return;
        try(var in=MatchDirector.class.getResourceAsStream("/data/bo2beta/nuketown-spawns.json")){
            var data=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();var array=data.getAsJsonArray("points");arena=new ArrayList<>();for(var item:data.getAsJsonArray("polygon")){var a=item.getAsJsonArray();arena.add(new double[]{a.get(0).getAsDouble(),a.get(1).getAsDouble()});}
            spawnPoints=new ArrayList<>();for(var p:array){var v=p.getAsJsonArray();spawnPoints.add(new Vec3(v.get(0).getAsDouble(),v.get(1).getAsDouble(),v.get(2).getAsDouble()));}
        }catch(IOException e){throw new IllegalStateException("Nuketown spawn data unavailable",e);}
    }
    static boolean safe(ServerLevel level,LivingEntity entity,Vec3 point){
        if(!insideArena(point))return false;var feet=BlockPos.containing(point);var floor=feet.below();var state=level.getBlockState(floor);
        if(!level.getWorldBorder().isWithinBounds(feet) || !state.isSolidRender(level,floor) || !state.getFluidState().isEmpty()
            || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.CACTUS))return false;
        var box=entity.getBoundingBox().move(point.subtract(entity.position())).deflate(.01);
        return level.noCollision(entity,box) && !level.containsAnyLiquid(box) && level.getBlockState(feet).getCollisionShape(level,feet).isEmpty()
            && !level.getBlockState(feet).is(Blocks.FIRE) && !level.getBlockState(feet).is(Blocks.POWDER_SNOW);
    }
    private static Vec3 chooseSpawn(Entry entry){
        loadSpawns();Vec3 best=null;double bestDistance=-1;
        var indices=new ArrayList<Integer>();for(int i=0;i<spawnPoints.size();i++)indices.add(i);Collections.shuffle(indices,random);
        int valid=0;
        for(int index:indices){
            var p=spawnPoints.get(index);
            if(entry.lastSpawn!=null && p.distanceToSqr(entry.lastSpawn)<9 || !safe(session.level,entry.entity,p))continue;
            double nearest=10000;
            for(var other:session.roster)if(other!=entry && other.entity!=null && other.entity.isAlive())nearest=Math.min(nearest,p.distanceToSqr(other.entity.position()));
            if(nearest>bestDistance){best=p;bestDistance=nearest;}
            if(++valid>=12)break;
        }
        if(best==null)throw new IllegalStateException("No clear Nuketown respawn location remains.");return best;
    }
    private static String randomSkin(){var ids=new ArrayList<>(BotCharacters.NAMES.keySet());ids.remove("cdc");return ids.isEmpty()?"cdc":ids.get(random.nextInt(ids.size()));}
    private static void loadout(Entry e){
        if(session.mode==Mode.GUN_GAME){e.primary=GUN_LADDER.get(Math.min(e.tier,GUN_LADDER.size()-1));e.secondary=null;}
        else{
            var primary=Beta.GUNS.keySet().stream().filter(id->!id.startsWith("pistol_") && !id.equals("shotty_ksg")).toList();
            var secondary=Beta.GUNS.keySet().stream().filter(id->id.startsWith("pistol_")).toList();
            e.primary=primary.get(random.nextInt(primary.size()));e.secondary=secondary.get(random.nextInt(secondary.size()));
        }
        equip(e);
    }
    private static void equip(Entry e){
        if(e.entity instanceof ServerPlayer p){
            GrenadeController.cancel(p);Beta.resetWeapon(p);p.getInventory().clearContent();p.getInventory().selected=0;
            p.getInventory().setItem(0,new ItemStack(Beta.GUNS.get(e.primary)));
            if(e.secondary!=null)p.getInventory().setItem(1,new ItemStack(Beta.GUNS.get(e.secondary)));
            if(session.mode!=Mode.GUN_GAME)p.getInventory().setItem(2,new ItemStack(Beta.GRENADE,2));
            p.inventoryMenu.broadcastChanges();
        }else if(e.entity instanceof Bo2Bot bot){
            bot.configure(e.primary,e.team==0,session.host);
            bot.setCustomName(Component.literal(e.name+" ("+BotCharacters.NAMES.get(e.skin)+")"));bot.setCustomNameVisible(true);
            if(e.secondary!=null)bot.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Beta.GUNS.get(e.secondary)));
        }
    }
    private static void spawn(Entry e){
        if(session==null)return;
        if(e.human){var p=session.level.getServer().getPlayerList().getPlayer(e.id);if(p==null)return;e.entity=p;((local.bo2beta.mixin.ServerPlayerAccess)p).bo2beta$spawnProtection(0);p.setGameMode(GameType.ADVENTURE);p.setNoGravity(false);CombatRules.prepare(p);p.setHealth(p.getMaxHealth());p.getFoodData().setFoodLevel(20);p.getFoodData().setSaturation(5);}
        else{
            if(e.entity!=null){lastArenaPosition.remove(e.entity.getUUID());outsideTicks.remove(e.entity.getUUID());entities.remove(e.entity.getUUID());e.entity.discard();}
            var bot=Beta.BOT_ENTITY.create(session.level);if(bot==null)throw new IllegalStateException("Bot creation failed");
            e.entity=bot;bot.matchEntrant=e.id;CombatRules.prepare(bot);entities.put(bot.getUUID(),e);e.skin=randomSkin();
            bot.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(BOT_MOVEMENT_SPEED);
            bot.difficulty(session.difficulty);bot.configure("ar_ak47",e.team==0,session.host);bot.character(e.skin,0);
            bot.setCustomName(Component.literal(e.name+" ("+BotCharacters.NAMES.get(e.skin)+")"));bot.setCustomNameVisible(true);
        }
        var point=chooseSpawn(e);e.lastSpawn=point;
        float yaw=random.nextFloat()*360;
        if(e.entity instanceof ServerPlayer p)p.connection.teleport(point.x,point.y,point.z,yaw,0);
        else {e.entity.moveTo(point.x,point.y,point.z,yaw,0);if(!session.level.addFreshEntity(e.entity))throw new IllegalStateException("Bot was not added to Nuketown");((Bo2Bot)e.entity).setNoAi(session.phase!=Phase.PLAYING);}
        lastArenaPosition.put(e.entity.getUUID(),point);outsideTicks.remove(e.entity.getUUID());e.entity.fallDistance=0;e.entity.setDeltaMovement(Vec3.ZERO);e.entity.invulnerableTime=0;e.entity.setHealth(e.entity.getMaxHealth());e.respawnAt=-1;e.deathPending=false;loadout(e);
    }
    public static Vec3 route(Bo2Bot bot,Vec3 threat,boolean cover){
        if(session==null)return null;loadSpawns();var points=new ArrayList<>(spawnPoints);Collections.shuffle(points,random);
        int tested=0;for(var at:points){double distance=at.distanceToSqr(bot.position());if(distance<16 || distance>1600 || !safe(session.level,bot,at))continue;
            if(cover && threat!=null && session.level.clip(new net.minecraft.world.level.ClipContext(at.add(0,bot.getEyeHeight(),0),threat,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,bot)).getType()==net.minecraft.world.phys.HitResult.Type.MISS)continue;
            var path=bot.getNavigation().createPath(net.minecraft.core.BlockPos.containing(at),0);if(path!=null && path.canReach())return at;if(++tested>=8)break;
        }return null;
    }
    public static void beforeDeath(LivingEntity victim){if(owns(victim) && victim instanceof ServerPlayer p)p.getInventory().clearContent();}
    public static void death(LivingEntity victim,DamageSource source){
        var dead=entities.get(victim.getUUID());if(dead==null || session==null || session.phase!=Phase.PLAYING || dead.deathPending)return;
        dead.deathPending=true;
        var killer=source.getEntity() instanceof LivingEntity attacker?entities.get(attacker.getUUID()):null;
        dead.deaths++;dead.respawnAt=session.level.getGameTime()+20;
        boolean credited=killer!=null && killer!=dead && (session.mode!=Mode.TDM || killer.team!=dead.team);
        if(credited){
            killer.kills++;
            if(session.mode==Mode.GUN_GAME){
                boolean melee=source.is(Beta.KNIFE_DAMAGE) || killer.entity.getMainHandItem().getItem() instanceof SwordItem || !(killer.entity.getMainHandItem().getItem() instanceof Beta.Gun);
                if(melee){dead.tier=Math.max(0,dead.tier-1);dead.score=dead.tier;}
                else{killer.score++;if(killer.tier<GUN_LADDER.size()-1){killer.tier++;killer.primary=GUN_LADDER.get(killer.tier);equip(killer);}else finish(killer.name+" wins Gun Game");}
            }else{killer.score++;if(session.mode==Mode.TDM)session.teamScores[killer.team]++;}
            var hud=new JsonObject();hud.addProperty("hit",false);hud.addProperty("kill",true);hud.addProperty("killer",killer.name);hud.addProperty("target",dead.name);
            for(var p:session.level.players()){hud.addProperty("kills",entities.containsKey(p.getUUID())?entities.get(p.getUUID()).kills:0);var packet=new JsonObject();packet.add("hud",hud.deepCopy());Beta.send(p,packet);}
        }else if(session.mode==Mode.GUN_GAME){dead.tier=Math.max(0,dead.tier-1);dead.score=dead.tier;}
        else if(session.mode==Mode.FFA)dead.score=Math.max(0,dead.score-1);
        if(session.mode==Mode.TDM && Arrays.stream(session.teamScores).max().orElse(0)>=session.scoreLimit)finish((session.teamScores[0]>session.teamScores[1]?"Team 1":"Team 2")+" wins");
        if(session.mode==Mode.FFA && credited && killer.score>=session.scoreLimit)finish(killer.name+" wins");
        broadcast(session.level.getServer());
    }
    private static void finish(String result){
        if(session==null || session.phase==Phase.FINISHED)return;
        session.phase=Phase.FINISHED;session.result=result;
        for(var e:session.roster)if(e.entity instanceof Bo2Bot bot){bot.setNoAi(true);bot.getNavigation().stop();bot.setTarget(null);}
    }
    public static boolean insideArena(Vec3 p){
        loadSpawns();boolean inside=false;for(int i=0,j=arena.size()-1;i<arena.size();j=i++){var a=arena.get(i);var b=arena.get(j);if((a[1]>p.z)!=(b[1]>p.z) && p.x<(b[0]-a[0])*(p.z-a[1])/(b[1]-a[1])+a[0])inside=!inside;}return inside && p.y>=18 && p.y<60;
    }
    /** Spawn candidates stay strict; movement includes road edges, steps and ordinary jumps. */
    public static boolean withinMovementBounds(Vec3 p){
        if(p.y<14 || p.y>=60)return false;
        if(insideArena(new Vec3(p.x,20,p.z)))return true;
        for(int i=0,j=arena.size()-1;i<arena.size();j=i++){
            var a=arena.get(j);var b=arena.get(i);double dx=b[0]-a[0],dz=b[1]-a[1];
            double t=Math.max(0,Math.min(1,((p.x-a[0])*dx+(p.z-a[1])*dz)/(dx*dx+dz*dz)));
            double x=p.x-(a[0]+t*dx),z=p.z-(a[1]+t*dz);if(x*x+z*z<=36)return true;
        }return false;
    }
    private static void confine(LivingEntity entity){
        var level=(ServerLevel)entity.level();var id=entity.getUUID();
        if(!entity.isAlive() || !nuketown(level))return;
        if(withinMovementBounds(entity.position())){outsideTicks.remove(id);if(safe(level,entity,entity.position()))lastArenaPosition.put(id,entity.position());return;}
        // A brief crossing of an edge must not teleport a running or jumping player.
        if(entity.getY()>=14 && outsideTicks.merge(id,1,Integer::sum)<20)return;
        Vec3 at=lastArenaPosition.get(id);
        if(at==null || !safe(level,entity,at)){loadSpawns();at=spawnPoints.stream().filter(v->safe(level,entity,v)).min(Comparator.comparingDouble(v->v.distanceToSqr(entity.position()))).orElse(null);}
        if(at==null)return;entity.fallDistance=0;entity.setDeltaMovement(Vec3.ZERO);
        if(entity instanceof ServerPlayer p){p.connection.teleport(at.x,at.y,at.z,p.getYRot(),p.getXRot());p.displayClientMessage(Component.literal("Map boundary — returned to nearby playable ground"),true);}
        else entity.moveTo(at.x,at.y,at.z,entity.getYRot(),entity.getXRot());
        lastArenaPosition.put(id,at);outsideTicks.remove(id);
    }
    public static boolean nuketown(ServerLevel level){var p=level.getSharedSpawnPos();return level.dimension()==net.minecraft.world.level.Level.OVERWORLD && Math.abs(p.getX()-1633)<100 && Math.abs(p.getZ()+784)<100;}
    public static void enterArena(ServerPlayer p){
        if(Boolean.getBoolean("bo2beta.smoke") && !Boolean.getBoolean("bo2beta.matchCheck"))return;
        if(!nuketown(p.serverLevel()) || !p.isAlive() || withinMovementBounds(p.position()))return;
        loadSpawns();Vec3 at=spawnPoints.stream().filter(v->safe(p.serverLevel(),p,v)).min(Comparator.comparingDouble(v->v.distanceToSqr(new Vec3(1630.5,20,-792.5)))).orElse(null);
        if(at!=null){p.fallDistance=0;p.setDeltaMovement(Vec3.ZERO);p.connection.teleport(at.x,at.y,at.z,180,0);}
    }
    public static void tick(MinecraftServer server){
        for(var p:server.getPlayerList().getPlayers())if(!owns(p) && !(Boolean.getBoolean("bo2beta.smoke") && !Boolean.getBoolean("bo2beta.matchCheck")))confine(p);
        if(session==null)return;
        if(server.getPlayerList().getPlayer(session.host)==null){stop(server,"Host left; match ended");return;}
        long now=session.level.getGameTime();
        for(var e:session.roster)if(e.entity!=null)confine(e.entity);

        if(session.phase==Phase.COUNTDOWN){
            for(var e:session.roster)if(e.entity instanceof ServerPlayer p && p.position().distanceToSqr(e.lastSpawn)>.02)p.connection.teleport(e.lastSpawn.x,e.lastSpawn.y,e.lastSpawn.z,p.getYRot(),p.getXRot());
            if(now>=session.countdownEnd){session.phase=Phase.PLAYING;session.startTick=now;session.endTick=session.timeLimit>0?now+session.timeLimit:Long.MAX_VALUE;
                for(var e:session.roster)if(e.entity instanceof Bo2Bot bot)bot.setNoAi(false);}
        }
        if(session.phase==Phase.PLAYING && now>=session.endTick){
            if(session.mode==Mode.TDM)finish(session.teamScores[0]==session.teamScores[1]?"Draw":(session.teamScores[0]>session.teamScores[1]?"Team 1":"Team 2")+" wins");
            else{var sorted=session.roster.stream().sorted(Comparator.comparingInt((Entry e)->e.score).reversed()).toList();finish(sorted.size()>1 && sorted.get(0).score==sorted.get(1).score?"Draw":sorted.get(0).name+" wins");}
        }
        for(var e:new ArrayList<>(session.roster))if(e.deathPending && e.respawnAt>=0 && now>=e.respawnAt){
            if(e.human){var p=server.getPlayerList().getPlayer(e.id);if(p==null)continue;
                if(!p.isAlive()){p=server.getPlayerList().respawn(p,false);p.connection.player=p;e.entity=p;}entities.put(e.entity.getUUID(),e);}
            try{spawn(e);}catch(IllegalStateException ex){finish(ex.getMessage());}
        }
        // Gun Game cannot retain an old tier weapon via hotbar/drop actions.
        if(session.mode==Mode.GUN_GAME && session.phase==Phase.PLAYING)for(var e:session.roster)if(e.entity instanceof ServerPlayer p && p.isAlive()){
            String gun=GUN_LADDER.get(Math.min(e.tier,GUN_LADDER.size()-1));if(p.getInventory().getItem(0).getItem()!=Beta.GUNS.get(gun) || p.getMainHandItem().getItem() instanceof Beta.Gun held && held!=Beta.GUNS.get(gun)){e.primary=gun;equip(e);}
        }
        if(now%10==0)broadcast(server);
    }
    public static void respawned(ServerPlayer old,ServerPlayer player){
        lastArenaPosition.remove(player.getUUID());outsideTicks.remove(player.getUUID());
        var e=entities.get(old.getUUID());if(e==null || !e.deathPending || session==null)return;e.entity=player;entities.put(player.getUUID(),e);
        // The vanilla caller updates connection.player after this callback.
        // Spawn on the next server tick, after that assignment is complete.
        e.respawnAt=Math.max(session.level.getGameTime(),e.respawnAt);player.setGameMode(GameType.SPECTATOR);
    }
    public static void joined(ServerPlayer p){
        if(session==null || p.serverLevel()!=session.level || session.roster.size()>=MAX_COMBATANTS || owns(p))return;
        var e=new Entry(p);if(session.mode==Mode.TDM){long a=session.roster.stream().filter(r->r.team==0).count(),b=session.roster.stream().filter(r->r.team==1).count();e.team=a<=b?0:1;}else e.team=session.roster.size();
        session.roster.add(e);entities.put(p.getUUID(),e);try{spawn(e);}catch(IllegalStateException ex){session.roster.remove(e);entities.remove(p.getUUID());restore(p,e);message(p,ex.getMessage());}broadcast(p.server);
    }
    public static void disconnect(ServerPlayer player){
        lastArenaPosition.remove(player.getUUID());outsideTicks.remove(player.getUUID());
        if(session==null)return;
        if(session.host.equals(player.getUUID())){stop(player.server,"Host left; match ended");return;}
        var e=entities.remove(player.getUUID());if(e!=null){restore(player,e);session.roster.remove(e);broadcast(player.server);}
    }
    private static void restore(ServerPlayer p,Entry e){
        p.getInventory().clearContent();p.getInventory().load(e.inventory);p.setGameMode(e.previousMode);p.setNoGravity(e.previousNoGravity);
        p.totalExperience=e.previousXP;p.experienceLevel=e.previousLevel;p.experienceProgress=e.previousProgress;GrenadeController.cancel(p);
        p.setHealth(Math.max(1,Math.min(p.getMaxHealth(),e.previousHealth)));p.getFoodData().setFoodLevel(e.previousFood);p.getFoodData().setSaturation(e.previousSaturation);
        p.connection.teleport(e.previousPosition.x,e.previousPosition.y,e.previousPosition.z,e.previousYaw,e.previousPitch);Beta.resetWeapon(p);p.inventoryMenu.broadcastChanges();
    }
    public static boolean stop(ServerPlayer actor){
        if(session==null)return false;if(!session.host.equals(actor.getUUID()) && !actor.hasPermissions(2))return false;stop(actor.server,"Returned to lobby");return true;
    }
    public static void stop(MinecraftServer server,String reason){
        if(session==null)return;var old=session;session=null;entities.clear();
        for(var e:old.roster)if(e.human){var p=server.getPlayerList().getPlayer(e.id);if(p!=null){if(!p.isAlive()){p=server.getPlayerList().respawn(p,false);p.connection.player=p;}restore(p,e);message(p,reason);}}
            else if(e.entity!=null){lastArenaPosition.remove(e.entity.getUUID());outsideTicks.remove(e.entity.getUUID());e.entity.discard();}
        broadcast(server);
    }
    public static void melee(ServerPlayer p){
        var e=entities.get(p.getUUID());if(e==null || session==null || session.phase!=Phase.PLAYING || !p.isAlive())return;
        long now=session.level.getGameTime();if(now-e.lastMelee<20)return;e.lastMelee=now;
        Vec3 from=p.getEyePosition(),end=from.add(p.getLookAngle().scale(2.6));var hit=session.level.clip(new net.minecraft.world.level.ClipContext(from,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)end=hit.getLocation();
        LivingEntity victim=null;double nearest=from.distanceToSqr(end);for(var other:session.roster)if(other.entity!=null && target(p,other.entity)){var at=other.entity.getBoundingBox().inflate(.15).clip(from,end);if(at.isPresent() && at.get().distanceToSqr(from)<=nearest){victim=other.entity;nearest=at.get().distanceToSqr(from);}}
        p.swing(net.minecraft.world.InteractionHand.MAIN_HAND,true);if(victim!=null){victim.invulnerableTime=0;victim.hurt(new net.minecraft.world.damagesource.DamageSource(session.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(Beta.KNIFE_DAMAGE),p),CombatRules.HEALTH);}
    }
    public static void request(ServerPlayer p,String action,String value){
        if(action.equals("match_melee")){melee(p);return;}
        if(action.equals("match_state")){send(p);return;}
        if(action.equals("match_stop")){stop(p);return;}
        if(action.equals("character")){PlayerCharacters.select(p,value);return;}
        if(action.equals("match_difficulty")){if(session!=null && (session.host.equals(p.getUUID()) || p.hasPermissions(2)))try{session.difficulty=BotDifficulty.valueOf(value);for(var e:session.roster)if(e.entity instanceof Bo2Bot bot)bot.difficulty(session.difficulty);broadcast(p.server);}catch(IllegalArgumentException ex){message(p,"Invalid bot difficulty");}return;}
        if(action.equals("match_start"))try{String[] v=value.split(":");if(v.length==4 || v.length==5)start(p,Mode.valueOf(v[0]),Integer.parseInt(v[1]),Integer.parseInt(v[2]),Integer.parseInt(v[3]),v.length==5?BotDifficulty.valueOf(v[4]):BotDifficulty.REGULAR);}catch(IllegalArgumentException ex){message(p,"Invalid lobby settings");}
    }
    public static void send(ServerPlayer p){
        var state=new JsonObject();state.addProperty("phase",session==null?"LOBBY":session.phase.name());
        state.addProperty("canControl",session==null?canHost(p):session.host.equals(p.getUUID())||p.hasPermissions(2));
        if(session!=null){
            state.addProperty("difficulty",session.difficulty.name());state.addProperty("mode",session.mode.name());state.addProperty("label",session.mode.label);state.addProperty("result",session.result);
            state.addProperty("limit",session.scoreLimit);state.addProperty("team1",session.teamScores[0]);state.addProperty("team2",session.teamScores[1]);
            state.addProperty("seconds",session.phase==Phase.COUNTDOWN?Math.max(0,(session.countdownEnd-session.level.getGameTime()+19)/20):session.timeLimit==0?-1:Math.max(0,(session.endTick-session.level.getGameTime()+19)/20));
            var rows=new JsonArray();for(var e:session.roster){var r=new JsonObject();r.addProperty("id",e.id.toString());if(e.entity!=null)r.addProperty("entity",e.entity.getUUID().toString());r.addProperty("name",e.name);r.addProperty("team",e.team);r.addProperty("kills",e.kills);r.addProperty("deaths",e.deaths);r.addProperty("score",e.score);r.addProperty("tier",e.tier+1);rows.add(r);}state.add("roster",rows);
        }
        var packet=new JsonObject();packet.add("match",state);Beta.send(p,packet);
    }
    private static void broadcast(MinecraftServer server){for(var p:server.getPlayerList().getPlayers())send(p);}
    private static void message(ServerPlayer p,String text){p.displayClientMessage(Component.literal(text),false);}
}
