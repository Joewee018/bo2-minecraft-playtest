package local.bo2beta;
import java.util.*;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
public final class PlayerAnimations {
    private static final Map<UUID,String> previous=new HashMap<>();
    public static void update(ServerPlayer p,Beta.Gun gun,JsonObject response){
        boolean reload=response.has("reload") && response.get("reload").getAsFloat()>0;boolean fire=response.has("shots") && response.get("shots").getAsInt()>0;
        String key=gun.id+":"+reload;if(!fire && key.equals(previous.put(p.getUUID(),key)))return;
        var data=new JsonObject();data.addProperty("id",p.getUUID().toString());data.addProperty("weapon",gun.id);data.addProperty("reload",reload);data.addProperty("fire",fire);data.addProperty("tick",p.serverLevel().getGameTime());var packet=new JsonObject();packet.add("playerAnimation",data);
        for(var viewer:p.serverLevel().players())Beta.send(viewer,packet);
    }
    public static void reset(){previous.clear();}
}
