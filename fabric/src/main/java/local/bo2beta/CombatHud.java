package local.bo2beta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
public final class CombatHud {
    public static void render(GuiGraphics g){var c=Minecraft.getInstance();if(c.player==null || c.player.getMaxHealth()<CombatRules.HEALTH)return;
        g.drawString(c.font,Math.max(0,(int)Math.ceil(c.player.getHealth()))+" / "+(int)c.player.getMaxHealth()+" HP",10,c.getWindow().getGuiScaledHeight()-14,c.player.getHealth()<110?0xFF7777:0xFFFFFF,true);}
}
