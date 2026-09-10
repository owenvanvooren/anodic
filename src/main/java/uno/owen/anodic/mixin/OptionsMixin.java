package uno.owen.anodic.mixin;

import java.util.Arrays;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uno.owen.anodic.AnodicKeys;

@Mixin(Options.class)
public abstract class OptionsMixin {
    @Shadow @Final @Mutable public KeyMapping[] keyMappings;
    // Register before options.txt is read so rebound keys survive restarts.
    @Inject(method="<init>",at=@At(value="INVOKE",target="Lnet/minecraft/client/Options;load()V"))
    private void anodic$register(CallbackInfo ci) {
        int count=keyMappings.length;
        keyMappings=Arrays.copyOf(keyMappings,count+2);
        keyMappings[count]=AnodicKeys.SETTINGS;
        keyMappings[count+1]=AnodicKeys.TOGGLE;
    }
}
