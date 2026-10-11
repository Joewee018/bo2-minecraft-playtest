package local.bo2beta;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
public final class AimScreen extends Screen {
    public AimScreen(){super(Component.literal("BOT AIM MENU"));}
    private void button(String label,int y,Runnable action){addRenderableWidget(Button.builder(Component.literal(label),b->{action.run();BetaSettings.save();rebuildWidgets();}).bounds((width-260)/2,y,260,20).build());}
    protected void init(){
        button("Aim: "+(AimAssist.enabled?"ON":"OFF"),36,()->AimAssist.enabled=!AimAssist.enabled);
        button("Auto-fire: "+(AimAssist.autoFire?"ON":"OFF"),60,()->AimAssist.autoFire=!AimAssist.autoFire);
        button("Target: "+(AimAssist.head?"Head":"Body"),84,()->AimAssist.head=!AimAssist.head);
        button("FOV: "+AimAssist.fov+" degrees >",108,()->AimAssist.fov=AimAssist.fov==30?60:AimAssist.fov==60?90:AimAssist.fov==90?180:30);
        button("Range: "+AimAssist.range+" blocks >",132,()->AimAssist.range=AimAssist.range==16?32:AimAssist.range==32?64:AimAssist.range==64?128:16);
        button("Aim speed: "+Math.round(AimAssist.smoothing*100)+"% >",156,()->AimAssist.smoothing=AimAssist.smoothing<.2f?.35f:AimAssist.smoothing<.5f?.7f:AimAssist.smoothing<.9f?1:.15f);
        button("< Lobby",180,()->minecraft.setScreen(new LobbyScreen()));
    }
    public boolean isPauseScreen(){return false;}
    public boolean keyPressed(int key,int scan,int mods){if(key==GLFW.GLFW_KEY_O || key==GLFW.GLFW_KEY_P){onClose();return true;}return super.keyPressed(key,scan,mods);}
    public void render(GuiGraphics g,int mx,int my,float dt){renderBackground(g);g.drawCenteredString(font,title,width/2,10,0xFFFFFF);g.drawCenteredString(font,"Opposing bots only | Visible targets",width/2,23,0xAAAAAA);super.render(g,mx,my,dt);}
}
