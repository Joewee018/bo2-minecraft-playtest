package local.bo2beta;
/** Reaction/FOV from local _bot.gsc; cadence and accuracy are sandbox tuning. */
public enum BotDifficulty {
    RECRUIT("Recruit",1200,1600,50,12,5,5,20,.1f),
    REGULAR("Regular",600,800,70,9,8,3,40,.4f),
    HARDENED("Hardened",400,700,100,6,12,1.5f,60,.7f),
    VETERAN("Veteran",40,70,160,4,18,.6f,80,.9f);
    public final String label;public final int reactionMin,reactionMax,fov,shotTicks,turnDegrees,memoryTicks;public final float aimError,strafeChance;
    BotDifficulty(String label,int min,int max,int fov,int ticks,int turn,float error,int memory,float strafe){this.label=label;reactionMin=min;reactionMax=max;this.fov=fov;shotTicks=ticks;turnDegrees=turn;aimError=error;memoryTicks=memory;strafeChance=strafe;}
}
