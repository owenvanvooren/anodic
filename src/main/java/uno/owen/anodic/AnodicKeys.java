package uno.owen.anodic;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class AnodicKeys {
    private static final KeyMapping.Category CATEGORY=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("anodic","controls"));
    public static final KeyMapping SETTINGS=new KeyMapping("key.anodic.settings",GLFW.GLFW_KEY_F8,CATEGORY);
    public static final KeyMapping TOGGLE=new KeyMapping("key.anodic.toggle",InputConstants.UNKNOWN.getValue(),CATEGORY);
    private AnodicKeys() {}
    public static boolean canOpen(Screen screen) {
        return screen==null || screen instanceof TitleScreen || screen instanceof PauseScreen || screen instanceof AnodicScreen;
    }
    public static void open(Minecraft mc) {
        Screen parent=mc.gui.screen();
        if (parent instanceof AnodicScreen screen) screen.onClose();
        else if (canOpen(parent)) mc.gui.setScreen(new AnodicScreen(parent));
    }
    public static void toggle() { Anodic.config.toggle(); TemporalAA.settingsChanged(); Anodic.save(); }
    // Keyboard shortcuts are handled before game input; normal KeyMapping
    // clicks also support bindings assigned to a mouse button in Controls.
    public static void consumeClicks(Minecraft mc) {
        while (SETTINGS.consumeClick()) {
            if (mc.gui.screen()==null) {
                if (mc.hasShiftDown()) toggle(); else open(mc);
            }
        }
        while (TOGGLE.consumeClick()) { if (mc.gui.screen()==null) toggle(); }
    }
}
