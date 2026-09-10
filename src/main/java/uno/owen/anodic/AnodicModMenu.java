package uno.owen.anodic;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

// Loaded by Mod Menu only when that optional mod is installed.
public final class AnodicModMenu implements ModMenuApi {
    @Override public ConfigScreenFactory<AnodicScreen> getModConfigScreenFactory() { return AnodicScreen::new; }
}
