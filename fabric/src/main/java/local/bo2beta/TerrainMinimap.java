package local.bo2beta;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;

/** Client-loaded Minecraft terrain, never invented enemy or objective markers. */
public final class TerrainMinimap {
    private static ResourceLocation texture;
    private static DynamicTexture image;
    private static long lastTick=-100;
    public static int updates;
    public static int centerX,centerZ;
    public static ResourceLocation texture(){return texture;}
    public static void tick(){
        var c=Minecraft.getInstance();
        if(c.player==null || c.level==null){
            if(texture!=null)c.getTextureManager().release(texture);
            texture=null;image=null;lastTick=-100;return;
        }
        if(!Bo2Hud.active() || c.isPaused() || c.level.getGameTime()-lastTick<20)return;
        lastTick=c.level.getGameTime();
        if(image==null){image=new DynamicTexture(new NativeImage(64,64,false));texture=c.getTextureManager().register("bo2beta_minimap",image);}
        var pixels=image.getPixels();var pos=new BlockPos.MutableBlockPos();int cx=c.player.getBlockX(),cz=c.player.getBlockZ();centerX=cx;centerZ=cz;
        for(int y=0;y<64;y++)for(int x=0;x<64;x++){
            int wx=cx+(x-32)*2,wz=cz+(y-32)*2,rgb=0x182326;
            pos.set(wx,0,wz);
            if(c.level.hasChunkAt(pos)){
                int top=c.level.getHeight(Heightmap.Types.WORLD_SURFACE,wx,wz)-1;pos.setY(top);
                rgb=c.level.getBlockState(pos).getMapColor(c.level,pos).col;
            }
            pixels.setPixelRGBA(x,y,0xff000000|((rgb&255)<<16)|(rgb&0xff00)|((rgb>>16)&255));
        }
        image.upload();updates++;
    }
}
