package local.bo2beta;

/** Tick-driven additive bridge motion. This does not replace original weapon clips. */
public final class JumpMotion {
    private static boolean initialized,grounded;
    private static double lastY,fallSpeed;
    private static float dip,previousDip,pitch,previousPitch;
    public static int jumps,landings;
    public static void reset(){initialized=false;dip=previousDip=pitch=previousPitch=0;fallSpeed=0;}
    public static void tick(boolean ground,boolean disabled,double y,double velocity){
        if(disabled){reset();return;}
        if(!initialized){grounded=ground;lastY=y;initialized=true;}
        previousDip=dip;previousPitch=pitch;
        double step=y-lastY;lastY=y;
        // Teleports cannot create a landing kick.
        if(Math.abs(step)>1.5){reset();return;}
        if(grounded&&!ground&&velocity>.08){dip=.85f;pitch=3;fallSpeed=0;jumps++;}
        if(!ground)fallSpeed=Math.max(fallSpeed,Math.max(0,-velocity));
        if(!grounded&&ground){float impact=(float)Math.min(1,fallSpeed/.65);dip=-1.15f*impact;pitch=-4*impact;fallSpeed=0;landings++;}
        grounded=ground;
        dip+=(0-dip)*.28f;pitch+=(0-pitch)*.28f;
    }
    public static float dip(float delta){return previousDip+(dip-previousDip)*delta;}
    public static float pitch(float delta){return previousPitch+(pitch-previousPitch)*delta;}
}
