package local.bo2beta;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
public final class BetaScreen extends Screen {
    private int page;
    private boolean settings,bots,ally;
    private String botGun="ar_ak47";
    private int characterIndex,botPose;
    public BetaScreen(){super(Component.literal("BO2 BETA DEBUG"));}
    protected void init(){
        if(bots){botWidgets();return;}
        if(settings){settingsWidgets();return;}
        var guns=new ArrayList<>(Beta.GUNS.values());
        int count=Math.max(2,Math.min(12,((height-132)/22)*2));page=Math.min(page,(guns.size()-1)/count);
        int start=page*count,panel=Math.min(330,width-20),left=(width-panel)/2,column=(panel-6)/2;
        for(int i=0;i<count && start+i<guns.size();i++){
            var gun=guns.get(start+i);addRenderableWidget(Button.builder(Component.literal(gun.id.replace('_',' ').toUpperCase()),b->BetaClient.send("give",gun.id,false,false))
                .bounds(left+(i%2)*(column+6),52+(i/2)*22,column,20).build());
        }
        int footer=56+(count/2)*22,third=(panel-12)/3;
        addRenderableWidget(Button.builder(Component.literal("< Previous"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(left,footer,third,20).build());
        addRenderableWidget(Button.builder(Component.literal("Next >"),b->{page=Math.min((guns.size()-1)/count,page+1);rebuildWidgets();}).bounds(left+third+6,footer,third,20).build());
        addRenderableWidget(Button.builder(Component.literal("Grenades"),b->BetaClient.send("give","frag_grenade",false,false)).bounds(left+2*(third+6),footer,third,20).build());
        addRenderableWidget(Button.builder(Component.literal("Reload"),b->BetaClient.send("reload","",false,false)).bounds(left,footer+24,third,20).build());
        addRenderableWidget(Button.builder(Component.literal("BO2 view: "+(OriginalView.enabled?"ON":"OFF")),b->{OriginalView.enabled=!OriginalView.enabled;rebuildWidgets();}).bounds(left+third+6,footer+24,third,20).build());
        addRenderableWidget(Button.builder(Component.literal("Close [P]"),b->onClose()).bounds(left+2*(third+6),footer+24,third,20).build());
        addRenderableWidget(Button.builder(Component.literal("Effects / Audio / Bots"),b->{settings=true;rebuildWidgets();}).bounds(left,footer+48,panel,20).build());
    }
    private void settingsWidgets(){
        int panel=Math.min(330,width-20),left=(width-panel)/2,half=(panel-6)/2,index=0;
        for(var preset:DebugFilters.Preset.values()){
            String label=(DebugFilters.current()==preset?"> ":"")+preset.label;
            addRenderableWidget(Button.builder(Component.literal(label),b->{DebugFilters.select(preset);rebuildWidgets();}).bounds(left+(half+6)*(index%2),48+22*(index/2),half,20).build());index++;
        }
        addRenderableWidget(new net.minecraft.client.gui.components.AbstractSliderButton(left,140,panel,20,Component.empty(),BetaSettings.volume){
            {updateMessage();}
            protected void updateMessage(){setMessage(Component.literal("BO2 volume: "+Math.round(value*100)+"%"));}
            protected void applyValue(){BetaSettings.volume=(float)value;BetaSettings.save();
                var c=net.minecraft.client.Minecraft.getInstance();c.getSoundManager().updateSourceVolume(net.minecraft.sounds.SoundSource.PLAYERS,c.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.PLAYERS));}
        });
        addRenderableWidget(Button.builder(Component.literal("Corner peek: "+(BetaSettings.peeking?"ON":"OFF")+" [hold Z / C]"),b->{BetaSettings.peeking=!BetaSettings.peeking;BetaSettings.save();rebuildWidgets();}).bounds(left,164,panel,20).build());
        addRenderableWidget(Button.builder(Component.literal("Entity radar: "+(BetaSettings.entityRadar?"ON":"OFF")),b->{BetaSettings.entityRadar=!BetaSettings.entityRadar;BetaSettings.save();rebuildWidgets();}).bounds(left,188,half,20).build());
        addRenderableWidget(Button.builder(Component.literal("Bots / spawn"),b->{settings=false;bots=true;page=0;rebuildWidgets();}).bounds(left+half+6,188,half,20).build());
        addRenderableWidget(Button.builder(Component.literal("< Arsenal"),b->{settings=false;rebuildWidgets();}).bounds(left,212,half,20).build());
        addRenderableWidget(Button.builder(Component.literal("Close [P]"),b->onClose()).bounds(left+half+6,212,half,20).build());
    }
    private void botWidgets(){
        int panel=Math.min(330,width-20),left=(width-panel)/2,half=(panel-6)/2;
        var guns=new ArrayList<>(Beta.GUNS.keySet());int count=Math.max(2,Math.min(8,((height-200)/22)*2));page=Math.min(page,(guns.size()-1)/count);
        for(int i=0;i<count && page*count+i<guns.size();i++){String id=guns.get(page*count+i);addRenderableWidget(Button.builder(Component.literal((id.equals(botGun)?"> ":"")+id),b->{botGun=id;rebuildWidgets();}).bounds(left+(i%2)*(half+6),52+(i/2)*22,half,20).build());}
        int y=56+(count/2)*22;
        var characters=new ArrayList<>(BotCharacters.NAMES.keySet());String character=characters.get(characterIndex);
        addRenderableWidget(Button.builder(Component.literal("Character: "+BotCharacters.NAMES.get(character)+" >"),b->{characterIndex=(characterIndex+1)%characters.size();rebuildWidgets();}).bounds(left,y,panel,20).build());
        String[] poses={"Gameplay","Idle","Walk","Reload","Fire","Death"};
        addRenderableWidget(Button.builder(Component.literal("Debug pose: "+poses[botPose]+" >"),b->{botPose=(botPose+1)%poses.length;rebuildWidgets();}).bounds(left,y+24,half,20).build());
        addRenderableWidget(Button.builder(Component.literal("Difficulty: "+BetaSettings.botDifficulty.label+" >"),b->{BetaSettings.botDifficulty=BotDifficulty.values()[(BetaSettings.botDifficulty.ordinal()+1)%4];BetaSettings.save();rebuildWidgets();}).bounds(left+half+6,y+24,half,20).build());y+=48;
        addRenderableWidget(Button.builder(Component.literal("< Weapons"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(left,y,half,20).build());
        addRenderableWidget(Button.builder(Component.literal("Weapons >"),b->{page=Math.min((guns.size()-1)/count,page+1);rebuildWidgets();}).bounds(left+half+6,y,half,20).build());
        addRenderableWidget(Button.builder(Component.literal(ally?"Team: ally":"Team: enemy"),b->{ally=!ally;rebuildWidgets();}).bounds(left,y+24,half,20).build());
        addRenderableWidget(Button.builder(Component.literal("Spawn bot"),b->BetaClient.send("bot_spawn",(ally?"ally:":"enemy:")+botGun+":"+new ArrayList<>(BotCharacters.NAMES.keySet()).get(characterIndex)+":"+botPose+":"+BetaSettings.botDifficulty.name(),false,false)).bounds(left+half+6,y+24,half,20).build());
        addRenderableWidget(Button.builder(Component.literal("Remove nearby bots"),b->BetaClient.send("bot_remove","",false,false)).bounds(left,y+48,panel,20).build());
        addRenderableWidget(Button.builder(Component.literal("< Arsenal"),b->{bots=false;page=0;rebuildWidgets();}).bounds(left,y+72,half,20).build());
        addRenderableWidget(Button.builder(Component.literal("Close [P]"),b->onClose()).bounds(left+half+6,y+72,half,20).build());
    }
    public boolean isPauseScreen(){return false;}
    public boolean keyPressed(int key,int scan,int modifiers){if(key==GLFW.GLFW_KEY_P){onClose();return true;}return super.keyPressed(key,scan,modifiers);}
    public void render(GuiGraphics g,int x,int y,float delta){renderBackground(g);g.drawCenteredString(font,title,width/2,20,0xFFFFFF);g.drawCenteredString(font,"Creative / operator item grants | Page "+(page+1),width/2,36,0xAAAAAA);super.render(g,x,y,delta);}
}
