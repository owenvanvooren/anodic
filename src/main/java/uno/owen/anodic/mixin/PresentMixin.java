package uno.owen.anodic.mixin;

import com.metallum.mtl.*;
import java.lang.foreign.MemorySegment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uno.owen.anodic.MetalAA;

@Mixin(value=MTLBuiltinPipelines.class, remap=false)
public abstract class PresentMixin {
    @Inject(method="init", at=@At("RETURN"))
    private static void anodic$init(MTLDevice device, CallbackInfo ci) { MetalAA.init(device); }
    @Redirect(method="encodePresentTextureToDrawable", at=@At(value="INVOKE", target="Lcom/metallum/mtl/MTLRenderCommandEncoder;setRenderPipelineState(Ljava/lang/foreign/MemorySegment;)V"))
    private static void anodic$bind(MTLRenderCommandEncoder encoder, MemorySegment pipeline) { MetalAA.bind(encoder,pipeline); }
    @Inject(method="close", at=@At("HEAD"))
    private static void anodic$close(CallbackInfo ci) { MetalAA.close(); }
}
