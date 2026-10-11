package local.bo2beta;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
public final class CornerPeek {
    public static float amount;
    private static float previous;
    private static boolean leftHeld,rightHeld;
    private static float lastDirection;
    /** The most recently pressed side wins when both keys are held. */
    public static float input(boolean left,boolean right){
        if(left&&!leftHeld)lastDirection=-1;
        if(right&&!rightHeld)lastDirection=1;
        leftHeld=left;rightHeld=right;
        return left&&right?lastDirection:right?1:left?-1:0;
    }
    public static void tick(float target){previous=amount;amount+=Math.max(-.25f,Math.min(.25f,target-amount));}
    public static void reset(){amount=previous=lastDirection=0;leftHeld=rightHeld=false;}
    public static float sample(float delta){return previous+(amount-previous)*delta;}
    public static float effective(Player p,Vec3 from,float lean){
        double yaw=Math.toRadians(p.getYRot());Vec3 right=new Vec3(-Math.cos(yaw),0,-Math.sin(yaw));
        return (float)(offset(p,from,lean).dot(right)/.4);
    }
    /** Shared collision test for camera and server shot origin; never offsets through walls. */
    public static Vec3 offset(Player p,Vec3 from,float lean){
        if(!p.isAlive() || p.isPassenger() || p.isFallFlying())return Vec3.ZERO;
        double yaw=Math.toRadians(p.getYRot());
        Vec3 wanted=new Vec3(Math.cos(yaw),0,Math.sin(yaw)).scale(-Math.max(-1,Math.min(1,lean))*.4);
        if(wanted.lengthSqr()<1e-8)return Vec3.ZERO;
        double allowed=1;
        // Give the camera clearance, rather than letting its center touch a wall.
        for(double y:new double[]{-.08,0,.08})for(double x:new double[]{-.08,0,.08}){
            Vec3 start=from.add(x,y,0);
            var hit=p.level().clip(new ClipContext(start,start.add(wanted),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            if(hit.getType()!=HitResult.Type.MISS)allowed=Math.min(allowed,Math.max(0,(start.distanceTo(hit.getLocation())-.04)/wanted.length()));
        }
        return wanted.scale(allowed).add(0,-.045*Math.abs(lean)*allowed,0);
    }
}
