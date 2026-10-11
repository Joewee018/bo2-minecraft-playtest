package local.bo2beta;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** Exact source sound notetracks; rumble and unresolved aliases are never substituted. */
public final class AnimationAudio {
    private static JsonObject clips;
    private static String previousKey="";
    private static float previousTime=-1;
    public static int played;
    public static int started;
    private record Pending(net.minecraft.client.resources.sounds.SoundInstance sound,long tick){}
    private static final java.util.ArrayDeque<Pending> pending=new java.util.ArrayDeque<>();
    public static void tick(){
        var c=Minecraft.getInstance();if(c.level==null){pending.clear();return;}
        long now=c.level.getGameTime();
        for(var it=pending.iterator();it.hasNext();){var p=it.next();
            if(c.getSoundManager().isActive(p.sound)){started++;it.remove();}
            else if(now-p.tick>40)it.remove();
        }
    }
    private static JsonObject clips(){
        if(clips==null)try(var input=Minecraft.getInstance().getResourceManager().getResourceOrThrow(new ResourceLocation("bo2beta","viewmodels/notetracks.json")).open()){
            clips=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(IOException e){throw new IllegalStateException("Original sound notetracks unavailable",e);}
        return clips;
    }
    public static void reset(){previousKey="";previousTime=-1;}
    public static void sample(String key,String clip,float time,float duration,boolean loop){
        var client=Minecraft.getInstance();if(client.isPaused())return;
        float current=duration>0?time/duration:0;if(loop)current%=1;
        boolean changed=!key.equals(previousKey);
        float from=changed?(loop?current:-1):previousTime;
        var source=clips().getAsJsonObject(clip);
        if(source!=null)for(var value:source.getAsJsonArray("events")){
            var event=value.getAsJsonObject();if(!event.get("available").getAsBoolean())continue;
            float at=event.get("time").getAsFloat();
            boolean crossed=current>=from?at>from && at<=current:loop && (at>from || at<=current);
            if(crossed){
                var sound=SoundEvent.createVariableRangeEvent(new ResourceLocation("bo2beta",event.get("event").getAsString()));
                var instance=new SimpleSoundInstance(sound,net.minecraft.sounds.SoundSource.PLAYERS,1,1,
                    net.minecraft.util.RandomSource.create(),client.player.getX(),client.player.getEyeY(),client.player.getZ());
                client.getSoundManager().play(instance);
                if(pending.size()>=32)pending.removeFirst();
                pending.addLast(new Pending(instance,client.level.getGameTime()));played++;
            }
        }
        previousKey=key;previousTime=current;
    }
}
