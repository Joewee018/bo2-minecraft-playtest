package local.bo2beta;
import net.minecraft.world.phys.Vec3;
/** Server-safe aiming math; avoids loading any client classes on a dedicated server. */
public final class AimAngles {
    public static float[] of(Vec3 from,Vec3 to){var d=to.subtract(from);return new float[]{(float)(Math.toDegrees(Math.atan2(d.z,d.x))-90),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)))};}
}
