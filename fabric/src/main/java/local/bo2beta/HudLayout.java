package local.bo2beta;

/** Native 720-line Compass anchors inside the current safe area. */
public final class HudLayout {
    public static final int SAFE_LEFT=34,SAFE_TOP=20;
    public static final int MAP_X=SAFE_LEFT+13,MAP_Y=SAFE_TOP+7,MAP_SIZE=150;
    public static final int SAFE_BOTTOM=720-SAFE_TOP;
    public static final int COMPASS_Y=MAP_Y+148,COMPASS_HEIGHT=26;
    public static final int AMMO_TOP=SAFE_BOTTOM-87;
    public static final int AMMO_SMALL_TOP=AMMO_TOP+12;
    public static int ammoRight(int width){return width-SAFE_LEFT-28-20;}
    public static double mapX(double x){return MAP_X+MAP_SIZE/2d+(x-TerrainMinimap.centerX)*MAP_SIZE/128d;}
    public static double mapY(double z){return MAP_Y+MAP_SIZE/2d+(z-TerrainMinimap.centerZ)*MAP_SIZE/128d;}
}
