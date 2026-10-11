package local.bo2beta;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
public final class MatchClient {
    public static JsonObject state=new JsonObject();
    public static void accept(JsonObject value){state=value;if(value.has("difficulty"))BetaSettings.botDifficulty=BotDifficulty.valueOf(value.get("difficulty").getAsString());}
    public static String phase(){return state.has("phase")?state.get("phase").getAsString():"LOBBY";}
    public static boolean active(){return !phase().equals("LOBBY");}
    public static void reset(){state=new JsonObject();PlayerCharacterClient.reset();}
    public static void render(GuiGraphics g){
        if(!active())return;var c=Minecraft.getInstance();var f=c.font;int w=c.getWindow().getGuiScaledWidth();
        String text=state.get("label").getAsString();int seconds=state.get("seconds").getAsInt();
        if(phase().equals("COUNTDOWN"))text+="  Starts in "+seconds;
        else if(phase().equals("FINISHED"))text=state.get("result").getAsString()+" | P lobby";
        else {text+="  "+(seconds<0?"Unlimited":String.format("%d:%02d",seconds/60,seconds%60));
            if(state.get("mode").getAsString().equals("TDM"))text+="  "+state.get("team1").getAsInt()+" - "+state.get("team2").getAsInt()+" / "+state.get("limit").getAsInt();
            else if(c.player!=null)for(var row:state.getAsJsonArray("roster")){var r=row.getAsJsonObject();if(r.get("id").getAsString().equals(c.player.getUUID().toString()))text+="  "+(state.get("mode").getAsString().equals("GUN_GAME")?"Gun "+r.get("tier").getAsInt():"Score "+r.get("score").getAsInt())+" / "+state.get("limit").getAsInt();}}
        int size=f.width(text);g.fill((w-size)/2-5,3,(w+size)/2+5,17,0xB0000000);g.drawCenteredString(f,text,w/2,6,0xFFFFFF);
    }
}
