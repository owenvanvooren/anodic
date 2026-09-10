package uno.owen.anodic.mixin;
import com.metallum.mtl.MTLFence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="com.metallum.render.MetalCommandEncoder",remap=false)
public interface MetalEncoderAccess { @Accessor("fence") MTLFence anodic$fence(); }
