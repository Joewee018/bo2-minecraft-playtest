package local.bo2beta;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;
/** Real damage and AI assertions in the disposable MatchCheck world. */
public final class CombatSmoke {
    private static int stage,ticks,index,previousCount;private static volatile boolean ready;private static volatile Throwable failure;
    private static Bo2Bot bot;private static final List<String> results=new ArrayList<>();
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static ServerPlayer player(Minecraft c){return c.getSingleplayerServer().getPlayerList().getPlayers().get(0);}
    private static void task(Minecraft c,Runnable r){ready=false;c.getSingleplayerServer().execute(()->{try{r.run();}catch(Throwable t){failure=t;}finally{ready=true;}});}
    private static Vec3 clearSpot(ServerPlayer p,Bo2Bot b){
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++){var v=p.position().add(x,0,z);if(v.distanceToSqr(p.position())<16 || !MatchDirector.safe(p.serverLevel(),b,v))continue;
            if(p.serverLevel().clip(new ClipContext(p.getEyePosition(),v.add(0,b.getBbHeight()*.65,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS)return v;}
        throw new AssertionError("No clear combat fixture location");
    }
    private static BulletCombat.Result shoot(ServerPlayer p,Bo2Bot b,String gun,double height){return BulletCombat.shoot(p.serverLevel(),p,Beta.GUNS.get(gun),p.getEyePosition(),b.position().add(0,b.getBbHeight()*height,0).subtract(p.getEyePosition()),0);}
    public static boolean tick(Minecraft c){
        if(failure!=null)throw new AssertionError("Combat smoke",failure);
        if(stage==0){task(c,()->{var p=player(c);p.setGameMode(GameType.ADVENTURE);CombatRules.prepare(p);p.setHealth(200);
            check(MatchDirector.start(p,MatchDirector.Mode.TDM,2,100,0,BotDifficulty.RECRUIT),"Difficulty match start");MatchDirector.session.countdownEnd=0;MatchDirector.tick(p.server);
            for(var e:MatchDirector.session.roster){check(e.entity.getMaxHealth()==200 && e.entity.getHealth()==200,"200 HP spawn");if(e.entity instanceof Bo2Bot b){b.setNoAi(true);check(b.difficulty()==BotDifficulty.RECRUIT,"Initial difficulty");}}
            MatchDirector.request(p,"match_difficulty","HARDENED");for(var e:MatchDirector.session.roster)if(e.entity instanceof Bo2Bot b){check(b.difficulty()==BotDifficulty.HARDENED,"Live difficulty update");b.setNoAi(true);}
            var victim=MatchDirector.session.roster.stream().filter(e->!e.human && e.team!=0).findFirst().orElseThrow();bot=(Bo2Bot)victim.entity;var at=clearSpot(p,bot);bot.moveTo(at.x,at.y,at.z,0,0);bot.setNoGravity(true);
            var profile=BulletCombat.profile("ar_ak47");check(BulletCombat.damageAt(profile,0)>BulletCombat.damageAt(profile,128),"Distance falloff");
            var body=shoot(p,bot,"ar_ak47",.65);check(body.hits().size()==1 && bot.isAlive(),"Body shot must hurt without one-shotting 200 HP");float bodyDamage=200-bot.getHealth();bot.setHealth(200);
            shoot(p,bot,"ar_ak47",.9);check(200-bot.getHealth()>bodyDamage,"Head multiplier");bot.setHealth(200);bot.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));
            shoot(p,bot,"ar_ak47",.65);check(Math.abs(200-bot.getHealth()-bodyDamage)<.01,"Minecraft armor changed bullet damage");bot.setHealth(200);
            var mid=BlockPos.containing(p.getEyePosition().add(bot.position().add(0,bot.getBbHeight()*.65,0)).scale(.5));var old=p.serverLevel().getBlockState(mid);p.serverLevel().setBlock(mid,Blocks.STONE.defaultBlockState(),3);
            try{check(shoot(p,bot,"ar_ak47",.65).hits().isEmpty() && bot.getHealth()==200,"Wall obstruction");}finally{p.serverLevel().setBlock(mid,old,3);}
            var friend=MatchDirector.session.roster.stream().filter(e->!e.human && e.team==0).findFirst().orElseThrow();var health=friend.entity.getHealth();shoot(p,(Bo2Bot)friend.entity,"ar_ak47",.65);check(friend.entity.getHealth()==health,"Bullet friendly fire");
            var shotgun=BulletCombat.profile("shotty_saiga");check(shotgun.pellets()>1,"Native pellet count");bot.setHealth(200);var pellet=shoot(p,bot,"shotty_saiga",.65);check(!pellet.hits().isEmpty() && pellet.hits().get(0).damage()>BulletCombat.damageAt(shotgun,p.distanceTo(bot)),"Shotgun pellet aggregation");
            if(bot.isAlive())bot.hurt(Beta.bulletDamage(p),1000);victim.respawnAt=0;MatchDirector.tick(p.server);check(victim.entity.getHealth()==200 && ((Bo2Bot)victim.entity).difficulty()==BotDifficulty.HARDENED,"HP and difficulty after respawn");
            MatchDirector.stop(p);p.getAbilities().invulnerable=true;p.onUpdateAbilities();p.setHealth(100);CombatRules.damaged(p);p.connection.teleport(1630.5,20,-792.5,0,0);
            System.out.println("MATCH COMBAT RULES PASS: 200 HP; body/head; native range falloff; armor-independent bullets; wall obstruction; friendly fire; native shotgun pellets; difficulty update and respawn");
        });stage=1;return false;}
        if(!ready)return false;
        if(stage==1){if(index>=4){task(c,()->{var p=player(c);p.getAbilities().invulnerable=false;p.onUpdateAbilities();System.out.println("MATCH BOT DIFFICULTY PASS: "+String.join("; ",results));});stage=3;return false;}
            task(c,()->{var p=player(c);bot=Beta.BOT_ENTITY.create(p.serverLevel());CombatRules.prepare(bot);bot.configure("ar_ak47",false,p.getUUID());bot.difficulty(BotDifficulty.values()[index]);var tag=new net.minecraft.nbt.CompoundTag();bot.addAdditionalSaveData(tag);var loaded=Beta.BOT_ENTITY.create(p.serverLevel());loaded.readAdditionalSaveData(tag);check(loaded.difficulty()==BotDifficulty.values()[index],"Saved difficulty");loaded.discard();var at=clearSpot(p,bot);bot.moveTo(at.x,at.y,at.z,0,0);var a=AimAngles.of(bot.getEyePosition(),p.position().add(0,p.getBbHeight()*.68,0));bot.setYRot(a[0]);bot.setXRot(a[1]);bot.setTarget(p);bot.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);bot.setNoGravity(true);p.serverLevel().addFreshEntity(bot);});stage=2;ticks=0;return false;}
        if(stage==2){if(++ticks<110)return false;task(c,()->{if(index==0)check(player(c).getHealth()==200,"Delayed health recovery");var d=BotDifficulty.values()[index];int shots=bot.acceptedShots;check(shots>0,"No shots at "+d.label);check(bot.firstShotTick>=(d.reactionMin+49)/50,"Reaction delay bypassed "+d.label);check(bot.minShotGap>=d.shotTicks,"Bot cadence exceeded "+d.label+" gap="+bot.minShotGap);System.out.println("COMBAT DIFFICULTY SAMPLE: "+d.label+" shots="+shots+" previous="+previousCount+" first="+bot.firstShotTick+" gap="+bot.minShotGap);check(bot.minShotGap==((d.shotTicks+1)/2)*2,"Expected firing interval not observed: "+d.label+" gap="+bot.minShotGap);previousCount=shots;results.add(d.label+" shots="+shots+" firstTick="+bot.firstShotTick+" minGap="+bot.minShotGap);bot.discard();index++;});stage=1;return false;}
        return stage==3;
    }
}
