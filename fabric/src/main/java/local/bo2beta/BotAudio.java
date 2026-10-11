package local.bo2beta;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;

/** Available original mechanical notetracks played at the bot's world position. */
public final class BotAudio {
    private static JsonObject guns,notes;
    private record Event(long tick,SoundEvent sound){}
    private final ArrayDeque<Event> events=new ArrayDeque<>();
    private static JsonObject read(String resource){try(var in=Objects.requireNonNull(BotAudio.class.getResourceAsStream(resource))){return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}}
    public void begin(Bo2Bot bot){
        if(guns==null){guns=read("/assets/bo2beta/viewmodels/catalog.json").getAsJsonObject("guns");notes=read("/assets/bo2beta/viewmodels/notetracks.json");}
        events.clear();var gun=guns.getAsJsonObject(bot.weapon());String clip=null;
        for(var value:gun.getAsJsonArray("clips"))if(value.getAsString().endsWith("_reload_empty")){clip=value.getAsString();break;}
        if(clip==null)return;var track=notes.getAsJsonObject(clip);if(track==null)return;
        float duration=gun.get("empty").getAsFloat();long now=bot.level().getGameTime();
        for(var value:track.getAsJsonArray("events")){var e=value.getAsJsonObject();if(e.get("available").getAsBoolean())events.add(new Event(now+Math.round(e.get("time").getAsFloat()*duration*20),SoundEvent.createVariableRangeEvent(new ResourceLocation("bo2beta",e.get("event").getAsString()))));}
    }
    public void tick(Bo2Bot bot){if(!(bot.level() instanceof ServerLevel level))return;while(!events.isEmpty() && events.peek().tick<=level.getGameTime()){var event=events.remove();level.playSound(null,bot.getX(),bot.getEyeY(),bot.getZ(),event.sound,SoundSource.PLAYERS,.7f,1);}}
}
