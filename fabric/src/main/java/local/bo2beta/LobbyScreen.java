package local.bo2beta;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
public final class LobbyScreen extends Screen {
    private static int bots=6,mode,limit=75,minutes=10,character;
    private boolean scoreboard;private int rowOffset;
    private final List<String> skins=new ArrayList<>();
    public LobbyScreen(){super(Component.literal("NUKETOWN MULTIPLAYER"));skins.add("default");skins.addAll(BotCharacters.NAMES.keySet());if(net.minecraft.client.Minecraft.getInstance().player!=null){int index=skins.indexOf(PlayerCharacterClient.skin(net.minecraft.client.Minecraft.getInstance().player.getUUID()));if(index>=0)character=index;}BetaClient.send("match_state","",false,false);}
    private void button(String text,int x,int y,int w,Button.OnPress click){addRenderableWidget(Button.builder(Component.literal(text),click).bounds(x,y,w,18).build());}
    protected void init(){
        int w=Math.min(width-16,340),x=(width-w)/2,h=(w-6)/2,y=35;
        if(scoreboard){button("< Lobby",x,height-24,w,b->{scoreboard=false;rebuildWidgets();});return;}
        button("Mode: "+MatchDirector.Mode.values()[mode].label+" >",x,y,w,b->{mode=(mode+1)%3;rebuildWidgets();});y+=21;
        button("- Bots: "+bots,x,y,h,b->{bots=Math.max(0,bots-1);rebuildWidgets();});button("Bots +",x+h+6,y,h,b->{bots=Math.min(17,bots+1);rebuildWidgets();});y+=21;
        button("Bot difficulty: "+BetaSettings.botDifficulty.label+" >",x,y,w,b->{BetaSettings.botDifficulty=BotDifficulty.values()[(BetaSettings.botDifficulty.ordinal()+1)%4];BetaSettings.save();BetaClient.send("match_difficulty",BetaSettings.botDifficulty.name(),false,false);rebuildWidgets();});y+=21;
        button("Score: "+(mode==2?20:limit)+" >",x,y,h,b->{limit=limit==25?50:limit==50?75:limit==75?100:25;rebuildWidgets();});button("Time: "+(minutes==0?"Unlimited":minutes+" min")+" >",x+h+6,y,h,b->{minutes=minutes==5?10:minutes==10?15:minutes==15?0:5;rebuildWidgets();});y+=21;
        button("Start match",x,y,h,b->{BetaClient.send("match_start",MatchDirector.Mode.values()[mode].name()+":"+bots+":"+limit+":"+minutes+":"+BetaSettings.botDifficulty.name(),false,false);onClose();});button("End / return to lobby",x+h+6,y,h,b->BetaClient.send("match_stop","",false,false));y+=21;
        button("Character: "+(skins.get(character).equals("default")?"Default":BotCharacters.NAMES.get(skins.get(character)))+" >",x,y,w,b->{character=(character+1)%skins.size();BetaClient.send("character",skins.get(character),false,false);rebuildWidgets();});y+=21;
        button("Restore default",x,y,h,b->{character=0;BetaClient.send("character","default",false,false);rebuildWidgets();});button("Scoreboard",x+h+6,y,h,b->{scoreboard=true;rebuildWidgets();});y+=21;
        button("Arsenal / settings",x,y,h,b->minecraft.setScreen(new BetaScreen()));button("Aim menu [O]",x+h+6,y,h,b->minecraft.setScreen(new AimScreen()));
    }
    public boolean mouseScrolled(double x,double y,double amount){if(scoreboard){int total=MatchClient.state.has("roster")?MatchClient.state.getAsJsonArray("roster").size():0;int rows=Math.max(1,(height-94)/11);rowOffset=Math.max(0,Math.min(Math.max(0,total-rows),rowOffset-(int)Math.signum(amount)));return true;}return super.mouseScrolled(x,y,amount);}
    public boolean isPauseScreen(){return false;}
    public boolean keyPressed(int key,int scan,int mods){if(key==GLFW.GLFW_KEY_P){onClose();return true;}return super.keyPressed(key,scan,mods);}
    public void render(GuiGraphics g,int mx,int my,float dt){renderBackground(g);g.drawCenteredString(font,title,width/2,8,0xFFFFFF);g.drawCenteredString(font,MatchClient.phase()+" | Random spawns and loadouts",width/2,22,0xAAAAAA);
        if(scoreboard && MatchClient.state.has("roster")){int y=42;g.drawString(font,"Player                  Team   K/D   Score",Math.max(6,width/2-170),y,0xE5B76C);y+=14;
            int index=0;for(var row:MatchClient.state.getAsJsonArray("roster")){if(index++<rowOffset)continue;var r=row.getAsJsonObject();if(y>height-38)break;String name=r.get("name").getAsString();String line=String.format("%-22s %3d    %d/%d   %d",name,r.get("team").getAsInt()+1,r.get("kills").getAsInt(),r.get("deaths").getAsInt(),r.get("score").getAsInt());g.drawString(font,line,Math.max(6,width/2-170),y,0xFFFFFF);y+=11;}g.drawCenteredString(font,"Scroll for more players",width/2,height-36,0xAAAAAA);}
        super.render(g,mx,my,dt);
    }
}
