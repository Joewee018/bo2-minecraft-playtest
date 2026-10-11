package local.bo2beta;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import local.bo2beta.mixin.GameRendererAccess;
public final class DebugFilters {
    public enum Preset {
        OFF("OFF",null),TOON("Toon","toon"),KUWAHARA("Kuwahara","kuwahara"),PIXEL_ART("Pixel Art","pixel_art"),
        GRAYSCALE("Grayscale","grayscale"),SEPIA("Sepia","sepia"),INVERTED("Inverted","inverted"),CRT("CRT","crt");
        public final String label;final ResourceLocation resource;
        Preset(String label,String file){this.label=label;resource=file==null?null:new ResourceLocation("bo2beta","shaders/post/"+file+".json");}
    }
    public static int filteredHudFrames;
    private static ResourceLocation previous;
    public static Preset current(){
        var effect=Minecraft.getInstance().gameRenderer.currentEffect();if(effect==null)return Preset.OFF;
        for(var preset:Preset.values())if(preset.resource!=null && effect.getName().equals(preset.resource.toString()))return preset;
        return Preset.OFF;
    }
    public static void select(Preset preset){
        var renderer=Minecraft.getInstance().gameRenderer;var active=current();
        if(preset==active)return;
        if(preset==Preset.OFF){
            if(active!=Preset.OFF){renderer.shutdownEffect();if(previous!=null)((GameRendererAccess)renderer).bo2beta$loadEffect(previous);}
            previous=null;return;
        }
        if(active==Preset.OFF){var effect=renderer.currentEffect();previous=effect==null?null:new ResourceLocation(effect.getName());}
        ((GameRendererAccess)renderer).bo2beta$loadEffect(preset.resource);
        if(current()!=preset){
            if(previous!=null)((GameRendererAccess)renderer).bo2beta$loadEffect(previous);
            previous=null;throw new IllegalStateException("Debug filter failed to load: "+preset.label);
        }
    }
}
