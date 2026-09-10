package uno.owen.anodic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class AnodicConfigTest {
    @TempDir Path dir;
    @Test void missingConfigUsesQuality() throws Exception {
        assertEquals(AnodicConfig.Preset.QUALITY,AnodicConfig.load(dir.resolve("missing")).preset);
    }
    @Test void invalidValuesFallBackAndValidCaseIsAccepted() throws Exception {
        Path path=dir.resolve("anodic.properties");
        Files.writeString(path,"preset=unknown\nprevious= crisp \n");
        var c=AnodicConfig.load(path);
        assertEquals(AnodicConfig.Preset.QUALITY,c.preset);
        assertEquals(AnodicConfig.Preset.PERFORMANCE,c.previous);
    }
    @Test void quickToggleRestoresChosenQualityAfterRestart() throws Exception {
        Path path=dir.resolve("subdir/anodic.properties");
        var c=new AnodicConfig();c.preset=AnodicConfig.Preset.QUALITY;c.toggle();c.save(path);
        var restored=AnodicConfig.load(path);
        assertEquals(AnodicConfig.Preset.OFF,restored.preset);
        restored.toggle();assertEquals(AnodicConfig.Preset.QUALITY,restored.preset);
        restored.save(path);assertEquals(AnodicConfig.Preset.QUALITY,AnodicConfig.load(path).preset);
        try (var files=Files.list(path.getParent())) { assertEquals(1,files.count()); }
    }
    @Test void corruptPreviousCannotTrapToggleInOff() {
        var c=new AnodicConfig();c.preset=AnodicConfig.Preset.OFF;c.previous=AnodicConfig.Preset.OFF;c.toggle();
        assertEquals(AnodicConfig.Preset.QUALITY,c.preset);
    }
    @Test void importsLegacySettingsWithoutModifyingOriginal() throws Exception {
        Path legacy=dir.resolve("clarity.properties"),target=dir.resolve("anodic.properties");
        Files.writeString(legacy,"preset=SMOOTH\nprevious=CRISP\n");
        var c=AnodicConfig.loadWithFallback(target,legacy);
        assertEquals(AnodicConfig.Preset.PERFORMANCE,c.preset);
        c.save(target);
        assertEquals("preset=SMOOTH\nprevious=CRISP\n",Files.readString(legacy));
        assertEquals(AnodicConfig.Preset.PERFORMANCE,AnodicConfig.load(target).preset);
    }
    @Test void newSettingsTakePrecedenceOverLegacySettings() throws Exception {
        Path legacy=dir.resolve("clarity.properties"),target=dir.resolve("anodic.properties");
        Files.writeString(legacy,"preset=SMOOTH\n");Files.writeString(target,"preset=CRISP\n");
        assertEquals(AnodicConfig.Preset.PERFORMANCE,AnodicConfig.loadWithFallback(target,legacy).preset);
    }
}
