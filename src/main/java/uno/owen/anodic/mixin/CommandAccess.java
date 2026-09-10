package uno.owen.anodic.mixin;
import com.mojang.blaze3d.systems.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(CommandEncoder.class)
public interface CommandAccess { @Accessor("backend") CommandEncoderBackend anodic$backend(); }
