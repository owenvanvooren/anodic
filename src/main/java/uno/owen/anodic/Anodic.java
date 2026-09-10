package uno.owen.anodic;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.nio.file.Files;

public final class Anodic implements ClientModInitializer {
    public static final Logger LOG=LoggerFactory.getLogger("Anodic");
    public static AnodicConfig config=new AnodicConfig();
    private static final Path CONFIG=FabricLoader.getInstance().getConfigDir().resolve("anodic.properties");
    public static String saveError="";
    @Override public void onInitializeClient() {
        try {
            Path legacy=CONFIG.resolveSibling("clarity.properties");
            config=AnodicConfig.loadWithFallback(CONFIG,legacy);
            if (!Files.exists(CONFIG) && Files.exists(legacy)) save();
        }
        catch (Exception e) { LOG.warn("Could not read Anodic settings; using Balanced",e); }
        LOG.info("Anodic: {}. Settings: Mods > Anodic or Controls > Key Binds > Anodic.",config.preset.label);
    }
    public static void save() {
        try { config.save(CONFIG); saveError=""; }
        catch (Exception e) { saveError="Could not save settings. Check latest.log."; LOG.warn(saveError,e); }
    }
}
