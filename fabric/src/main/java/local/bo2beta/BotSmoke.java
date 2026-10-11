package local.bo2beta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.concurrent.atomic.AtomicBoolean;

/** Disposable world integration test, only selected by the explicit bot test launcher switch. */
public final class BotSmoke {
    private static int stage,ticks,wait;
    private static final AtomicBoolean ready=new AtomicBoolean();
    private static Bo2Bot bot;
    private static Zombie target;
    private static int shots,characterIndex,poseIndex,frameStart;
    private static final java.util.List<String> characters=selectedCharacters();
    private static java.util.List<String> selectedCharacters(){
        String selection=System.getProperty("bo2beta.botCheckCharacters","");
        if(selection.isBlank())return new java.util.ArrayList<>(BotCharacters.NAMES.keySet());
        var selected=java.util.Arrays.asList(selection.split(","));
        for(String id:selected)if(!BotCharacters.valid(id))throw new IllegalArgumentException("Unknown bot check character: "+id);
        return selected;
    }
    public static void tick(Minecraft c){
        if(++ticks>6000)throw new AssertionError("Bot smoke timeout stage "+stage);
        c.options.pauseOnLostFocus=false;
        if(stage==0 && c.screen instanceof TitleScreen){c.createWorldOpenFlows().loadLevel(c.screen,"BetaSmoke");stage=1;return;}
        var server=c.getSingleplayerServer();if(server==null || c.player==null || c.level==null)return;
        if(c.screen!=null)c.setScreen(null);
        if(stage==1){c.setScreen(null);server.execute(()->{
            var p=server.getPlayerList().getPlayers().get(0);p.setGameMode(GameType.CREATIVE);p.setHealth(20);p.connection.teleport(.5,210,.5,-90,0);p.setNoGravity(true);
            for(var old:p.serverLevel().getEntitiesOfClass(Bo2Bot.class,p.getBoundingBox().inflate(128)))old.discard();
            for(int x=-3;x<=18;x++)for(int z=-4;z<=6;z++){p.serverLevel().setBlock(new BlockPos(x,209,z),Blocks.OBSIDIAN.defaultBlockState(),3);for(int y=210;y<=214;y++)p.serverLevel().setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}
            for(var old:p.serverLevel().getEntitiesOfClass(Zombie.class,p.getBoundingBox().inflate(32)))old.discard();
            bot=BotControl.spawnEntity(p,"pistol_m1911",true);if(bot==null)throw new AssertionError("Spawn control failed on open ground");bot.moveTo(5,210,2,0,0);
            target=EntityType.ZOMBIE.create(p.serverLevel());target.setNoAi(true);target.moveTo(16,210,2,0,0);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);target.setHealth(2000);p.serverLevel().addFreshEntity(target);
            var tag=new net.minecraft.nbt.CompoundTag();bot.addAdditionalSaveData(tag);var copy=Beta.BOT_ENTITY.create(p.serverLevel());copy.readAdditionalSaveData(tag);if(!copy.ally() || !copy.weapon().equals("pistol_m1911"))throw new AssertionError("Bot save roundtrip");copy.discard();
            if(!bot.validTarget(target) || bot.validTarget(p))throw new AssertionError("Ally targeting rules");
            p.setGameMode(GameType.SURVIVAL);copy.configure("ar_ak47",false,null);if(!copy.validTarget(p))throw new AssertionError("Enemy must target survival player");p.setGameMode(GameType.CREATIVE);if(copy.validTarget(p))throw new AssertionError("Enemy must ignore creative player");ready.set(true);
        });stage=2;return;}
        if(stage==2 && ready.get() && ++wait>260){
            if(BotRenderer.frames<20)throw new AssertionError("Original character did not render");
            server.execute(()->{if(bot.acceptedShots<9 || bot.reloads<1 || target.getHealth()>=2000)throw new AssertionError("Bot shooting/reload/damage: "+bot.acceptedShots+"/"+bot.reloads+" health "+target.getHealth());if(bot.nativeWalkDistance<.5)throw new AssertionError("Bot locomotion distance clock did not advance");bot.getNavigation().stop();bot.moveTo(8,210,2,0,0);bot.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);shots=bot.acceptedShots;for(int x=6;x<=10;x++)for(int z=0;z<=4;z++)for(int y=210;y<=214;y++)if(x==6 || x==10 || z==0 || z==4 || y==214)bot.level().setBlock(new BlockPos(x,y,z),Blocks.OBSIDIAN.defaultBlockState(),3);if(bot.hasLineOfSight(target))throw new AssertionError("Enclosed LOS fixture did not block target");ready.set(false);});stage=3;wait=0;return;
        }
        if(stage==3 && !ready.get() && ++wait>30){server.execute(()->{if(bot.acceptedShots>shots+1)throw new AssertionError("Bot kept firing through wall");shots=bot.acceptedShots;bot.setNoAi(true);ready.set(true);});stage=4;wait=0;return;}
        if(stage==4 && ready.get() && ++wait>30){server.execute(()->{if(bot.acceptedShots!=shots)throw new AssertionError("Bot fired through wall/noAI");bot.setNoAi(false);bot.setTarget(target);if(bot.hasLineOfSight(target))throw new AssertionError("Solid wall did not block visibility");ready.set(false);});stage=5;wait=0;return;}
        if(stage==5 && !ready.get() && ++wait>10){
            net.minecraft.client.Screenshot.grab(c.gameDirectory,c.getMainRenderTarget(),m->System.out.println("BOT SMOKE SCREENSHOT: "+m.getString()));
            server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);int count=BotControl.remove(p);target.discard();if(count<1 || !bot.isRemoved())throw new AssertionError("Bot remove failed");System.out.println("BOT SMOKE PASS: original CDC rendered; spawn permission; ally rules; NBT; Rust accepted shots="+shots+" reloads="+bot.reloads+"; damage; LOS; remove");ready.set(true);});stage=6;return;
        }
        if(stage==6 && ready.get()){
            if(characterIndex>=characters.size()){System.out.println("BOT CHARACTER DEBUG LOAD PASS: "+BotRenderer.renderedCharacters);c.stop();return;}
            ready.set(false);frameStart=BotRenderer.frames;String id=characters.get(characterIndex);
            server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);BotControl.remove(p);p.connection.teleport(2.7,210,-.3,-45,12);bot=BotControl.spawnEntity(p,poseIndex==0?"pistol_m1911":"ar_ak47",true);if(bot==null)throw new AssertionError("Character spawn failed "+id);bot.moveTo(5,210,2,135,0);bot.yBodyRot=135;bot.character(id,poseIndex+1);var tag=new net.minecraft.nbt.CompoundTag();bot.addAdditionalSaveData(tag);var copy=Beta.BOT_ENTITY.create(p.serverLevel());copy.readAdditionalSaveData(tag);if(!copy.character().equals(id) || copy.debugPose()!=poseIndex+1)throw new AssertionError("Character persistence "+id);copy.discard();ready.set(true);});stage=7;wait=0;return;
        }
        if(stage==7 && ready.get() && c.screen!=null){c.setScreen(null);wait=0;return;}
        if(stage==7 && ready.get() && ++wait>45){
            String id=characters.get(characterIndex);if(!BotRenderer.renderedCharacters.contains(id) || BotRenderer.frames-frameStart<10)throw new AssertionError("Character not rendered "+id);
            final String label=id+" pose="+(poseIndex+1);net.minecraft.client.Screenshot.grab(c.gameDirectory,c.getMainRenderTarget(),m->System.out.println("CHARACTER DEBUG SCREENSHOT "+label+": "+m.getString()));
            System.out.println("CHARACTER DEBUG LOAD PASS: "+label);
            if(++poseIndex>=5){poseIndex=0;characterIndex++;}stage=6;
        }
    }
}
