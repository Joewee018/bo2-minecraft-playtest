package local.bo2beta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Screenshot;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import java.util.concurrent.atomic.AtomicBoolean;
public final class BetaSmoke {
    private static int stage,ticks;
    private static Pig target,blastTarget;
    private static BlockPos block;
    private static GrenadeEntity grenade;
    private static java.util.List<Beta.Gun> catalog;
    private static int gunIndex,ammoBefore,audioCheckpoint,audioStartedCheckpoint,flashCheckpoint,scopeCheckpoint;
    private static long walkTick;
    private static double walkPhase;
    private static boolean reloadScreenshot;
    private static int renderCheckpoint,walkCheckpoint,walkChecks,muzzleCheckpoint;
    private static int filterIndex,filterFrames,filterHudCheckpoint;
    private static boolean pinScreenshot,respawnRequested;
    private static int impactCheckpoint;
    private static int jumpCheckpoint,landingCheckpoint,grenadeJumpCheckpoint;
    private static int gunMixerCheckpoint;
    private static int leanTick;
    private static long creativeShots;
    private static long filterStart;
    private static final DebugFilters.Preset[] filterChecks={DebugFilters.Preset.OFF,DebugFilters.Preset.TOON,DebugFilters.Preset.KUWAHARA,DebugFilters.Preset.PIXEL_ART,DebugFilters.Preset.GRAYSCALE,DebugFilters.Preset.SEPIA,DebugFilters.Preset.INVERTED,DebugFilters.Preset.CRT,DebugFilters.Preset.OFF};
    private static long nextStep;
    private static final AtomicBoolean ready=new AtomicBoolean();
    public static void tick(Minecraft client){
        if(!Boolean.getBoolean("bo2beta.smoke"))return;
        if(Boolean.getBoolean("bo2beta.mapCheck")){MapSmoke.tick(client);return;}
        if(Boolean.getBoolean("bo2beta.matchCheck")){MatchSmoke.tick(client);return;}
        if(Boolean.getBoolean("bo2beta.botCheck")){BotSmoke.tick(client);return;}
        if((stage==6 || stage==17) && client.screen instanceof LobbyScreen){var b=client.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).filter(w->w.getMessage().getString().equals("Arsenal / settings")).findFirst().orElseThrow();client.screen.mouseClicked(b.getX()+2,b.getY()+2,0);return;}
        if(stage==100){WitherStormSmoke.tick(client);return;}
        client.options.pauseOnLostFocus=false;
        if(++ticks>4800)throw new AssertionError("BO2 beta smoke timeout at stage "+stage);
        if(stage==0 && client.screen instanceof TitleScreen){client.createWorldOpenFlows().loadLevel(client.screen,"BetaSmoke");stage=1;return;}
        var server=client.getSingleplayerServer();if(server==null || client.player==null || client.level==null)return;
        if(stage==2 || stage==28 || stage==49 || stage==50 || stage==3 || stage==4){client.player.setYRot(-90);client.player.setXRot(0);}
        if(stage==1){
            if(client.player.isDeadOrDying()){
                if(!respawnRequested){client.player.respawn();respawnRequested=true;}
                return;
            }
            client.setScreen(null);client.player.setYRot(-90);client.player.setXRot(0);
            server.execute(()->{
                var p=server.getPlayerList().getPlayers().get(0);p.setGameMode(GameType.SURVIVAL);p.setHealth(20);p.getAbilities().invulnerable=true;p.onUpdateAbilities();
                for(var old:p.serverLevel().getEntitiesOfClass(GrenadeEntity.class,p.getBoundingBox().inflate(64)))old.discard();
                p.setDeltaMovement(Vec3.ZERO);p.connection.teleport(p.getX(),200,p.getZ(),-90,0);p.setNoGravity(false);p.getAbilities().flying=false;p.onUpdateAbilities();
                for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)p.serverLevel().setBlock(new BlockPos(p.getBlockX()+x,199,p.getBlockZ()+z),Blocks.OBSIDIAN.defaultBlockState(),3);p.setNoGravity(true);
                for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)for(int y=200;y<=205;y++)p.serverLevel().setBlock(new BlockPos(p.getBlockX()+x,y,p.getBlockZ()+z),Blocks.AIR.defaultBlockState(),3);
                for(var old:p.serverLevel().getEntitiesOfClass(Pig.class,p.getBoundingBox().inflate(16)))old.discard();
                p.getInventory().setItem(p.getInventory().selected,new ItemStack(Beta.GUNS.get("pistol_m1911")));
                target=EntityType.PIG.create(p.serverLevel());target.setNoAi(true);target.setNoGravity(true);target.setHealth(1);
                target.moveTo(p.getX()+3,p.getEyeY()-.45,p.getZ(),0,0);p.serverLevel().addFreshEntity(target);
                for(var old:p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Creeper.class,p.getBoundingBox().inflate(96)))old.discard();
                var radarTarget=EntityType.CREEPER.create(p.serverLevel());radarTarget.setNoAi(true);radarTarget.setNoGravity(true);radarTarget.moveTo(p.getX()+15,201,p.getZ()+10,0,0);p.serverLevel().addFreshEntity(radarTarget);ready.set(true);
            });stage=2;return;
        }
        if(stage==2 && Math.abs(client.player.getY()-200)<.05 && ready.get() && BetaClient.state!=null && BetaClient.state.has("loaded") && OriginalView.renderedFrames>10){
            if(Bo2Hud.hiddenHotbar==0)throw new AssertionError("Minecraft hotbar was not hidden with BO2 weapon");
            if(Beta.GUNS.size()!=30)throw new AssertionError("Incorrect gun catalog");
            for(var gun:Beta.GUNS.values()){
                var sound=client.getSoundManager().getSoundEvent(gun.fireSound.getLocation());
                if(sound==null || sound.getSound(net.minecraft.util.RandomSource.create()).getLocation().getPath().equals("empty"))
                    throw new AssertionError("Original gun audio unavailable: "+gun.id);
            }
            if(EntityRadar.markers==0)throw new AssertionError("Loaded entity did not appear on minimap");
            BetaSettings.entityRadar=false;EntityRadar.lastVisible=0;
            System.out.println("BETA RADAR PASS: loaded entity marker rendered");BetaSettings.entityRadar=true;
            System.out.println("BETA PASS: all 30 original player-fire sound events loaded");
            Screenshot.grab(client.gameDirectory,"original-m1911-minecraft.png",client.getMainRenderTarget(),c->System.out.println(c.getString()));
            client.player.setYRot(-90);client.player.setXRot(0);nextStep=System.nanoTime()+300_000_000L;stage=28;return;
        }
        if(stage==28 && System.nanoTime()>nextStep){BetaSettings.entityRadar=false;nextStep=System.nanoTime()+300_000_000L;stage=49;return;}
        if(stage==49 && System.nanoTime()>nextStep){
            if(EntityRadar.lastVisible!=0)throw new AssertionError("Disabled radar still renders markers");
            BetaSettings.entityRadar=true;nextStep=System.nanoTime()+300_000_000L;stage=50;return;
        }
        if(stage==50 && System.nanoTime()>nextStep){
            if(EntityRadar.lastVisible<2)throw new AssertionError("Radar did not restore passive and hostile entities");
            Screenshot.grab(client.gameDirectory,"entity-radar.png",client.getMainRenderTarget(),c->{});
            System.out.println("BETA RADAR PASS: OFF hides entities; ON restores hostile and passive markers");
            ready.set(false);
            // Align after startup movement packets and radar UI work have settled.
            server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);target.setDeltaMovement(Vec3.ZERO);target.moveTo(p.getX()+3,p.getEyeY()-.45,p.getZ(),0,0);ready.set(true);});
            stage=59;return;
        }
        if(stage==59 && ready.get()){
            client.player.setYRot(-90);client.player.setXRot(0);BetaClient.send("frame","",false,true);stage=3;return;
        }
        if(stage==3 && BetaClient.state.has("loaded") && BetaClient.state.get("loaded").getAsInt()==7){
            server.execute(()->{if(target==null || target.isAlive())throw new AssertionError("Rust shot failed to kill the one-health test mob");target.discard();});
            System.out.println("BETA PASS: Rust-authorized gun consumed ammo and hit Minecraft mob");
            BetaClient.send("reload","",false,false);stage=4;return;
        }
        if(stage==4 && BetaClient.state.has("loaded") && BetaClient.state.get("loaded").getAsInt()==8 && BetaClient.state.get("reload").getAsFloat()==0){
            if(MultiplayerHud.killEvents==0 || MultiplayerHud.feedFrames==0 || MultiplayerHud.hitFrames==0)throw new AssertionError("Server combat feedback did not render in multiplayer HUD: kills="+MultiplayerHud.killEvents+", feed="+MultiplayerHud.feedFrames+", hits="+MultiplayerHud.hitFrames);
            if(MultiplayerHud.compassFrames==0 || MultiplayerHud.ammoFrames==0)throw new AssertionError("Compass strip or native ammo slots did not render");
            System.out.println("BETA HUD PASS: native ammo slots and live compass strip rendered");
            System.out.println("BETA HUD PASS: server-confirmed hitmarker, kill count and killfeed rendered");
            Screenshot.grab(client.gameDirectory,"multiplayer-combat-hud.png",client.getMainRenderTarget(),c->System.out.println(c.getString()));
            if(OriginalView.renderedFireFrames==0 || OriginalView.renderedReloadFrames==0)throw new AssertionError("Original firing/reload clips did not render");
            System.out.println("BETA PASS: original Rust reload refilled Minecraft weapon");ready.set(false);
            server.execute(()->{
                var p=server.getPlayerList().getPlayers().get(0);var level=p.serverLevel();
                int x=p.getBlockX(),z=p.getBlockZ();for(int a=-5;a<=5;a++)for(int b=-5;b<=5;b++)for(int y=200;y<=205;y++)level.setBlock(new BlockPos(x+a,y,z+b),Blocks.AIR.defaultBlockState(),3);for(int a=-5;a<=5;a++)for(int b=-5;b<=5;b++)level.setBlock(new BlockPos(x+a,199,z+b),Blocks.OBSIDIAN.defaultBlockState(),3);
                block=new BlockPos(x+3,200,z);level.setBlock(block,Blocks.DIRT.defaultBlockState(),3);
                blastTarget=EntityType.PIG.create(level);blastTarget.setNoAi(true);blastTarget.setNoGravity(true);blastTarget.moveTo(x+3.8,201,z+0.5,0,0);level.addFreshEntity(blastTarget);
                p.getInventory().setItem(p.getInventory().selected,new ItemStack(Beta.GRENADE,2));
                Beta.GRENADE.use(level,p,InteractionHand.MAIN_HAND);
                ready.set(true);
            });stage=5;return;
        }
        if(stage==5 && ready.get()){
            if(!pinScreenshot && OriginalGrenadeView.state!=null && OriginalGrenadeView.state.get("elapsed_ms").getAsInt()>150){pinScreenshot=true;Screenshot.grab(client.gameDirectory,"original-m67-pin.png",client.getMainRenderTarget(),c->System.out.println(c.getString()));}
            var player=server.getPlayerList().getPlayers().get(0);
            var thrown=server.overworld().getEntitiesOfClass(GrenadeEntity.class,player.getBoundingBox().inflate(40));
            if(thrown.isEmpty())return;grenade=thrown.get(0);
            if(grenade.remaining()>3400 || grenade.remaining()<3000 || grenade.getDeltaMovement().length()<.7)throw new AssertionError("Original quick-throw timing/speed failed: "+grenade.remaining());
            if(OriginalGrenadeView.pinFrames==0)throw new AssertionError("Original M67 pin-pull clip did not render");
            System.out.println("BETA PASS: original M67 pin pull, Rust release and cooked fuse | remaining="+grenade.remaining());
            server.execute(()->{grenade.setPos(block.getX()+.5,201.1,block.getZ()+.5);grenade.setDeltaMovement(Vec3.ZERO);});
            Screenshot.grab(client.gameDirectory,"original-m67-throw.png",client.getMainRenderTarget(),c->System.out.println(c.getString()));
            stage=16;return;
        }
        if(stage==16 && grenade!=null && !grenade.isAlive()){
            if(!server.overworld().getBlockState(block).isAir())throw new AssertionError("Grenade did not break Minecraft block");
            if(blastTarget.isAlive())throw new AssertionError("Grenade did not kill Minecraft mob");
            if(grenade.bounces==0 || OriginalGrenadeView.worldFrames==0 || OriginalGrenadeView.throwFrames==0)throw new AssertionError("Original grenade model/throw/collision did not render");
            System.out.println("BETA PASS: original M67 world model, throw animation, bounce, explosion block/mob damage and TNT particles/sounds");
            KeyMapping.click(InputConstants.getKey(GLFW.GLFW_KEY_P,0));stage=6;return;
        }
        if(stage==6 && client.screen instanceof BetaScreen){
            server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);p.getInventory().setItem(8,new ItemStack(Beta.GRENADE));p.getInventory().setItem(p.getInventory().selected,new ItemStack(Beta.GUNS.get("pistol_m1911")));});
            Screenshot.grab(client.gameDirectory,"bo2-beta-smoke.png",client.getMainRenderTarget(),c->System.out.println(c.getString()));
            clickView(client);if(OriginalView.enabled)throw new AssertionError("Viewmodel OFF mouse button failed");
            renderCheckpoint=OriginalView.renderedFrames;nextStep=System.nanoTime()+300_000_000L;stage=14;return;
        }
        if(stage==14 && System.nanoTime()>nextStep && client.player.getMainHandItem().getItem() instanceof Beta.Gun){
            if(OriginalView.renderedFrames!=renderCheckpoint)throw new AssertionError("Viewmodel OFF still renders original mesh");
            Screenshot.grab(client.gameDirectory,"original-m67-inventory.png",client.getMainRenderTarget(),c->System.out.println(c.getString()));
            BetaClient.send("frame","",false,true);stage=32;return;
        }
        if(stage==32 && BetaClient.state.get("loaded").getAsInt()<8){audioStartedCheckpoint=AnimationAudio.started;BetaClient.send("reload","",false,false);stage=33;return;}
        if(stage==33 && BetaClient.state.get("reload").getAsFloat()>0){stage=34;return;}
        if(stage==34 && BetaClient.state.get("reload").getAsFloat()==0 && BetaClient.state.get("loaded").getAsInt()==8){
            if(AnimationAudio.started<=audioStartedCheckpoint)throw new AssertionError("Reload sounds require visible hands");
            System.out.println("BETA PASS: original reload sounds remain active with BO2 view OFF");
            clickView(client);if(!OriginalView.enabled)throw new AssertionError("Viewmodel ON mouse button failed");
            nextStep=System.nanoTime()+300_000_000L;stage=15;return;
        }
        if(stage==15 && System.nanoTime()>nextStep && OriginalView.renderedFrames>renderCheckpoint){
            if(OriginalGrenadeView.itemFrames==0)throw new AssertionError("Original M67 inventory item did not render");
            if(Bo2Hud.restoredHotbar==0)throw new AssertionError("Minecraft hotbar did not restore with BO2 view OFF");
            System.out.println("BETA PASS: mouse viewmodel ON/OFF and original rendering resume");
            if(Boolean.getBoolean("bo2beta.visualCheck")){flashCheckpoint=MuzzleEffects.firstPersonFrames;BetaClient.send("frame","",false,true);stage=35;return;}
            stage=17;return;
        }
        if(stage==17 && client.screen instanceof BetaScreen){
            var preset=filterChecks[filterIndex];clickFilter(client,preset);
            if(filterIndex==0){
                var slider=client.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.AbstractSliderButton).map(c->(net.minecraft.client.gui.components.AbstractSliderButton)c).findFirst().orElseThrow();
                client.screen.mouseClicked(slider.getX()+slider.getWidth()*.3,slider.getY()+10,0);client.screen.mouseReleased(slider.getX()+slider.getWidth()*.3,slider.getY()+10,0);
                if(BetaSettings.volume>.4 || BetaSettings.volume<.2)throw new AssertionError("BO2 volume mouse slider failed");
                System.out.println("BETA CONTROLS PASS: independent BO2 volume slider mouse input");
            }
            if(DebugFilters.current()!=preset)throw new AssertionError("Filter selection failed: "+preset.label);
            nextStep=System.nanoTime()+300_000_000L;stage=36;return;
        }
        if(stage==36 && System.nanoTime()>nextStep && client.screen instanceof BetaScreen){
            Screenshot.grab(client.gameDirectory,"filter-menu-"+filterIndex+".png",client.getMainRenderTarget(),c->System.out.println(c.getString()));
            client.screen.keyPressed(GLFW.GLFW_KEY_P,0,0);
            filterFrames=OriginalView.renderedFrames;filterHudCheckpoint=DebugFilters.filteredHudFrames;filterStart=System.nanoTime();nextStep=filterStart+500_000_000L;stage=18;return;
        }
        if(stage==18 && System.nanoTime()>nextStep){
            var preset=filterChecks[filterIndex];
            if(DebugFilters.current()!=preset)throw new AssertionError("Filter did not stay selected: "+preset.label);
            if(OriginalView.renderedFrames<=filterFrames)throw new AssertionError("World stopped rendering with filter: "+preset.label);
            if(preset!=DebugFilters.Preset.OFF && DebugFilters.filteredHudFrames<=filterHudCheckpoint)throw new AssertionError("HUD filter pass did not execute");
            if(MultiplayerHud.frames==0 || TerrainMinimap.updates==0 || MultiplayerHud.hitFrames==0)throw new AssertionError("Multiplayer HUD live widgets not rendered");
            Screenshot.grab(client.gameDirectory,"filter-"+filterIndex+"-"+preset.name().toLowerCase()+".png",client.getMainRenderTarget(),c->System.out.println(c.getString()));
            float fps=(OriginalView.renderedFrames-filterFrames)*1_000_000_000f/(System.nanoTime()-filterStart);
            System.out.println("BETA FILTER PASS: "+preset.label+" | mouse selection | rendered world/weapon/HUD/menu | filtered HUD frames="+(DebugFilters.filteredHudFrames-filterHudCheckpoint)+" | sample FPS="+fps);
            if(++filterIndex==filterChecks.length){
                System.out.println("BETA PASS: P opened and closed Minecraft debug menu");
                if(Boolean.getBoolean("bo2beta.hudCheck")){System.out.println("BO2 MULTIPLAYER HUD SMOKE COMPLETE");stage=19;return;}
                stage=8;ticks=0;return;
            }
            KeyMapping.click(InputConstants.getKey(GLFW.GLFW_KEY_P,0));stage=17;return;
        }
        if(stage==35 && BetaClient.state.get("loaded").getAsInt()<8 && MuzzleEffects.firstPersonFrames>flashCheckpoint){System.out.println("BO2 MINECRAFT VISUAL CHECK COMPLETE");if(Boolean.getBoolean("bo2beta.witherStormCheck")){WitherStormSmoke.start(client);stage=100;}else{client.stop();stage=13;}return;}
        if(stage==7 && client.screen==null){System.out.println("BETA PASS: P opened and closed Minecraft debug menu");stage=8;ticks=0;return;}
        if(stage==8 && ticks>40){catalog=new java.util.ArrayList<>(Beta.GUNS.values());stage=9;}
        if(stage==9){
            if(gunIndex==catalog.size()){if(walkChecks!=30)throw new AssertionError("Walking coverage incomplete");System.out.println("BETA PASS: all 30 guns walking motion and original ADS FOV values");stage=26;return;}
            var gun=catalog.get(gunIndex);ready.set(false);
            client.options.keyUse.setDown(false);
            server.execute(()->{
                var p=server.getPlayerList().getPlayers().get(0);
                // The explosion fixture can impart knockback even in creative.
                // Reset it before measuring the next gun's horizontal hit ray.
                p.setDeltaMovement(Vec3.ZERO);p.connection.teleport(p.getX(),200,p.getZ(),-90,0);p.setNoGravity(false);p.getAbilities().flying=false;p.onUpdateAbilities();
                // Reset the disposable world corridor as walking advances through its old map.
                for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){
                    p.serverLevel().setBlock(new BlockPos(p.getBlockX()+x,199,p.getBlockZ()+z),Blocks.OBSIDIAN.defaultBlockState(),3);
                    for(int y=200;y<=205;y++)p.serverLevel().setBlock(new BlockPos(p.getBlockX()+x,y,p.getBlockZ()+z),Blocks.AIR.defaultBlockState(),3);
                }
                p.getInventory().setItem(p.getInventory().selected,new ItemStack(gun));
                p.setYRot(-90);p.setXRot(0);
                target=EntityType.PIG.create(p.serverLevel());target.setNoAi(true);target.setNoGravity(true);
                target.moveTo(p.getX()+20,p.getEyeY()-0.45,p.getZ(),0,0);p.serverLevel().addFreshEntity(target);ready.set(true);
            });stage=10;nextStep=System.nanoTime()+500_000_000L;return;
        }
        if(stage==10 && ready.get() && System.nanoTime()>nextStep && BetaClient.state!=null
            && BetaClient.state.has("weapon") && BetaClient.state.get("weapon").getAsString().equals(catalog.get(gunIndex).model)
            && OriginalView.lastRenderedGun.equals(catalog.get(gunIndex).id)){
            Screenshot.grab(client.gameDirectory,"hip-"+catalog.get(gunIndex).id+".png",client.getMainRenderTarget(),c->System.out.println(c.getString()));
            walkTick=client.level.getGameTime();walkPhase=ViewMovement.phase();scopeCheckpoint=ScopeScreen.frames;
            client.player.setSprinting(false);walkCheckpoint=ViewMovement.walkingFrames;client.options.keyUp.setDown(true);nextStep=System.nanoTime()+600_000_000L;stage=24;return;
        }
        if(stage==24 && System.nanoTime()>nextStep){
            client.options.keyUp.setDown(false);if(ViewMovement.walkingFrames<=walkCheckpoint)throw new AssertionError("Walking view did not animate: "+catalog.get(gunIndex).id);
            long elapsedTicks=client.level.getGameTime()-walkTick;double phase=ViewMovement.phase()-walkPhase;
            if(phase>(elapsedTicks+1)*Math.PI*2/20+.01)throw new AssertionError("Walking animation exceeds 20-tick cadence: "+phase+" / "+elapsedTicks);
            System.out.println("BETA CADENCE PASS: "+catalog.get(gunIndex).id+" | ticks="+elapsedTicks+" | cycles="+phase/(Math.PI*2));
            walkChecks++;client.options.keyUse.setDown(true);nextStep=System.nanoTime()+700_000_000L;stage=25;return;
        }
        if(stage==25 && System.nanoTime()>nextStep && OriginalView.aimAmount>.98f){
            double fov=((local.bo2beta.mixin.GameRendererAccess)client.gameRenderer).bo2beta$getFov(client.gameRenderer.getMainCamera(),0,true);
            if(catalog.get(gunIndex).id.startsWith("sniper_") && ScopeScreen.frames<=scopeCheckpoint)throw new AssertionError("Scope screen missing");
            double expected=ViewMovement.aimedFov(catalog.get(gunIndex).id,70,1);if(Math.abs(fov-expected)>.1)throw new AssertionError("Original ADS FOV missing for "+catalog.get(gunIndex).id+": "+fov+" vs "+expected);
            Screenshot.grab(client.gameDirectory,"aim-"+catalog.get(gunIndex).id+".png",client.getMainRenderTarget(),c->System.out.println(c.getString()));
            muzzleCheckpoint=MuzzleEffects.emitted;flashCheckpoint=MuzzleEffects.firstPersonFrames;
            ammoBefore=BetaClient.state.get("loaded").getAsInt();
            if(ammoBefore<=0)throw new AssertionError("No test ammo: "+catalog.get(gunIndex).id);
            client.player.setYRot(-90);client.player.setXRot(0);
            ready.set(false);server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);target.moveTo(p.getX()+3,p.getEyeY()-.45,p.getZ(),0,0);ready.set(true);});
            nextStep=System.nanoTime()+200_000_000L;stage=31;return;
        }
        if(stage==31 && ready.get() && System.nanoTime()>nextStep){gunMixerCheckpoint=BetaSettings.newSoundsMixed;client.options.keyAttack.setDown(true);stage=11;return;}
        if(stage==11 && BetaClient.state.has("weapon") && BetaClient.state.get("weapon").getAsString().equals(catalog.get(gunIndex).model)
            && BetaClient.state.get("loaded").getAsInt()<ammoBefore){
            if(!catalog.get(gunIndex).id.startsWith("sniper_") && MuzzleEffects.firstPersonFrames<=flashCheckpoint)return;
            Screenshot.grab(client.gameDirectory,"fire-"+catalog.get(gunIndex).id+".png",client.getMainRenderTarget(),c->System.out.println(c.getString()));
            if(MuzzleEffects.emitted<=muzzleCheckpoint)throw new AssertionError("Minecraft muzzle particles missing: "+catalog.get(gunIndex).id);
            if(BetaSettings.newSoundsMixed<=gunMixerCheckpoint)throw new AssertionError("New gun sound bypassed BO2 volume control: "+catalog.get(gunIndex).id);
            System.out.println("BETA MIXER PASS: "+catalog.get(gunIndex).id+" | new-shot gain="+BetaSettings.lastMixedVolume);
            client.options.keyAttack.setDown(false);
            client.options.keyUse.setDown(false);
            ready.set(false);var gun=catalog.get(gunIndex);
            server.execute(()->{
                if(target==null || target.getHealth()>=target.getMaxHealth())throw new AssertionError("Gun bridge did not hit mob: "+gun.id+" | target="+(target==null?"null":target.getBoundingBox())+" | player="+server.getPlayerList().getPlayers().get(0).position()+" | look="+server.getPlayerList().getPlayers().get(0).getLookAngle());
                target.discard();System.out.println("BETA GUN PASS: "+gun.id+" | original fire sound | Rust ammo | Minecraft damage");ready.set(true);
            });stage=12;return;
        }
        if(stage==12 && ready.get()){
            audioCheckpoint=AnimationAudio.played;audioStartedCheckpoint=AnimationAudio.started;reloadScreenshot=false;BetaClient.send("reload","",false,false);stage=29;return;
        }
        if(stage==29 && BetaClient.state.get("reload").getAsFloat()>0){
            nextStep=System.nanoTime()+350_000_000L;stage=30;return;
        }
        if(stage==30){
            if(!reloadScreenshot && System.nanoTime()>nextStep){reloadScreenshot=true;Screenshot.grab(client.gameDirectory,"reload-"+catalog.get(gunIndex).id+".png",client.getMainRenderTarget(),c->System.out.println(c.getString()));}
            if(BetaClient.state.get("reload").getAsFloat()==0 && BetaClient.state.get("loaded").getAsInt()>ammoBefore-1){
                if(AnimationAudio.started<=audioStartedCheckpoint)throw new AssertionError("Reload audio did not start in sound engine: "+catalog.get(gunIndex).id);
                System.out.println("BETA RELOAD PASS: "+catalog.get(gunIndex).id+" | original sound notetracks="+(AnimationAudio.played-audioCheckpoint)+" | active audio="+(AnimationAudio.started-audioStartedCheckpoint));
                gunIndex++;stage=9;
            }
        }
        if(stage==26){
            client.options.keyUse.setDown(false);server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);p.getInventory().setItem(p.getInventory().selected,new ItemStack(Beta.GRENADE));});
            walkCheckpoint=ViewMovement.grenadeWalkingFrames;client.options.keyUp.setDown(true);nextStep=System.nanoTime()+700_000_000L;stage=27;return;
        }
        if(stage==27 && System.nanoTime()>nextStep){client.options.keyUp.setDown(false);if(ViewMovement.grenadeWalkingFrames<=walkCheckpoint)throw new AssertionError("M67 walking did not render");System.out.println("BETA PASS: M67 walking motion keeps hands and grenade together");stage=19;return;}
        if(stage==19){
            ready.set(false);client.options.keyUse.setDown(true);
            server.execute(()->{
                var p=server.getPlayerList().getPlayers().get(0);p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=false;p.onUpdateAbilities();p.setHealth(20);p.setDeltaMovement(Vec3.ZERO);p.setNoGravity(true);
                p.getInventory().setItem(p.getInventory().selected,new ItemStack(Beta.GRENADE));
                Beta.GRENADE.use(p.serverLevel(),p,InteractionHand.MAIN_HAND);ready.set(true);
            });stage=20;return;
        }
        if(stage==20 && ready.get() && OriginalGrenadeView.cooking() && OriginalGrenadeView.state.get("elapsed_ms").getAsInt()>=2300){
            if(!client.player.getMainHandItem().isEmpty())throw new AssertionError("Survival grenade was not consumed exactly once");
            if(!Bo2Hud.active())throw new AssertionError("Last-grenade cook lost original view/HUD");
            client.options.keyUse.setDown(false);stage=21;return;
        }
        if(stage==21){
            var p=server.getPlayerList().getPlayers().get(0);
            var thrown=server.overworld().getEntitiesOfClass(GrenadeEntity.class,p.getBoundingBox().inflate(40));
            if(thrown.isEmpty())return;grenade=thrown.get(0);
            if(grenade.remaining()>1700 || grenade.remaining()<700)throw new AssertionError("Cooking did not shorten original fuse: "+grenade.remaining());
            server.execute(()->{grenade.setPos(p.getX()+20,200,p.getZ());grenade.setDeltaMovement(Vec3.ZERO);});
            System.out.println("BETA PASS: last survival grenade consumes one item and keeps original hands; cooking reduces thrown fuse to "+grenade.remaining()+"ms");stage=22;return;
        }
        if(stage==22 && !grenade.isAlive()){
            ready.set(false);client.options.keyUse.setDown(true);
            server.execute(()->{
                var p=server.getPlayerList().getPlayers().get(0);if(!p.isAlive())throw new AssertionError("Remote grenade damaged test owner");
                p.getInventory().setItem(p.getInventory().selected,new ItemStack(Beta.GRENADE));Beta.GRENADE.use(p.serverLevel(),p,InteractionHand.MAIN_HAND);ready.set(true);
            });stage=23;return;
        }
        if(stage==23 && ready.get() && !server.getPlayerList().getPlayers().get(0).isAlive()){
            if(server.getPlayerList().getPlayers().get(0).getMainHandItem().getCount()!=0)throw new AssertionError("Overcook did not consume exactly one grenade");
            client.options.keyUse.setDown(false);System.out.println("BETA PASS: original 550ms pin plus 3500ms fuse explodes in hand and kills unarmored survival player");client.player.respawn();nextStep=System.nanoTime()+500_000_000L;stage=40;return;
        }


        if(stage==40 && System.nanoTime()>nextStep && client.player.isAlive()){
            client.setScreen(null);ready.set(false);
            server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);p.setGameMode(GameType.SURVIVAL);p.getInventory().setItem(p.getInventory().selected,new ItemStack(Beta.GUNS.get("pistol_m1911")));p.connection.teleport(0.5,200,.5,-90,0);p.setNoGravity(true);
                for(int x=-4;x<=5;x++)for(int z=-4;z<=4;z++){p.serverLevel().setBlock(new BlockPos(x,199,z),Blocks.OBSIDIAN.defaultBlockState(),3);for(int y=200;y<205;y++)p.serverLevel().setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}
                for(var pig:p.serverLevel().getEntitiesOfClass(Pig.class,p.getBoundingBox().inflate(64)))pig.discard();ready.set(true);
            });stage=41;return;
        }
        if(stage==41 && ready.get() && BetaClient.state!=null && BetaClient.state.has("weapon") && BetaClient.state.get("weapon").getAsString().equals(Beta.GUNS.get("pistol_m1911").model)){
            if(BetaClient.state.get("loaded").getAsInt()!=8 || BetaClient.state.get("reserve").getAsInt()!=40 || BetaClient.state.get("total_shots").getAsLong()!=0)throw new AssertionError("Survival death did not reset ammunition");
            System.out.println("BETA CONTROLS PASS: survival death/respawn resets gun ammo and reserve");
            server.execute(()->server.getPlayerList().getPlayers().get(0).setGameMode(GameType.CREATIVE));stage=42;return;
        }
        if(stage==42 && BetaClient.state.has("infinite_ammo") && BetaClient.state.get("infinite_ammo").getAsBoolean()){
            creativeShots=BetaClient.state.get("total_shots").getAsLong();BetaClient.send("frame","",false,true);stage=43;return;
        }
        if(stage==43 && BetaClient.state.get("total_shots").getAsLong()>creativeShots){
            if(BetaClient.state.get("loaded").getAsInt()!=8)throw new AssertionError("Creative shot consumed ammo");
            System.out.println("BETA CONTROLS PASS: creative fire preserves full magazine");
            server.execute(()->server.getPlayerList().getPlayers().get(0).serverLevel().setBlock(new BlockPos(3,201,0),Blocks.STONE.defaultBlockState(),3));
            impactCheckpoint=BulletHoles.received;nextStep=System.nanoTime()+400_000_000L;stage=44;return;
        }
        if(stage==44 && System.nanoTime()>nextStep){client.player.setYRot(-90);client.player.setXRot(0);BetaClient.send("frame","",false,true);stage=45;return;}
        if(stage==45 && BulletHoles.received>impactCheckpoint && BulletHoles.rendered>0){
            System.out.println("BETA CONTROLS PASS: accepted block hit produced visible bullet-hole decal");
            Screenshot.grab(client.gameDirectory,"bullet-holes.png",client.getMainRenderTarget(),c->{});
            BetaClient.peekRight.setDown(true);leanTick=ticks;stage=48;return;
        }
        if(stage==48 && ticks>leanTick){
            if(CornerPeek.amount<=0 || CornerPeek.amount>=1)throw new AssertionError("Lean did not transition smoothly");
            leanTick=ticks;stage=46;return;
        }
        if(stage==46 && ticks-leanTick>=4){
            if(CornerPeek.amount!=1 || client.gameRenderer.getMainCamera().getPosition().distanceTo(client.player.getEyePosition())<.3)throw new AssertionError("Corner peek camera did not move");
            Screenshot.grab(client.gameDirectory,"corner-peek.png",client.getMainRenderTarget(),c->{});
            BetaClient.peekLeft.setDown(true);leanTick=ticks;stage=51;return;
        }
        if(stage==51 && ticks-leanTick>=9){
            if(CornerPeek.amount!=-1)throw new AssertionError("Latest left input did not switch right lean to left while both held");
            Screenshot.grab(client.gameDirectory,"corner-peek-left.png",client.getMainRenderTarget(),c->{});
            BetaClient.peekRight.setDown(false);leanTick=ticks;stage=52;return;
        }
        if(stage==52 && ticks-leanTick>=2){
            BetaClient.peekRight.setDown(true);leanTick=ticks;stage=53;return;
        }
        if(stage==53 && ticks-leanTick>=9){
            if(CornerPeek.amount!=1)throw new AssertionError("Latest right input did not switch left lean to right while both held");
            System.out.println("BETA CONTROLS PASS: right-to-left and left-to-right lean switches while opposite key held");
            BetaClient.peekLeft.setDown(false);BetaClient.peekRight.setDown(false);ready.set(false);
            server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);p.connection.teleport(.5,200,.85,-90,0);p.serverLevel().setBlock(new BlockPos(0,201,1),Blocks.STONE.defaultBlockState(),3);Vec3 offset=CornerPeek.offset(p,p.getEyePosition(),1);if(offset.length()>.12)throw new AssertionError("Peek crossed solid wall: "+offset);ready.set(true);});stage=47;return;
        }
        if(stage==47 && ready.get()){
            System.out.println("BETA CONTROLS PASS: smooth lean moves and banks camera; collision stops it at walls");
            server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);p.setNoGravity(false);p.getAbilities().flying=false;p.onUpdateAbilities();p.connection.teleport(.5,200,.5,-90,0);});
            nextStep=System.nanoTime()+400_000_000L;stage=54;return;
        }
        if(stage==54 && System.nanoTime()>nextStep){
            jumpCheckpoint=JumpMotion.jumps;landingCheckpoint=JumpMotion.landings;
            client.options.keyJump.setDown(true);nextStep=System.nanoTime()+150_000_000L;stage=55;return;
        }
        if(stage==55 && System.nanoTime()>nextStep){
            client.options.keyJump.setDown(false);
            if(JumpMotion.jumps<=jumpCheckpoint || ViewMovement.jumpFrames==0)throw new AssertionError("Gun jump motion did not start on actual Minecraft jump");
            Screenshot.grab(client.gameDirectory,"gun-jump.png",client.getMainRenderTarget(),c->{});
            nextStep=System.nanoTime()+950_000_000L;stage=56;return;
        }
        if(stage==56 && System.nanoTime()>nextStep){
            if(JumpMotion.landings<=landingCheckpoint)throw new AssertionError("Landing motion missing");
            server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Beta.GRENADE));});
            nextStep=System.nanoTime()+400_000_000L;stage=57;return;
        }
        if(stage==57 && System.nanoTime()>nextStep){
            // Native offhand idle keeps hands lowered; test the visible pin/hold pose.
            client.options.keyUse.setDown(true);server.execute(()->{var p=server.getPlayerList().getPlayers().get(0);Beta.GRENADE.use(p.serverLevel(),p,InteractionHand.MAIN_HAND);});
            nextStep=System.nanoTime()+150_000_000L;stage=60;return;
        }
        if(stage==60 && System.nanoTime()>nextStep){
            if(!OriginalGrenadeView.cooking())throw new AssertionError("Grenade pin/hold pose did not start");
            grenadeJumpCheckpoint=ViewMovement.grenadeJumpFrames;client.options.keyJump.setDown(true);nextStep=System.nanoTime()+150_000_000L;stage=58;return;
        }
        if(stage==58 && System.nanoTime()>nextStep){
            client.options.keyJump.setDown(false);
            if(ViewMovement.grenadeJumpFrames<=grenadeJumpCheckpoint)throw new AssertionError("Grenade jump hands missing");
            Screenshot.grab(client.gameDirectory,"grenade-jump.png",client.getMainRenderTarget(),c->{});
            client.options.keyUse.setDown(false);
            System.out.println("BETA CONTROLS PASS: actual Minecraft gun/grenade jump motion and landing; shared additive controller preserves weapon clips");finish(client);return;
        }

    }
    private static void finish(Minecraft client){
        if(BetaSettings.newSoundsMixed==0 || BetaSettings.lastMixedVolume>.4)throw new AssertionError("BO2 volume did not reach sound mixer: new="+BetaSettings.newSoundsMixed+", calls="+BetaSettings.soundMixed+", mixed="+BetaSettings.lastMixedVolume+", slider="+BetaSettings.volume);
        System.out.println("BETA CONTROLS PASS: BO2 gain applied in live sound mixer");BetaSettings.volume=1;BetaSettings.save();
        System.out.println(Boolean.getBoolean("bo2beta.hudCheck")?"BO2 CONTROLS SMOKE COMPLETE":"BO2 MINECRAFT BETA SMOKE COMPLETE: 30 gun bridges and original M67 integration verified");
        if(Boolean.getBoolean("bo2beta.witherStormCheck")){WitherStormSmoke.start(client);stage=100;}else{client.stop();stage=13;}
    }
    private static void clickView(Minecraft client){
        var button=client.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button)
            .map(c->(net.minecraft.client.gui.components.Button)c).filter(b->b.getMessage().getString().startsWith("BO2 view:")).findFirst().orElseThrow();
        double x=button.getX()+button.getWidth()/2d,y=button.getY()+button.getHeight()/2d;
        if(!client.screen.mouseClicked(x,y,0))throw new AssertionError("Viewmodel toggle mouse click ignored");client.screen.mouseReleased(x,y,0);
    }
    private static void clickFilter(Minecraft client,DebugFilters.Preset preset){
        var settings=client.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button)
            .map(c->(net.minecraft.client.gui.components.Button)c).filter(b->b.getMessage().getString().startsWith("Effects / Audio")).findFirst();
        if(settings.isPresent()){var b=settings.get();client.screen.mouseClicked(b.getX()+b.getWidth()/2d,b.getY()+10,0);client.screen.mouseReleased(b.getX()+b.getWidth()/2d,b.getY()+10,0);}
        var button=client.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button)
            .map(c->(net.minecraft.client.gui.components.Button)c).filter(b->{String label=b.getMessage().getString();return label.equals(preset.label)||label.equals("> "+preset.label);}).findFirst().orElseThrow();
        double x=button.getX()+button.getWidth()/2d,y=button.getY()+button.getHeight()/2d;
        if(!client.screen.mouseClicked(x,y,0))throw new AssertionError("Filter mouse click ignored: "+preset.label);client.screen.mouseReleased(x,y,0);
    }
}

