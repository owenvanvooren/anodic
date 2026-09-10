package uno.owen.anodic.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uno.owen.anodic.AnodicKeys;

@Mixin(Minecraft.class)
public abstract class InputTickMixin {
    @Inject(method="tick",at=@At("HEAD"))
    private void anodic$clicks(CallbackInfo ci) { AnodicKeys.consumeClicks((Minecraft)(Object)this); }
}
