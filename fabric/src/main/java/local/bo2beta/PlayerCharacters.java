package local.bo2beta;

import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/** Server-owned cosmetic choices, independent of inventories and match lives. */
public final class PlayerCharacters extends SavedData {
    private final Map<UUID,String> choices=new HashMap<>();
    public static PlayerCharacters data(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(PlayerCharacters::load,PlayerCharacters::new,"bo2_character_choices");
    }
    private static PlayerCharacters load(CompoundTag tag){
        var result=new PlayerCharacters();
        for(String key:tag.getAllKeys())try{
            String id=tag.getString(key);
            if(BotCharacters.valid(id))result.choices.put(UUID.fromString(key),id);
        }catch(IllegalArgumentException ignored){}
        return result;
    }
    public CompoundTag save(CompoundTag tag){choices.forEach((id,skin)->tag.putString(id.toString(),skin));return tag;}
    public static String character(ServerPlayer p){return data(p.server).choices.getOrDefault(p.getUUID(),"default");}
    public static boolean select(ServerPlayer p,String skin){
        if(!skin.equals("default") && !BotCharacters.valid(skin))return false;
        var d=data(p.server);
        if(skin.equals("default"))d.choices.remove(p.getUUID());else d.choices.put(p.getUUID(),skin);
        d.setDirty();broadcast(p.server);return true;
    }
    public static void broadcast(MinecraftServer server){
        var skins=new JsonObject();
        for(var p:server.getPlayerList().getPlayers())skins.addProperty(p.getUUID().toString(),character(p));
        var packet=new JsonObject();packet.add("characters",skins);
        for(var p:server.getPlayerList().getPlayers())Beta.send(p,packet);
    }
}
