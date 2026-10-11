package local.bo2beta;

import com.google.gson.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Original GLB skin/clip evaluator. Unsupported formats fail instead of substituting a rig. */
public final class OriginalMesh {
    private final JsonObject document;
    private final ByteBuffer binary;
    private final Map<Integer,float[]> accessors=new HashMap<>();
    private final Map<String,Clip> clips=new HashMap<>();
    private final Node[] nodes;
    private final List<Primitive> primitives=new ArrayList<>();
    private final Skin[] skins;
    private final Quaternionf quaternion=new Quaternionf(),other=new Quaternionf();
    private int worldEpoch=1;
    private static final class Node {
        String name;int parent=-1,epoch;float[] translation,rotation,scale;Matrix4f bind;
        final float[] sampledTranslation=new float[3],sampledRotation=new float[4],sampledScale=new float[3];
        boolean moved,turned,scaled;
        final Matrix4f local=new Matrix4f(),world=new Matrix4f();
    }
    private record Track(int node,String path,String interpolation,float[] times,float[] values,int width){}
    private record Clip(List<Track> tracks,float duration){}
    private static final class Skin {
        int[] joints;Matrix4f[] inverse,matrices;
    }
    private static final class Primitive {
        int node,skin;float[] position,normal,uv,joints,weights,output,outputNormal;int[] indices;
        ResourceLocation texture;
    }
    public OriginalMesh(ResourceLocation location){
        try(var stream=Minecraft.getInstance().getResourceManager().getResourceOrThrow(location).open()){
            var data=ByteBuffer.wrap(stream.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            require(data.getInt()==0x46546c67 && data.getInt()==2,"GLB version");require(data.getInt()==data.capacity(),"GLB length");
            int length=data.getInt();require(data.getInt()==0x4e4f534a,"GLB JSON chunk");byte[] json=new byte[length];data.get(json);
            document=JsonParser.parseString(new String(json,StandardCharsets.UTF_8)).getAsJsonObject();
            int binaryLength=data.getInt();require(data.getInt()==0x004e4942 && binaryLength<=data.remaining(),"GLB binary chunk");
            binary=data.slice().order(ByteOrder.LITTLE_ENDIAN);binary.limit(binaryLength);
            var sourceNodes=document.getAsJsonArray("nodes");nodes=new Node[sourceNodes.size()];
            for(int i=0;i<nodes.length;i++){
                var source=sourceNodes.get(i).getAsJsonObject();var n=new Node();nodes[i]=n;
                n.name=source.has("name")?source.get("name").getAsString():"";
                if(source.has("matrix"))n.bind=new Matrix4f().set(vector(source,"matrix",new float[16]));
                n.translation=vector(source,"translation",new float[]{0,0,0});n.rotation=vector(source,"rotation",new float[]{0,0,0,1});n.scale=vector(source,"scale",new float[]{1,1,1});
            }
            for(int i=0;i<nodes.length;i++){
                var source=sourceNodes.get(i).getAsJsonObject();if(source.has("children"))for(var child:source.getAsJsonArray("children")){
                    int index=child.getAsInt();require(index>=0 && index<nodes.length && nodes[index].parent==-1,"Node parent");nodes[index].parent=i;
                }
            }
            var sourceSkins=document.getAsJsonArray("skins");skins=new Skin[sourceSkins==null?0:sourceSkins.size()];
            for(int i=0;i<skins.length;i++){
                var source=sourceSkins.get(i).getAsJsonObject();var skin=new Skin();skins[i]=skin;
                skin.joints=integers(source.getAsJsonArray("joints"));var inverse=accessor(source.get("inverseBindMatrices").getAsInt());
                require(inverse.length==skin.joints.length*16,"Inverse bind count");skin.inverse=new Matrix4f[skin.joints.length];skin.matrices=new Matrix4f[skin.joints.length];
                for(int j=0;j<skin.joints.length;j++){skin.inverse[j]=new Matrix4f().set(Arrays.copyOfRange(inverse,j*16,j*16+16));skin.matrices[j]=new Matrix4f();}
            }
            for(int i=0;i<nodes.length;i++){
                var source=sourceNodes.get(i).getAsJsonObject();if(!source.has("mesh"))continue;
                var mesh=document.getAsJsonArray("meshes").get(source.get("mesh").getAsInt()).getAsJsonObject();
                for(var p:mesh.getAsJsonArray("primitives")){
                    var sourcePrimitive=p.getAsJsonObject();require(!sourcePrimitive.has("mode") || sourcePrimitive.get("mode").getAsInt()==4,"Triangle topology");
                    var a=sourcePrimitive.getAsJsonObject("attributes");var primitive=new Primitive();primitive.node=i;primitive.skin=source.has("skin")?source.get("skin").getAsInt():-1;
                    primitive.position=accessor(a.get("POSITION").getAsInt());primitive.normal=accessor(a.get("NORMAL").getAsInt());primitive.uv=accessor(a.get("TEXCOORD_0").getAsInt());
                    primitive.indices=indices(sourcePrimitive.get("indices").getAsInt());require(primitive.indices.length%3==0,"Triangle count");
                    if(primitive.skin>=0){primitive.joints=accessor(a.get("JOINTS_0").getAsInt());primitive.weights=accessor(a.get("WEIGHTS_0").getAsInt());}
                    var material=document.getAsJsonArray("materials").get(sourcePrimitive.get("material").getAsInt()).getAsJsonObject();
                    int texture=material.getAsJsonObject("pbrMetallicRoughness").getAsJsonObject("baseColorTexture").get("index").getAsInt();
                    int image=document.getAsJsonArray("textures").get(texture).getAsJsonObject().get("source").getAsInt();
                    primitive.texture=new ResourceLocation(document.getAsJsonArray("images").get(image).getAsJsonObject().get("uri").getAsString());
                    primitive.output=new float[primitive.position.length];primitive.outputNormal=new float[primitive.normal.length];primitives.add(primitive);
                }
            }
            for(var a:document.has("animations")?document.getAsJsonArray("animations"):new JsonArray()){
                var source=a.getAsJsonObject();var tracks=new ArrayList<Track>();float duration=0;
                for(var c:source.getAsJsonArray("channels")){
                    var channel=c.getAsJsonObject();var target=channel.getAsJsonObject("target");var sampler=source.getAsJsonArray("samplers").get(channel.get("sampler").getAsInt()).getAsJsonObject();
                    String interpolation=sampler.has("interpolation")?sampler.get("interpolation").getAsString():"LINEAR";
                    require(interpolation.equals("LINEAR") || interpolation.equals("STEP"),"Original interpolation");
                    float[] times=accessor(sampler.get("input").getAsInt()),values=accessor(sampler.get("output").getAsInt());
                    String path=target.get("path").getAsString();int width=path.equals("rotation")?4:3;
                    require(values.length==times.length*width && times.length>0,"Animation key count");duration=Math.max(duration,times[times.length-1]);
                    tracks.add(new Track(target.get("node").getAsInt(),path,interpolation,times,values,width));
                }
                clips.put(source.get("name").getAsString(),new Clip(tracks,duration));
            }
        }catch(Exception e){throw new IllegalStateException("Original viewmodel failed: "+location,e);}
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
    private static float[] vector(JsonObject o,String key,float[] fallback){if(!o.has(key))return fallback;var values=o.getAsJsonArray(key);float[] result=new float[values.size()];for(int i=0;i<result.length;i++)result[i]=values.get(i).getAsFloat();return result;}
    private static int[] integers(JsonArray values){int[] result=new int[values.size()];for(int i=0;i<result.length;i++)result[i]=values.get(i).getAsInt();return result;}
    private int[] indices(int index){float[] values=accessor(index);int[] result=new int[values.length];for(int i=0;i<result.length;i++)result[i]=(int)values[i];return result;}
    private float[] accessor(int index){return accessors.computeIfAbsent(index,key->{
        var a=document.getAsJsonArray("accessors").get(key).getAsJsonObject();require(!a.has("sparse"),"Sparse accessor");
        var view=document.getAsJsonArray("bufferViews").get(a.get("bufferView").getAsInt()).getAsJsonObject();
        int components=switch(a.get("type").getAsString()){case "SCALAR"->1;case "VEC2"->2;case "VEC3"->3;case "VEC4"->4;case "MAT4"->16;default->throw new IllegalArgumentException("Accessor type");};
        int type=a.get("componentType").getAsInt(),bytes=switch(type){case 5120,5121->1;case 5122,5123->2;case 5125,5126->4;default->throw new IllegalArgumentException("Component type");};
        int count=a.get("count").getAsInt(),stride=view.has("byteStride")?view.get("byteStride").getAsInt():bytes*components;
        int start=(view.has("byteOffset")?view.get("byteOffset").getAsInt():0)+(a.has("byteOffset")?a.get("byteOffset").getAsInt():0);
        require(count>0 && count<2_000_000 && start>=0 && (long)start+(long)(count-1)*stride+components*bytes<=binary.limit(),"Accessor bounds");
        float[] result=new float[count*components];boolean normalized=a.has("normalized") && a.get("normalized").getAsBoolean();
        for(int i=0;i<count;i++)for(int j=0;j<components;j++){
            int offset=start+i*stride+j*bytes;float value=switch(type){case 5120->binary.get(offset);case 5121->Byte.toUnsignedInt(binary.get(offset));case 5122->binary.getShort(offset);case 5123->Short.toUnsignedInt(binary.getShort(offset));case 5125->Integer.toUnsignedLong(binary.getInt(offset));case 5126->binary.getFloat(offset);default->0;};
            if(normalized)value=switch(type){case 5120->Math.max(-1,value/127);case 5121->value/255;case 5122->Math.max(-1,value/32767);case 5123->value/65535;default->value;};
            require(Float.isFinite(value),"Nonfinite original value");result[i*components+j]=value;
        }return result;
    });}
    public boolean hasClip(String name){return clips.containsKey(name);}
    public float duration(String clip){var c=clips.get(clip);if(c==null)throw new IllegalArgumentException("Missing original clip "+clip);return c.duration;}
    public void sample(String name,float seconds,boolean loop,JsonObject aim,float progress,boolean lowering){sampleInternal(name,seconds,loop,aim,progress,lowering,true,false);}
    public void sampleNative(String name,float seconds,boolean loop,boolean layer){sampleInternal(name,seconds,loop,null,0,false,!layer,true);}
    private void sampleInternal(String name,float seconds,boolean loop,JsonObject aim,float progress,boolean lowering,boolean reset,boolean nativeInterpolation){
        var clip=clips.get(name);if(clip==null)throw new IllegalArgumentException("Wrong gun clip "+name);
        if(reset)for(var n:nodes){if(n.bind!=null)n.local.set(n.bind);else n.local.translationRotateScale(n.translation[0],n.translation[1],n.translation[2],n.rotation[0],n.rotation[1],n.rotation[2],n.rotation[3],n.scale[0],n.scale[1],n.scale[2]);}
        float time=loop && clip.duration>0?Math.max(0,seconds)%clip.duration:Math.max(0,Math.min(seconds,clip.duration));
        // Accumulate all channels in TRS scratch arrays before constructing matrices.
        for(var n:nodes){
            n.moved=!reset;n.turned=!reset;n.scaled=!reset;
            if(!reset){var v=new org.joml.Vector3f();n.local.getTranslation(v);n.sampledTranslation[0]=v.x;n.sampledTranslation[1]=v.y;n.sampledTranslation[2]=v.z;n.local.getScale(v);n.sampledScale[0]=v.x;n.sampledScale[1]=v.y;n.sampledScale[2]=v.z;n.local.getUnnormalizedRotation(quaternion);n.sampledRotation[0]=quaternion.x;n.sampledRotation[1]=quaternion.y;n.sampledRotation[2]=quaternion.z;n.sampledRotation[3]=quaternion.w;}
        }
        for(var track:clip.tracks){
            int b=0;while(b<track.times.length-1 && track.times[b]<time)b++;int a=Math.max(0,b-1);
            float t=a==b || track.interpolation.equals("STEP")?0:Math.max(0,Math.min(1,(time-track.times[a])/(track.times[b]-track.times[a])));
            var target=nodes[track.node];
            float[] values=track.path.equals("rotation")?target.sampledRotation:track.path.equals("translation")?target.sampledTranslation:target.sampledScale;
            if(track.path.equals("rotation")){
                quaternion.set(track.values[a*4],track.values[a*4+1],track.values[a*4+2],track.values[a*4+3]).normalize();
                other.set(track.values[b*4],track.values[b*4+1],track.values[b*4+2],track.values[b*4+3]).normalize();if(nativeInterpolation)quaternion.nlerp(other,t).normalize();else quaternion.slerp(other,t);
                values[0]=quaternion.x;values[1]=quaternion.y;values[2]=quaternion.z;values[3]=quaternion.w;target.turned=true;
            }else {for(int i=0;i<track.width;i++)values[i]=track.values[a*track.width+i]+t*(track.values[b*track.width+i]-track.values[a*track.width+i]);if(track.path.equals("translation"))target.moved=true;else if(track.path.equals("scale"))target.scaled=true;}
        }
        if(aim!=null){
            var curve=aim.getAsJsonArray(lowering?"down":"up");float point=lowering?1-progress:progress;int b=0;while(b<curve.size()-1 && curve.get(b).getAsJsonArray().get(0).getAsFloat()<point)b++;int a=Math.max(0,b-1);
            var ka=curve.get(a).getAsJsonArray();var kb=curve.get(b).getAsJsonArray();float t=a==b?0:Math.max(0,Math.min(1,(point-ka.get(0).getAsFloat())/(kb.get(0).getAsFloat()-ka.get(0).getAsFloat())));
            for(int i=0;i<nodes.length;i++)if(nodes[i].name.equals("tag_torso")){
                var target=nodes[i];target.moved=true;target.turned=true;for(int k=0;k<3;k++)target.sampledTranslation[k]=ka.get(k+1).getAsFloat()+t*(kb.get(k+1).getAsFloat()-ka.get(k+1).getAsFloat());
                quaternion.set(ka.get(4).getAsFloat(),ka.get(5).getAsFloat(),ka.get(6).getAsFloat(),ka.get(7).getAsFloat()).normalize();other.set(kb.get(4).getAsFloat(),kb.get(5).getAsFloat(),kb.get(6).getAsFloat(),kb.get(7).getAsFloat()).normalize();quaternion.slerp(other,t);
                target.sampledRotation[0]=quaternion.x;target.sampledRotation[1]=quaternion.y;target.sampledRotation[2]=quaternion.z;target.sampledRotation[3]=quaternion.w;
            }
        }
        for(int i=0;i<nodes.length;i++){
            var n=nodes[i];var p=n.moved?n.sampledTranslation:n.translation;var r=n.turned?n.sampledRotation:n.rotation;var s=n.scaled?n.sampledScale:n.scale;
            n.local.translationRotateScale(p[0],p[1],p[2],r[0],r[1],r[2],r[3],s[0],s[1],s[2]);
        }
        worldEpoch++;for(int i=0;i<nodes.length;i++)world(i,0);
        for(var skin:skins)for(int i=0;i<skin.joints.length;i++)skin.matrices[i].set(nodes[skin.joints[i]].world).mul(skin.inverse[i]);
    }
    public void followPose(OriginalMesh driver){
        require(nodes.length>=driver.nodes.length,"Shared native viewhand rig size");
        for(int i=0;i<driver.nodes.length;i++){require(nodes[i].name.equals(driver.nodes[i].name),"Shared native viewhand joint order");nodes[i].local.set(driver.nodes[i].local);}
        worldEpoch++;for(int i=0;i<nodes.length;i++)world(i,0);
        for(var skin:skins)for(int i=0;i<skin.joints.length;i++)skin.matrices[i].set(nodes[skin.joints[i]].world).mul(skin.inverse[i]);
    }
    public void sampleBind(){
        for(var n:nodes){if(n.bind!=null)n.local.set(n.bind);else n.local.translationRotateScale(n.translation[0],n.translation[1],n.translation[2],n.rotation[0],n.rotation[1],n.rotation[2],n.rotation[3],n.scale[0],n.scale[1],n.scale[2]);}
        worldEpoch++;for(int i=0;i<nodes.length;i++)world(i,0);
        for(var skin:skins)for(int i=0;i<skin.joints.length;i++)skin.matrices[i].set(nodes[skin.joints[i]].world).mul(skin.inverse[i]);
    }
    private void world(int i,int depth){require(depth<nodes.length,"Cyclic original rig");var n=nodes[i];if(n.epoch==worldEpoch)return;if(n.parent<0)n.world.set(n.local);else{world(n.parent,depth+1);nodes[n.parent].world.mul(n.local,n.world);}n.epoch=worldEpoch;}
    public Matrix4f attachment(String name){for(var n:nodes)if(n.name.equals(name))return n.world;throw new IllegalArgumentException("Missing original attachment "+name);}
    public void draw(PoseStack pose,MultiBufferSource buffers,int light){
        for(var p:primitives){
            for(int i=0;i<p.position.length/3;i++){
                float x=p.position[i*3],y=p.position[i*3+1],z=p.position[i*3+2],nx=p.normal[i*3],ny=p.normal[i*3+1],nz=p.normal[i*3+2];
                float ox=0,oy=0,oz=0,onx=0,ony=0,onz=0;
                for(int j=0;j<(p.skin<0?1:4);j++){
                    float weight=p.skin<0?1:p.weights[i*4+j];if(weight==0)continue;
                    var m=p.skin<0?nodes[p.node].world:skins[p.skin].matrices[(int)p.joints[i*4+j]];
                    ox+=weight*(m.m00()*x+m.m10()*y+m.m20()*z+m.m30());oy+=weight*(m.m01()*x+m.m11()*y+m.m21()*z+m.m31());oz+=weight*(m.m02()*x+m.m12()*y+m.m22()*z+m.m32());
                    onx+=weight*(m.m00()*nx+m.m10()*ny+m.m20()*nz);ony+=weight*(m.m01()*nx+m.m11()*ny+m.m21()*nz);onz+=weight*(m.m02()*nx+m.m12()*ny+m.m22()*nz);
                }
                p.output[i*3]=ox;p.output[i*3+1]=oy;p.output[i*3+2]=oz;float norm=(float)Math.sqrt(onx*onx+ony*ony+onz*onz);if(norm==0)norm=1;
                p.outputNormal[i*3]=onx/norm;p.outputNormal[i*3+1]=ony/norm;p.outputNormal[i*3+2]=onz/norm;
            }
            var out=buffers.getBuffer(OriginalRenderTypes.opaque(p.texture));
            for(int t=0;t<p.indices.length;t+=3)for(int corner=0;corner<4;corner++){
                int i=p.indices[t+Math.min(corner,2)];require(i>=0 && i<p.position.length/3,"Original triangle index");
                out.vertex(pose.last().pose(),p.output[i*3],p.output[i*3+1],p.output[i*3+2]).color(255,255,255,255).uv(p.uv[i*2],p.uv[i*2+1])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.last().normal(),p.outputNormal[i*3],p.outputNormal[i*3+1],p.outputNormal[i*3+2]).endVertex();
            }
        }
    }
}
