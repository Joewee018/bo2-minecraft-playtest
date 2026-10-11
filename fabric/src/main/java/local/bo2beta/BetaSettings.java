package local.bo2beta;
import com.google.gson.*;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;
public final class BetaSettings {
    public static volatile float volume=1,lastMixedVolume;
    public static volatile int soundMixed;
    public static volatile int newSoundsMixed;
    public static BotDifficulty botDifficulty=BotDifficulty.REGULAR;
    public static boolean peeking=true;
    public static boolean entityRadar=true;
    private static Path path(){return FabricLoader.getInstance().getConfigDir().resolve("bo2beta-client.json");}
    public static void load(){try{
        if(!Files.exists(path()))return;
        var j=JsonParser.parseString(Files.readString(path())).getAsJsonObject();
        float v=j.get("volume").getAsFloat();volume=Float.isFinite(v)?Math.max(0,Math.min(1,v)):1;
        peeking=j.get("peeking").getAsBoolean();
        if(j.has("entityRadar"))entityRadar=j.get("entityRadar").getAsBoolean();
        if(j.has("botDifficulty"))botDifficulty=BotDifficulty.valueOf(j.get("botDifficulty").getAsString());
        if(j.has("aim")){var a=j.getAsJsonObject("aim");AimAssist.enabled=a.get("enabled").getAsBoolean();AimAssist.autoFire=a.get("autoFire").getAsBoolean();AimAssist.head=a.get("head").getAsBoolean();AimAssist.fov=Math.max(10,Math.min(180,a.get("fov").getAsInt()));AimAssist.range=Math.max(8,Math.min(128,a.get("range").getAsInt()));float s=a.get("smoothing").getAsFloat();AimAssist.smoothing=Float.isFinite(s)?Math.max(.05f,Math.min(1,s)):.35f;}
    }catch(Exception e){System.err.println("BO2 client settings unreadable; using defaults");}}
    public static void save(){try{
        var j=new JsonObject();j.addProperty("volume",volume);j.addProperty("peeking",peeking);
        j.addProperty("entityRadar",entityRadar);j.addProperty("botDifficulty",botDifficulty.name());
        var a=new JsonObject();a.addProperty("enabled",AimAssist.enabled);a.addProperty("autoFire",AimAssist.autoFire);a.addProperty("head",AimAssist.head);a.addProperty("fov",AimAssist.fov);a.addProperty("range",AimAssist.range);a.addProperty("smoothing",AimAssist.smoothing);j.add("aim",a);
        Files.createDirectories(path().getParent());Files.writeString(path(),j.toString());
    }catch(Exception e){System.err.println("BO2 client settings could not be saved");}}
}
