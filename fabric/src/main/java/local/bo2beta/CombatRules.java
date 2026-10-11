package local.bo2beta;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
/** Shared health rules for the Nuketown sandbox. 200 HP is a custom match rule. */
public final class CombatRules {
    public static final float HEALTH=200;
    private static final Map<UUID,LivingEntity> actors=new HashMap<>();
    private static final Map<UUID,Long> damaged=new HashMap<>();
    private static final Map<net.minecraft.server.level.ServerLevel,Boolean> regen=new HashMap<>();
    public static void reset(){actors.clear();damaged.clear();}
    public static void stop(){regen.forEach((level,value)->level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION).set(value,level.getServer()));regen.clear();reset();}
    public static void prepare(LivingEntity e){
        if(e.level().isClientSide)return;var a=e.getAttribute(Attributes.MAX_HEALTH);if(a==null)return;
        float previous=e.getMaxHealth();a.setBaseValue(HEALTH);
        if(previous<HEALTH)e.setHealth(HEALTH);actors.put(e.getUUID(),e);
    }
    public static void damaged(LivingEntity e){if(actors.containsKey(e.getUUID()))damaged.put(e.getUUID(),e.level().getGameTime());}
    public static void tick(MinecraftServer server){
        for(var p:server.getPlayerList().getPlayers())if(MatchDirector.nuketown(p.serverLevel())){prepare(p);var level=p.serverLevel();if(!regen.containsKey(level)){regen.put(level,level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION));level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION).set(false,server);}}
        for(var e:new ArrayList<>(actors.values())){
            if(e.isRemoved()){actors.remove(e.getUUID());damaged.remove(e.getUUID());continue;}
            if(!e.isAlive() || !MatchDirector.playing(e))continue;
            long at=damaged.getOrDefault(e.getUUID(),e.level().getGameTime());
            if(e.level().getGameTime()-at>=100 && e.getHealth()<e.getMaxHealth())e.heal(e.getHealth()/e.getMaxHealth()<=.55f?e.getMaxHealth()*.1f:e.getMaxHealth());
        }
    }
}
