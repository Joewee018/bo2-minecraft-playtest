package local.bo2beta;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import java.util.concurrent.atomic.AtomicBoolean;

/** Disposable integration test only; never spawns a boss in normal play. */
final class WitherStormSmoke {
    private static final ResourceLocation STORM=new ResourceLocation("witherstormmod","wither_storm");
    private static final AtomicBoolean ready=new AtomicBoolean();
    private static Entity storm;
    private static int ticks,frames;
    static void start(Minecraft client){
        client.setScreen(null);
        client.options.keyUse.setDown(false);
        if(!BuiltInRegistries.ENTITY_TYPE.containsKey(STORM))throw new AssertionError("Wither Storm entity missing from registry");
        long items=BuiltInRegistries.ITEM.keySet().stream().filter(id->id.getNamespace().equals("witherstormmod")).count();
        if(items<10)throw new AssertionError("Wither Storm items missing: "+items);
        System.out.println("WITHER STORM PASS: entity registry and "+items+" items loaded");
        frames=OriginalView.renderedFrames;
        client.getSingleplayerServer().execute(()->{
            var player=client.getSingleplayerServer().getPlayerList().getPlayers().get(0);
            player.setGameMode(GameType.CREATIVE);
            player.connection.teleport(.5,210,.5,-90,0);player.setNoGravity(true);
            player.getInventory().setItem(player.getInventory().selected,new ItemStack(Beta.GUNS.get("pistol_m1911")));
            storm=BuiltInRegistries.ENTITY_TYPE.get(STORM).create(player.serverLevel());
            if(storm==null)throw new AssertionError("Wither Storm could not instantiate");
            storm.setNoGravity(true);if(storm instanceof Mob mob)mob.setNoAi(true);
            storm.moveTo(20.5,210,.5,90,0);player.serverLevel().addFreshEntity(storm);ready.set(true);
        });
    }
    static void tick(Minecraft client){
        if(++ticks>400)throw new AssertionError("Wither Storm integration test timed out");
        if(!ready.get() || ticks<100)return;
        client.player.setYRot(-90);client.player.setXRot(0);
        boolean present=false;
        for(var entity:client.level.entitiesForRendering())if(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(STORM))present=true;
        if(!present || OriginalView.renderedFrames<=frames+20)throw new AssertionError("Wither Storm client entity or BO2 view did not render");
        Screenshot.grab(client.gameDirectory,"witherstorm-bo2-integration.png",client.getMainRenderTarget(),c->System.out.println(c.getString()));
        System.out.println("WITHER STORM BO2 SMOKE COMPLETE: client boss entity and original BO2 hands coexist");
        client.getSingleplayerServer().execute(()->storm.discard());client.stop();
    }
}
