package local.bo2beta;
import java.util.*;
import com.google.gson.JsonObject;
public final class PlayerCharacterClient {
    private static final Map<String,OriginalMesh> hands=new HashMap<>();
    public static OriginalMesh hands(OriginalMesh original){var p=net.minecraft.client.Minecraft.getInstance().player;if(p==null)return original;String id=skin(p.getUUID());if(id.equals("default") || id.equals("cdc"))return original;return hands.computeIfAbsent(id,key->new OriginalMesh(new net.minecraft.resources.ResourceLocation("bo2beta","viewmodels/custom/"+key+"-hands.glb")));}
    public static int handFrames;
    public static class Animation {public boolean reloading;public long reloadTick,fireTick;public String weapon;}
    private static final Map<UUID,Animation> animations=new HashMap<>();
    public static Animation animation(UUID id){return animations.get(id);}
    public static void animation(JsonObject value){UUID id=UUID.fromString(value.get("id").getAsString());var a=animations.computeIfAbsent(id,k->new Animation());boolean reload=value.get("reload").getAsBoolean();long tick=value.get("tick").getAsLong();if(reload && !a.reloading)a.reloadTick=tick;a.reloading=reload;a.weapon=value.get("weapon").getAsString();if(value.get("fire").getAsBoolean())a.fireTick=tick;}
    private static final Map<UUID,String> choices=new HashMap<>();
    public static void accept(JsonObject data){choices.clear();for(var e:data.entrySet())try{choices.put(UUID.fromString(e.getKey()),e.getValue().getAsString());}catch(IllegalArgumentException ignored){}}
    public static String skin(UUID id){return choices.getOrDefault(id,"default");}
    public static void reset(){choices.clear();animations.clear();}
}
