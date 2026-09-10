package uno.owen.anodic.mixin;
import com.metallum.render.AnodicTemporalBridge;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uno.owen.anodic.TemporalAA;
@Mixin(GameRenderer.class)
public abstract class WorldMixin {
    @ModifyArg(method="renderLevel",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),index=0)
    private Matrix4f anodic$jitter(Matrix4f matrix){return TemporalAA.prepare(matrix,(GameRenderer)(Object)this);}
    @Inject(method="renderLevel",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/Projection;setupPerspective(FFFFF)V"))
    private void anodic$resolve(DeltaTracker dt,CallbackInfo ci){if(TemporalAA.prepared())AnodicTemporalBridge.resolve(((GameRenderer)(Object)this).mainRenderTarget());}
}
