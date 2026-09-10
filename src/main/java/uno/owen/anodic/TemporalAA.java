package uno.owen.anodic;

import com.metallum.mtl.*;
import com.metallum.objc.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.*;
import org.lwjgl.system.MemoryStack;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import static java.lang.foreign.ValueLayout.*;

public final class TemporalAA {
    private static MTLDevice device;
    private static MemorySegment resolvePipeline=MemorySegment.NULL,copyPipeline=MemorySegment.NULL;
    private static final MemorySegment[] history={MemorySegment.NULL,MemorySegment.NULL};
    private static final Msg BYTES=Msg.ofVoid("setFragmentBytes:length:atIndex:",ADDRESS,JAVA_LONG,JAVA_LONG);
    private static final Matrix4f previousVP=new Matrix4f(),currentVP=new Matrix4f(),reprojection=new Matrix4f();
    private static final Vector3f previousForward=new Vector3f(),currentForward=new Vector3f();
    private static final Vector3d previousPos=new Vector3d(),currentPos=new Vector3d();
    private static Object lastWorld,lastCameraType;
    private static long lastFrameTime;
    private static int width,height,index,frame;
    private static boolean valid,prepared;
    private static float jitterX,jitterY,lastFov;
    public static long resolvedFrames;
    public static boolean resolvedThisFrame;
    public static String status="Temporal unavailable";
    public static boolean ready() { return !ObjC.isNil(resolvePipeline); }
    public static void init(MTLDevice dev) {
        close();device=dev;
        try(var stream=TemporalAA.class.getResourceAsStream("/assets/anodic/shaders/temporal.metal")) {
            if(stream==null) throw new IllegalStateException("Missing temporal shader");
            String source=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            resolvePipeline=build(source,"taa_fs",MTLPixelFormat.RGBA16Float.value);
            copyPipeline=build(source,"copy_fs",MTLPixelFormat.RGBA8Unorm.value);
            if(ObjC.isNil(resolvePipeline)||ObjC.isNil(copyPipeline)) throw new IllegalStateException("Temporal pipeline failed");
            status="Temporal ready";
        } catch(Exception e) { close();Anodic.LOG.error("Temporal AA unavailable; using Performance",e); }
    }
    private static MemorySegment build(String source,String fragment,long format) {
        MemorySegment vs=device.newFunction(source,"taa_vs"),fs=device.newFunction(source,fragment);
        try {
            if(ObjC.isNil(vs)||ObjC.isNil(fs)) return MemorySegment.NULL;
            try(var d=new MTLRenderPipelineDescriptor()) {
                d.setCompiledFunctions(vs,fs);d.setColorAttachmentFormat(0,format);
                d.setDepthStencilFormats(MTLPixelFormat.Invalid.value,MTLPixelFormat.Invalid.value);
                d.disableBlending(0,MTLColorWriteMask.All.value);return device.newRenderPipelineState(d);
            }
        } finally { release(vs);release(fs); }
    }
    public static float halton(int n,int base) {
        float result=0,f=1;while(n>0){f/=base;result+=f*(n%base);n/=base;}return result;
    }
    public static Matrix4f prepare(Matrix4f projection,GameRenderer renderer) {
        prepared=false;resolvedThisFrame=false;
        long now=System.nanoTime();if(now-lastFrameTime>250_000_000L)valid=false;lastFrameTime=now;
        Minecraft mc=Minecraft.getInstance();
        if(Anodic.config.preset!=AnodicConfig.Preset.QUALITY) {releaseHistory();return projection;}
        if(!ready() || mc.level==null || mc.gui.screen()!=null) { reset();return projection; }
        var target=renderer.mainRenderTarget();
        if(target.width<=0||target.height<=0||target.getColorTexture().getFormat()!=com.mojang.blaze3d.GpuFormat.RGBA8_UNORM) { reset();return projection; }
        // Allocate before jitter is applied; failure never leaves an unresolved jittered frame.
        try { ensureSize(target.width,target.height); }
        catch(RuntimeException e){Anodic.LOG.error("Cannot allocate temporal history; using Performance",e);close();return projection;}
        var camera=renderer.gameRenderState().levelRenderState.cameraRenderState;
        currentPos.set(camera.pos.x,camera.pos.y,camera.pos.z);
        Object cameraType=renderer.gameRenderState().optionsRenderState.cameraType;
        if(lastWorld!=mc.level || lastCameraType!=cameraType || previousPos.distance(currentPos)>8 || java.lang.Math.abs(projection.m00()-lastFov)>.15f) valid=false;
        currentForward.set(camera.viewRotationMatrix.m02(),camera.viewRotationMatrix.m12(),camera.viewRotationMatrix.m22());
        if(previousForward.dot(currentForward)<.7f) valid=false;
        currentVP.set(projection).mul(camera.viewRotationMatrix);
        reprojection.set(previousVP).translate((float)(currentPos.x-previousPos.x),(float)(currentPos.y-previousPos.y),(float)(currentPos.z-previousPos.z)).mul(new Matrix4f(currentVP).invert());
        if(!reprojection.isFinite()) {valid=false;reprojection.identity();}
        jitterX=(halton((frame%8)+1,2)-.5f)/width;
        jitterY=(halton((frame%8)+1,3)-.5f)/height;
        lastWorld=mc.level;lastCameraType=cameraType;lastFov=projection.m00();
        prepared=true;
        return new Matrix4f().translation(jitterX*2,jitterY*2,0).mul(projection);
    }
    private static void ensureSize(int w,int h) {
        if(width==w&&height==h&&!ObjC.isNil(history[0]))return;
        releaseHistory();width=w;height=h;
        try(var d=MTLTextureDescriptor.create()) {
            d.pixelFormat(MTLPixelFormat.RGBA16Float);d.width(w);d.height(h);
            d.usage(MTLTextureUsage.ShaderRead.value|MTLTextureUsage.RenderTarget.value);
            d.storageMode(MTLStorageMode.Private);
            for(int i=0;i<2;i++)history[i]=device.newTexture(d);
        }
        valid=false;frame=0;index=0;
    }
    public static boolean prepared(){return prepared;}
    public static void encode(MTLCommandBuffer cb,MTLFence fence,MemorySegment color,MemorySegment depth) {
        if(!prepared)return;
        if(MTLTexture.pixelFormat(color)!=MTLPixelFormat.RGBA8Unorm.value) throw new IllegalStateException("Unsupported world color format for temporal resolve");
        try(AutoreleasePool pool=AutoreleasePool.push();MemoryStack stack=MemoryStack.stackPush()) {
            // Clear invalid history before any shader reads it, including the first frame.
            if(!valid) {
                var clear=cb.makeRenderCommandEncoder(history[1-index],new Vector4f(),MemorySegment.NULL,null,width,height);
                clear.waitForFence(fence,MTLRenderStages.VertexAndFragment);
                clear.updateFence(fence,MTLRenderStages.VertexAndFragment);clear.endEncoding();
            }
            var e=cb.makeRenderCommandEncoder(history[index],new Vector4f(),MemorySegment.NULL,null,width,height);
            e.waitForFence(fence,MTLRenderStages.VertexAndFragment);e.setRenderPipelineState(resolvePipeline);
            e.setFragmentTexture(color,0);e.setFragmentTexture(depth,1);e.setFragmentTexture(history[1-index],2);
            var data=MemorySegment.ofAddress(stack.nmalloc(16,80)).reinterpret(80);
            float[] matrix=new float[16];reprojection.get(matrix);
            for(int i=0;i<16;i++)data.set(JAVA_FLOAT,i*4L,matrix[i]);
            data.set(JAVA_FLOAT,64,jitterX);data.set(JAVA_FLOAT,68,jitterY);data.set(JAVA_FLOAT,72,.9f);data.set(JAVA_INT,76,valid?1:0);
            BYTES.send(e.handle(),data,80L,0L);e.drawPrimitives(MTLPrimitiveType.Triangle,0,3,1,0);
            e.updateFence(fence,MTLRenderStages.VertexAndFragment);e.endEncoding();
            var copy=cb.makeRenderCommandEncoder(color,new Vector4f(),MemorySegment.NULL,null,width,height);
            copy.waitForFence(fence,MTLRenderStages.VertexAndFragment);copy.setRenderPipelineState(copyPipeline);copy.setFragmentTexture(history[index],0);
            copy.drawPrimitives(MTLPrimitiveType.Triangle,0,3,1,0);copy.updateFence(fence,MTLRenderStages.VertexAndFragment);copy.endEncoding();
        }
        resolvedThisFrame=true;index=1-index;valid=true;prepared=false;frame++;previousVP.set(currentVP);previousPos.set(currentPos);previousForward.set(currentForward);
        if(resolvedFrames++==0)Anodic.LOG.info("Anodic temporal world resolve active");
    }
    public static void settingsChanged(){if(Anodic.config.preset!=AnodicConfig.Preset.QUALITY)releaseHistory();else reset();}
    public static void reset(){valid=false;frame=0;prepared=false;lastWorld=null;}
    private static void release(MemorySegment p){if(!ObjC.isNil(p))ObjC.release(p);}
    private static void releaseHistory(){for(int i=0;i<2;i++){release(history[i]);history[i]=MemorySegment.NULL;}width=height=0;reset();}
    public static void close(){releaseHistory();release(resolvePipeline);release(copyPipeline);resolvePipeline=copyPipeline=MemorySegment.NULL;device=null;status="Temporal unavailable";}
}
