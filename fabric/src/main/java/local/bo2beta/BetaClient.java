package local.bo2beta;
import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.*;
import net.minecraft.world.InteractionResult;
import org.lwjgl.glfw.GLFW;

public final class BetaClient implements ClientModInitializer {
    public static JsonObject state;
    public static KeyMapping peekLeft,peekRight;
    private boolean previous,bindingsReady;
    private Beta.Gun equipped;
    public static void send(String action,String value,boolean held,boolean pressed){
        if(!ClientPlayNetworking.canSend(Beta.INPUT))return;
        var b=PacketByteBufs.create();b.writeUtf(action,32);b.writeUtf(value,96);b.writeBoolean(held);b.writeBoolean(pressed);ClientPlayNetworking.send(Beta.INPUT,b);
    }
    public void onInitializeClient(){
        BetaSettings.load();BulletHoles.init();
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(Beta.BOT_ENTITY,BotRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(Beta.GRENADE_ENTITY,GrenadeRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(Beta.GRENADE,(stack,context,pose,buffers,light,overlay)->{
            pose.pushPose();pose.translate(.5,.5,.5);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(35));pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(15));
            float size=context==net.minecraft.world.item.ItemDisplayContext.GUI?.17f:context==net.minecraft.world.item.ItemDisplayContext.GROUND?.0254f:.06f;
            pose.scale(size,size,size);pose.translate(0,-.7102,0);OriginalGrenadeView.drawProjectile(pose,buffers,light);pose.popPose();OriginalGrenadeView.itemFrames++;
        });
        var menu=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.bo2beta.debug",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_P,"category.bo2beta"));
        var melee=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.bo2beta.melee",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_V,"category.bo2beta"));
        var aimMenu=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.bo2beta.aim",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_O,"category.bo2beta"));
        var reload=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.bo2beta.reload",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_R,"category.bo2beta"));
        peekLeft=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.bo2beta.peek_left",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_Z,"category.bo2beta"));
        peekRight=KeyBindingHelper.registerKeyBinding(new KeyMapping("key.bo2beta.peek_right",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_C,"category.bo2beta"));
        ClientPlayNetworking.registerGlobalReceiver(Beta.STATE,(client,handler,b,sender)->{String message=b.readUtf(8192);client.execute(()->{var response=JsonParser.parseString(message).getAsJsonObject();if(response.has("grenade_cancelled")){OriginalGrenadeView.reset();return;}if(response.has("playerAnimation")){PlayerCharacterClient.animation(response.getAsJsonObject("playerAnimation"));return;}if(response.has("match")){MatchClient.accept(response.getAsJsonObject("match"));return;}if(response.has("characters")){PlayerCharacterClient.accept(response.getAsJsonObject("characters"));return;}if(response.has("impact")){BulletHoles.accept(response.getAsJsonObject("impact"));return;}if(response.has("reset")){state=null;OriginalView.reset();MuzzleEffects.reset();OriginalGrenadeView.reset();CornerPeek.reset();return;}if(response.has("hud")){MultiplayerHud.accept(response.getAsJsonObject("hud"));return;}OriginalGrenadeView.accept(response);if(!response.has("grenade") && !response.has("grenade_error")){if(response.has("weapon") && (client.player==null || !(client.player.getMainHandItem().getItem() instanceof Beta.Gun current) || !response.get("weapon").getAsString().equals(current.model)))return;state=response;MuzzleEffects.accept(state);OriginalView.accept(state);}});});
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(!bindingsReady){
                // Options exist after client initialization. P has one owner.
                var social=client.options.keySocialInteractions;
                if(KeyBindingHelper.getBoundKeyOf(social).getValue()==GLFW.GLFW_KEY_P)
                    social.setKey(InputConstants.UNKNOWN);
                KeyMapping.resetMapping();bindingsReady=true;
            }
            Beta.Gun current=client.player!=null && client.player.getMainHandItem().getItem() instanceof Beta.Gun g?g:null;
            if(current!=equipped){equipped=current;previous=false;state=null;OriginalView.reset();MuzzleEffects.reset();}
            ViewMovement.tick();AnimationAudio.tick();TerrainMinimap.tick();BulletHoles.tick();
            if(client.player!=null && client.player.getMainHandItem().getItem() instanceof Beta.Gun gun)
                OriginalView.tickAudio(gun);
            BetaSmoke.tick(client);
            if(client.player==null){CornerPeek.reset();state=null;previous=false;OriginalView.reset();OriginalGrenadeView.reset();MultiplayerHud.reset();MatchClient.reset();return;}
            while(menu.consumeClick())client.setScreen(client.screen instanceof BetaScreen || client.screen instanceof LobbyScreen || client.screen instanceof AimScreen?null:new LobbyScreen());
            while(aimMenu.consumeClick())client.setScreen(client.screen instanceof AimScreen?null:new AimScreen());
            AimAssist.tick(client);
            if(OriginalGrenadeView.cooking())send("grenade_hold","",client.screen==null && client.options.keyUse.isDown(),false);
            boolean gun=client.player.getMainHandItem().getItem() instanceof Beta.Gun;
            CornerPeek.tick(gun && client.player.isAlive() && client.screen==null && BetaSettings.peeking && client.options.getCameraType().isFirstPerson()?
                CornerPeek.input(peekLeft.isDown(),peekRight.isDown()):CornerPeek.input(false,false));
            send("peek",Float.toString(CornerPeek.amount),false,false);
            send("aim",Float.toString(gun && client.screen==null && client.options.keyUse.isDown()?1:0),false,false);
            if(gun && OriginalView.enabled && client.options.keyUse.isDown())client.player.setSprinting(false);
            boolean held=gun && client.screen==null && (client.options.keyAttack.isDown() || AimAssist.firing);
            while(melee.consumeClick())if(client.screen==null)send("match_melee","",false,false);
            while(reload.consumeClick())if(gun)send("reload","",false,false);
            if(gun)send("frame","",held,held&&!previous);
            previous=held;
        });
        AttackBlockCallback.EVENT.register((player,level,hand,pos,face)->player.getMainHandItem().getItem() instanceof Beta.Gun?InteractionResult.FAIL:InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((player,level,hand,entity,hit)->player.getMainHandItem().getItem() instanceof Beta.Gun?InteractionResult.FAIL:InteractionResult.PASS);
        HudRenderCallback.EVENT.register((g,delta)->{
            var client=Minecraft.getInstance();if(client.player==null)return;
            CombatHud.render(g);if(Bo2Hud.active()){Bo2Hud.render(g);MatchClient.render(g);return;}MatchClient.render(g);
            if(!(client.player.getMainHandItem().getItem() instanceof Beta.Gun))return;
            String text="BO2 beta | P menu | R reload";
            if(state!=null){if(state.has("error"))text=state.get("error").getAsString();else if(state.has("loaded"))text=state.get("loaded").getAsInt()+" / "+state.get("reserve").getAsInt()+(state.get("reload").getAsFloat()>0?" | RELOADING":"")+" | P menu | R reload";}
            g.drawString(client.font,text,10,client.getWindow().getGuiScaledHeight()-32,0xFFFFFF,true);
        });
    }
}
