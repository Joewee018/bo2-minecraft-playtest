package local.bo2beta;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
/** Native imported surfaces are opaque, including auxiliary diffuse-alpha data. */
public final class OriginalRenderTypes extends RenderType {
    private OriginalRenderTypes(String name,VertexFormat format,VertexFormat.Mode mode,int size,boolean affects,boolean sort,Runnable setup,Runnable clear){super(name,format,mode,size,affects,sort,setup,clear);}
    private static final Map<ResourceLocation,RenderType> cache=new HashMap<>();
    private static final Map<ResourceLocation,RenderType> flashes=new HashMap<>();
    public static RenderType flash(ResourceLocation texture){
        return flashes.computeIfAbsent(texture,id->create("bo2_minecraft_muzzle_particle",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,false,
            CompositeState.builder().setShaderState(RENDERTYPE_EYES_SHADER).setTextureState(new TextureStateShard(id,false,false))
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
            .setOverlayState(OVERLAY).createCompositeState(false)));
    }
    public static RenderType opaque(ResourceLocation texture){
        return cache.computeIfAbsent(texture,id->create("bo2_original_opaque",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,true,false,
            CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_SOLID_SHADER).setTextureState(new TextureStateShard(id,false,false))
            .setTransparencyState(NO_TRANSPARENCY).setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(true)));
    }
}
