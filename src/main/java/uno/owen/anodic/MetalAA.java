package uno.owen.anodic;

import com.metallum.mtl.*;
import com.metallum.objc.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryStack;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import static java.lang.foreign.ValueLayout.*;

public final class MetalAA {
    public static long filteredFrames;
    private static MemorySegment pipeline=MemorySegment.NULL;
    private static final Msg SET_FRAGMENT_BYTES=Msg.ofVoid("setFragmentBytes:length:atIndex:",ADDRESS,JAVA_LONG,JAVA_LONG);
    public static String status="Waiting for Metal";
    private MetalAA() {}
    public static void init(MTLDevice device) {
        close();
        MemorySegment vertex=MemorySegment.NULL, fragment=MemorySegment.NULL;
        try (AutoreleasePool pool=AutoreleasePool.push()) {
            String source;
            try (var stream=MetalAA.class.getResourceAsStream("/assets/anodic/shaders/anodic.metal")) {
                if (stream == null) throw new IllegalStateException("Missing Anodic shader");
                source=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            }
            vertex=device.newFunction(source,"anodic_vs");
            fragment=device.newFunction(source,"anodic_fs");
            if (ObjC.isNil(vertex) || ObjC.isNil(fragment)) throw new IllegalStateException("Metal shader compilation failed");
            try (var descriptor=new MTLRenderPipelineDescriptor()) {
                descriptor.setCompiledFunctions(vertex,fragment);
                descriptor.setColorAttachmentFormat(0,MTLPixelFormat.BGRA8Unorm.value);
                descriptor.setDepthStencilFormats(MTLPixelFormat.Invalid.value,MTLPixelFormat.Invalid.value);
                descriptor.disableBlending(0,MTLColorWriteMask.All.value);
                pipeline=device.newRenderPipelineState(descriptor);
            }
            if (ObjC.isNil(pipeline)) throw new IllegalStateException("Metal pipeline creation failed");
            status="Native Metal ready";
            TemporalAA.init(device);
            Anodic.LOG.info("Anodic Metal pipelines initialized");
        } catch (Exception e) {
            close(); status="Unavailable; using Metallum presentation";
            Anodic.LOG.error("Anodic disabled; Metallum presentation remains active",e);
        } finally {
            if (!ObjC.isNil(vertex)) ObjC.release(vertex);
            if (!ObjC.isNil(fragment)) ObjC.release(fragment);
        }
    }
    public static void bind(MTLRenderCommandEncoder encoder, MemorySegment original) {
        Minecraft mc=Minecraft.getInstance();
        var preset=Anodic.config.preset;
        if (ObjC.isNil(pipeline) || preset==AnodicConfig.Preset.OFF || (preset==AnodicConfig.Preset.QUALITY && TemporalAA.resolvedThisFrame) || mc.level==null || mc.gui.screen()!=null) {
            encoder.setRenderPipelineState(original); return;
        }
        try (MemoryStack stack=MemoryStack.stackPush()) {
            MemorySegment data=MemorySegment.ofAddress(stack.nmalloc(16,16)).reinterpret(16);
            data.set(JAVA_FLOAT,0,preset.relative); data.set(JAVA_FLOAT,4,preset.minimum);
            data.set(JAVA_FLOAT,8,preset.subpixel); data.set(JAVA_INT,12,preset.steps);
            SET_FRAGMENT_BYTES.send(encoder.handle(),data,16L,0L);
        }
        encoder.setRenderPipelineState(pipeline);
        if (filteredFrames++ == 0) Anodic.LOG.info("Anodic filtering live world frames");
    }
    public static void close() {
        TemporalAA.close();
        if (!ObjC.isNil(pipeline)) { ObjC.release(pipeline); pipeline=MemorySegment.NULL; }
        status="Metal inactive";
    }
}
