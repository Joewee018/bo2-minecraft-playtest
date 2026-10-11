package local.bo2beta;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
/** Reproduces the bus/street false teleport in a disposable world. */
public final class BoundarySmoke {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void verify(ServerPlayer p){
        var checkpoint=new Vec3(1630.5,20,-792.5);check(MatchDirector.safe(p.serverLevel(),p,checkpoint),"Boundary fixture checkpoint");
        p.connection.teleport(checkpoint.x,checkpoint.y,checkpoint.z,12,7);MatchDirector.tick(p.server);
        var street=new Vec3(1633.5,20,-784.5);check(!MatchDirector.insideArena(street) && MatchDirector.withinMovementBounds(street),"Bus street must be permitted for movement");
        p.connection.teleport(street.x,street.y,street.z,65,10);for(int i=0;i<80;i++)MatchDirector.tick(p.server);
        check(p.position().distanceToSqr(street)<.001 && p.getYRot()==65,"Normal street movement teleported player");
        var jump=street.add(0,1.2,0);p.connection.teleport(jump.x,jump.y,jump.z,65,10);for(int i=0;i<30;i++)MatchDirector.tick(p.server);
        check(p.position().distanceToSqr(jump)<.001,"Ordinary jump teleported player");
        var outside=new Vec3(1500,20,-700);p.connection.teleport(outside.x,outside.y,outside.z,65,10);for(int i=0;i<19;i++)MatchDirector.tick(p.server);
        check(p.position().distanceToSqr(outside)<.001,"Short boundary crossing teleported player");MatchDirector.tick(p.server);
        check(p.position().distanceToSqr(checkpoint)<.001 && p.getYRot()==65 && p.getXRot()==10,"Boundary recovery must preserve view and nearby checkpoint");
        p.connection.teleport(jump.x,jump.y,jump.z,65,10);for(int i=0;i<3;i++)MatchDirector.tick(p.server);
        check(p.position().distanceToSqr(jump)<.001,"Repeated correction after return");
        if(MatchDirector.session!=null){
            var entry=MatchDirector.entities.get(p.getUUID());var kit=p.getMainHandItem().getItem();var spawn=entry.lastSpawn;
            for(int i=0;i<80;i++)MatchDirector.tick(p.server);
            check(p.position().distanceToSqr(jump)<.001 && entry.deaths==0 && !entry.deathPending && entry.lastSpawn.equals(spawn) && p.getMainHandItem().getItem()==kit,"Living match player respawned or lost loadout");
            entry.respawnAt=0;MatchDirector.tick(p.server);check(p.position().distanceToSqr(jump)<.001,"Stale timer respawned living player");entry.respawnAt=-1;
        }
        System.out.println("MATCH BOUNDARY PASS: bus/street; jumps; brief edge crossing; genuine exit recovery; last safe ground; preserved view; no repeated teleport");
    }
}
