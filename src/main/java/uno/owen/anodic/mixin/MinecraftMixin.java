package uno.owen.anodic.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uno.owen.anodic.AnodicKeys;

@Mixin(KeyboardHandler.class)
public abstract class MinecraftMixin {
    @Inject(method="keyPress",at=@At("HEAD"),cancellable=true)
    private void anodic$key(long window,int action,KeyEvent event,CallbackInfo ci) {
        Minecraft mc=Minecraft.getInstance();
        if (window!=mc.getWindow().handle() || action!=GLFW.GLFW_PRESS) return;
        // Leave typing, key-rebinding screens and other mod dialogs alone.
        if (!AnodicKeys.canOpen(mc.gui.screen())) return;
        if (AnodicKeys.SETTINGS.matches(event)) {
            if ((event.modifiers() & GLFW.GLFW_MOD_SHIFT)!=0 && mc.gui.screen()==null) AnodicKeys.toggle();
            else AnodicKeys.open(mc);
        } else if (mc.gui.screen()==null && AnodicKeys.TOGGLE.matches(event)) AnodicKeys.toggle();
        else return;
        ci.cancel();
    }
}
