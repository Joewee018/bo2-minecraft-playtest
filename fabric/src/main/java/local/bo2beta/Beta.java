package local.bo2beta;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraft.sounds.*;

public final class Beta implements ModInitializer {
    public static final ResourceLocation INPUT=new ResourceLocation("bo2beta","input"),STATE=new ResourceLocation("bo2beta","state");
    public static final SoundEvent GRENADE_PIN=Registry.register(BuiltInRegistries.SOUND_EVENT,new ResourceLocation("bo2beta","grenade.pin"),SoundEvent.createVariableRangeEvent(new ResourceLocation("bo2beta","grenade.pin")));
    public static final SoundEvent GRENADE_BOUNCE=Registry.register(BuiltInRegistries.SOUND_EVENT,new ResourceLocation("bo2beta","grenade.bounce"),SoundEvent.createVariableRangeEvent(new ResourceLocation("bo2beta","grenade.bounce")));
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> KNIFE_DAMAGE=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,new ResourceLocation("bo2beta","knife"));
    static Bridge botBridge;
    static net.minecraft.world.damagesource.DamageSource bulletDamage(net.minecraft.world.entity.LivingEntity shooter){var key=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,new ResourceLocation("bo2beta","bullet"));return new net.minecraft.world.damagesource.DamageSource(shooter.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getHolderOrThrow(key),shooter);}

    public static final EntityType<Bo2Bot> BOT_ENTITY=Registry.register(BuiltInRegistries.ENTITY_TYPE,new ResourceLocation("bo2beta","bot"),EntityType.Builder.<Bo2Bot>of(Bo2Bot::new,MobCategory.MONSTER).sized(.6f,1.8f).clientTrackingRange(10).updateInterval(2).build("bo2beta:bot"));
    public static final Map<String,Gun> GUNS=new LinkedHashMap<>();
    public static final EntityType<GrenadeEntity> GRENADE_ENTITY=Registry.register(BuiltInRegistries.ENTITY_TYPE,new ResourceLocation("bo2beta","m67"), EntityType.Builder.<GrenadeEntity>of(GrenadeEntity::new,MobCategory.MISC).sized(.15f,.15f).clientTrackingRange(8).updateInterval(1).build("bo2beta:m67"));
    public static final Item GRENADE=new Item(new Item.Properties().stacksTo(2)) {
        @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
            if(!level.isClientSide) {
                if(hand!=InteractionHand.MAIN_HAND || !GrenadeController.begin((ServerPlayer)player,player.getItemInHand(hand)))return InteractionResultHolder.fail(player.getItemInHand(hand));
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
        }
    };
    public static final class Gun extends Item {
        public final String id,model;public final float damage;public final SoundEvent fireSound;
        public Gun(JsonObject p){
            super(new Item.Properties().stacksTo(1));id=p.get("id").getAsString();model=p.get("model").getAsString();damage=p.get("damage").getAsFloat();
            var soundId=new ResourceLocation("bo2beta","fire."+id);
            fireSound=Registry.register(BuiltInRegistries.SOUND_EVENT,soundId,SoundEvent.createVariableRangeEvent(soundId));
        }
    }
    private static final class Input {boolean held,pressed,reload,busy;int last;long sequence;float peek,aim;long lastStep=-1;Gun gun;}
    private final Map<UUID,Input> inputs=new HashMap<>();
    private Bridge bridge;
    private static Beta active;
    private static final Map<UUID,Integer> hudKills=new HashMap<>();
    static void resetWeapon(ServerPlayer player){
        if(active!=null)active.inputs.remove(player.getUUID());
        if(botBridge!=null){var request=new JsonObject();request.addProperty("op","player_reset");request.addProperty("player",player.getUUID().toString());botBridge.request(request);}
        var packet=new JsonObject();packet.addProperty("reset",true);send(player,packet);
    }
    public void onInitialize() {
        active=this;
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(BOT_ENTITY,Bo2Bot.attributes());
        try(var reader=new InputStreamReader(Objects.requireNonNull(Beta.class.getResourceAsStream("/data/bo2beta/weapons.json")),StandardCharsets.UTF_8)) {
            for(var entry:JsonParser.parseReader(reader).getAsJsonArray()){var gun=new Gun(entry.getAsJsonObject());GUNS.put(gun.id,gun);Registry.register(BuiltInRegistries.ITEM,new ResourceLocation("bo2beta",gun.id),gun);}
        }catch(IOException e){throw new IllegalStateException(e);}
        Registry.register(BuiltInRegistries.ITEM,new ResourceLocation("bo2beta","frag_grenade"),GRENADE);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,new ResourceLocation("bo2beta","arsenal"),FabricItemGroup.builder()
            .title(Component.translatable("itemGroup.bo2beta")).icon(()->new ItemStack(GRENADE))
            .displayItems((p,out)->{GUNS.values().forEach(out::accept);out.accept(GRENADE);}).build());
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount)->{boolean allowed=!source.is(GrenadeController.VISUAL_BLAST) && MatchDirector.damageAllowed(entity,source);if(allowed && amount>0)CombatRules.damaged(entity);return allowed;});
        ServerLifecycleEvents.SERVER_STARTED.register(server->{bridge=new Bridge();botBridge=bridge;inputs.clear();hudKills.clear();GrenadeController.start(bridge);MatchDirector.reset();CombatRules.reset();PlayerAnimations.reset();});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{MatchDirector.stop(server,"Server closed");CombatRules.stop();GrenadeController.stop();if(bridge!=null)bridge.close();bridge=null;botBridge=null;inputs.clear();});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{inputs.remove(handler.player.getUUID());GrenadeController.cancel(handler.player);MatchDirector.disconnect(handler.player);});
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{if(MatchDirector.nuketown(handler.player.serverLevel()))CombatRules.prepare(handler.player);MatchDirector.enterArena(handler.player);MatchDirector.joined(handler.player);PlayerCharacters.broadcast(server);MatchDirector.send(handler.player);});
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((old,player,alive)->{if(MatchDirector.nuketown(player.serverLevel()))CombatRules.prepare(player);if(!alive)MatchDirector.respawned(old,player);PlayerCharacters.broadcast(player.server);});
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity,source,damage)->{MatchDirector.beforeDeath(entity);return true;});
        ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{
            MatchDirector.death(entity,source);
            if(!(entity instanceof ServerPlayer player) || bridge==null)return;
            inputs.remove(player.getUUID());GrenadeController.cancel(player);
            var request=new JsonObject();request.addProperty("op","player_reset");request.addProperty("player",player.getUUID().toString());
            bridge.request(request);var message=new JsonObject();message.addProperty("reset",true);send(player,message);
        });
        ServerPlayNetworking.registerGlobalReceiver(INPUT,(server,player,handler,buffer,sender)->{
            String action=buffer.readUtf(32);String value=buffer.readUtf(96);boolean held=buffer.readBoolean(),pressed=buffer.readBoolean();
            server.execute(()->{
                if(action.startsWith("match_") || action.equals("character")){MatchDirector.request(player,action,value);return;}
                if(action.equals("give")) {
                    Item item=value.equals("frag_grenade")?GRENADE:GUNS.get(value);
                    // Debug grants require creative or operator permission on the server.
                    if(item!=null && (player.isCreative() || player.hasPermissions(2)))player.getInventory().add(new ItemStack(item,item==GRENADE?2:1));return;
                }
                if(action.equals("bot_spawn")){String[] parts=value.split(":");if(parts.length==2)BotControl.spawn(player,parts[1],parts[0].equals("ally"));else if(parts.length==4 || parts.length==5)try{BotControl.spawnDebug(player,parts[1],parts[0].equals("ally"),parts[2],Integer.parseInt(parts[3]),parts.length==5?BotDifficulty.valueOf(parts[4]):BotDifficulty.REGULAR);}catch(IllegalArgumentException ignored){}return;}
                if(action.equals("bot_remove")){BotControl.remove(player);return;}
                if(action.equals("grenade_hold")){GrenadeController.hold(player,held);return;}
                if(action.equals("aim")){var input=inputs.computeIfAbsent(player.getUUID(),id->new Input());try{float aim=Float.parseFloat(value);input.aim=Float.isFinite(aim)?Math.max(0,Math.min(1,aim)):0;}catch(NumberFormatException ignored){}return;}
                if(action.equals("peek")){
                    var input=inputs.computeIfAbsent(player.getUUID(),id->new Input());
                    try{float lean=Float.parseFloat(value);input.peek=Float.isFinite(lean)?Math.max(-1,Math.min(1,lean)):0;}catch(NumberFormatException e){input.peek=0;}return;
                }
                if(!action.equals("frame") && !action.equals("reload"))return;
                if(!(player.getMainHandItem().getItem() instanceof Gun gun) || !player.isAlive() || !MatchDirector.playing(player))return;
                var input=inputs.computeIfAbsent(player.getUUID(),id->new Input());
                if(input.gun!=gun){input.gun=gun;input.held=input.pressed=input.reload=false;input.lastStep=player.serverLevel().getGameTime();}
                input.held=held;input.pressed|=pressed;
                input.reload|=action.equals("reload");input.last=server.getTickCount();
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(bridge==null)return;
            GrenadeController.tick(server);MatchDirector.tick(server);CombatRules.tick(server);
            for(var player:server.getPlayerList().getPlayers()) {
                if(!player.isAlive() || !MatchDirector.playing(player))continue;
                if(GrenadeController.active(player) || !(player.getMainHandItem().getItem() instanceof Gun)){
                    var idle=inputs.get(player.getUUID());if(idle!=null){idle.held=idle.pressed=idle.reload=false;idle.lastStep=player.serverLevel().getGameTime();idle.gun=null;}continue;
                }
                var gun=(Gun)player.getMainHandItem().getItem();
                var input=inputs.computeIfAbsent(player.getUUID(),id->new Input());if(input.busy)continue;
                if(input.gun!=gun){input.gun=gun;input.held=input.pressed=input.reload=false;input.lastStep=player.serverLevel().getGameTime();}
                if(server.getTickCount()-input.last>5){input.held=false;input.pressed=false;input.peek=0;}
                var request=new JsonObject();request.addProperty("op",input.reload?"reload":"frame");request.addProperty("player",player.getUUID().toString());
                request.addProperty("weapon",gun.model);request.addProperty("held",input.held);request.addProperty("pressed",input.pressed);
                request.addProperty("infinite_ammo",player.isCreative());
                long now=player.serverLevel().getGameTime();request.addProperty("weapon_step_ms",input.lastStep<0?50:Math.min(250,Math.max(0,(now-input.lastStep)*50)));input.lastStep=now;
                request.addProperty("sequence",++input.sequence);long sequence=input.sequence;input.pressed=false;input.reload=false;input.busy=true;
                bridge.request(request).whenComplete((response,error)->server.execute(()->{
                    input.busy=false;if(player.hasDisconnected() || !player.isAlive() || !MatchDirector.playing(player) || inputs.get(player.getUUID())!=input || input.sequence!=sequence)return;
                    if(error!=null || !response.has("ok") || !response.get("ok").getAsBoolean()){
                        var message=new JsonObject();message.addProperty("error",error==null?"Unsupported bridge request":"Rust companion unavailable");send(player,message);return;
                    }
                    if(!response.has("protocol") || response.get("protocol").getAsInt()!=1
                        || !response.has("sequence") || response.get("sequence").getAsLong()!=sequence
                        || !response.has("weapon") || !response.get("weapon").getAsString().equals(gun.model))return;
                    // Accepted shots belong to this equipped item; late replies never fire the next gun.
                    for(int i=0;i<Math.min(32,response.get("shots").getAsInt()) && player.getMainHandItem().getItem()==gun && MatchDirector.playing(player);i++)shoot(player,gun);
                    if(player.getMainHandItem().getItem()==gun && input.gun==gun){PlayerAnimations.update(player,gun,response);send(player,response);}
                }));
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,access,env)->dispatcher.register(Commands.literal("bo2beta").requires(s->s.hasPermission(2))
            .then(Commands.literal("bot").then(Commands.literal("remove").executes(ctx->BotControl.remove(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("spawn").then(Commands.argument("gun",com.mojang.brigadier.arguments.StringArgumentType.word()).suggests((ctx,b)->{GUNS.keySet().forEach(b::suggest);return b.buildFuture();}).executes(ctx->BotControl.spawn(ctx.getSource().getPlayerOrException(),com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,"gun"),false)?1:0)
                    .then(Commands.literal("ally").executes(ctx->BotControl.spawn(ctx.getSource().getPlayerOrException(),com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,"gun"),true)?1:0)))))
            .then(Commands.literal("grenades").executes(ctx->{ctx.getSource().getPlayerOrException().getInventory().add(new ItemStack(GRENADE,2));return 1;}))));
    }
    static void send(ServerPlayer player,JsonObject message){var b=PacketByteBufs.create();b.writeUtf(message.toString(),8192);ServerPlayNetworking.send(player,STATE,b);}
    private void shoot(ServerPlayer player,Gun gun) {
        var level=player.serverLevel();Vec3 from=player.getEyePosition();
        var input=inputs.get(player.getUUID());from=from.add(CornerPeek.offset(player,from,input==null?0:input.peek));
        var profile=BulletCombat.profile(gun.id);float aim=input==null?0:input.aim;
        float hip=player.getDeltaMovement().horizontalDistanceSqr()>.0036?profile.hipMax():profile.hipMin();if(player.isCrouching())hip*=.7f;
        var result=BulletCombat.shoot(level,player,gun,from,player.getLookAngle(),hip+(profile.adsSpread()-hip)*aim);
        var block=result.impact();
        if(block!=null){var impact=new JsonObject();var point=block.getLocation();var pos=block.getBlockPos();
            impact.addProperty("x",point.x);impact.addProperty("y",point.y);impact.addProperty("z",point.z);
            impact.addProperty("bx",pos.getX());impact.addProperty("by",pos.getY());impact.addProperty("bz",pos.getZ());impact.addProperty("face",block.getDirection().get3DDataValue());
            var message=new JsonObject();message.add("impact",impact);for(var viewer:level.players())send(viewer,message);}
        for(var accepted:result.hits()){var closest=accepted.entity();boolean kill=accepted.killed();if(kill && !MatchDirector.owns(player))hudKills.merge(player.getUUID(),1,Integer::sum);
            String target=closest.getDisplayName().getString();if(target.length()>128)target=target.substring(0,128);
            for(var viewer:level.players())if(kill || viewer==player){var hud=new JsonObject();hud.addProperty("hit",viewer==player);hud.addProperty("kill",kill && !MatchDirector.owns(player));hud.addProperty("kills",hudKills.getOrDefault(viewer.getUUID(),0));hud.addProperty("killer",player.getName().getString());hud.addProperty("target",target);var message=new JsonObject();message.add("hud",hud);send(viewer,message);}}
        var flash=player.getEyePosition().add(player.getLookAngle().scale(.85)).add(0,-.12,0);
        for(var viewer:level.players())if(viewer!=player){
            level.sendParticles(viewer,net.minecraft.core.particles.ParticleTypes.FLAME,false,flash.x,flash.y,flash.z,3,.025,.025,.025,.01);
            level.sendParticles(viewer,net.minecraft.core.particles.ParticleTypes.SMOKE,false,flash.x,flash.y,flash.z,1,0,.01,0,0);
        }
        level.playSound(null,player.getX(),player.getEyeY(),player.getZ(),gun.fireSound,SoundSource.PLAYERS,1,1);
    }
}
