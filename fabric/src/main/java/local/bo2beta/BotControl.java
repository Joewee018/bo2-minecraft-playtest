package local.bo2beta;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class BotControl {
    public static final int LIMIT=8;
    public static boolean spawn(ServerPlayer p,String gun,boolean ally){return spawnEntity(p,gun,ally)!=null;}
    public static Bo2Bot spawnEntity(ServerPlayer p,String gun,boolean ally){
        if(!(p.isCreative() || p.hasPermissions(2)) || !Beta.GUNS.containsKey(gun))return null;
        var level=p.serverLevel();if(level.getEntitiesOfClass(Bo2Bot.class,p.getBoundingBox().inflate(128)).size()>=LIMIT){p.displayClientMessage(Component.literal("BO2 bot limit: 8 within 128 blocks"),false);return null;}
        Vec3 facing=p.getLookAngle();facing=new Vec3(facing.x,0,facing.z).normalize();if(facing.lengthSqr()<.1)facing=new Vec3(0,0,1);
        Vec3 desired=p.position().add(facing.scale(4));
        for(int y=3;y>=-4;y--){BlockPos floor=BlockPos.containing(desired.x,p.getY()+y-1,desired.z);
            if(!level.getBlockState(floor).isSolidRender(level,floor))continue;
            var bot=Beta.BOT_ENTITY.create(level);if(bot==null)return null;bot.moveTo(desired.x,floor.getY()+1,desired.z,p.getYRot()+180,0);
            if(!level.noCollision(bot) || level.containsAnyLiquid(bot.getBoundingBox())){bot.discard();continue;}
            CombatRules.prepare(bot);bot.setHealth(bot.getMaxHealth());bot.configure(gun,ally,p.getUUID());if(!level.addFreshEntity(bot))return null;
            p.displayClientMessage(Component.literal("Spawned BO2 "+(ally?"ally":"enemy")+" with "+gun),false);return bot;
        }
        p.displayClientMessage(Component.literal("No clear ground four blocks ahead. Look at an open floor."),false);return null;
    }
    public static boolean spawnDebug(ServerPlayer p,String gun,boolean ally,String character,int pose){
        return spawnDebug(p,gun,ally,character,pose,BotDifficulty.REGULAR);
    }
    public static boolean spawnDebug(ServerPlayer p,String gun,boolean ally,String character,int pose,BotDifficulty difficulty){
        if(!BotCharacters.valid(character) || pose<0 || pose>5)return false;
        var bot=spawnEntity(p,gun,ally);if(bot==null)return false;bot.difficulty(difficulty);bot.character(character,pose);return true;
    }
    public static int remove(ServerPlayer p){if(!(p.isCreative() || p.hasPermissions(2)))return 0;var bots=p.serverLevel().getEntitiesOfClass(Bo2Bot.class,p.getBoundingBox().inflate(128));for(var b:bots)b.discard();return bots.size();}
}
