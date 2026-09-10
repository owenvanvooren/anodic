package uno.owen.anodic.smoke;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(KeyboardHandler.class)
public interface KeyInvoker {
    @Invoker("keyPress") void anodic$press(long window,int action,KeyEvent event);
}
