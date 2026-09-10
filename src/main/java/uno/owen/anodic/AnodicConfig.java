package uno.owen.anodic;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public final class AnodicConfig {
    public enum Preset {
        OFF("Off", 0, 0, 0, 0),
        PERFORMANCE("Performance", .125f, .0312f, .25f, 5),
        QUALITY("Quality", .125f, .0312f, .25f, 8);
        public final String label;
        public final float relative, minimum, subpixel;
        public final int steps;
        Preset(String label, float relative, float minimum, float subpixel, int steps) {
            this.label=label; this.relative=relative; this.minimum=minimum; this.subpixel=subpixel; this.steps=steps;
        }
        public Preset next() { return values()[(ordinal()+1)%values().length]; }
    }
    public Preset preset = Preset.QUALITY;
    public Preset previous = Preset.QUALITY;
    public void toggle() {
        if (preset == Preset.OFF) preset = previous == Preset.OFF ? Preset.QUALITY : previous;
        else { previous = preset; preset = Preset.OFF; }
    }
    public static AnodicConfig loadWithFallback(Path file, Path legacy) throws IOException {
        return load(Files.exists(file) ? file : legacy);
    }
    public static AnodicConfig load(Path file) throws IOException {
        AnodicConfig c=new AnodicConfig();
        if (!Files.exists(file)) return c;
        Properties p=new Properties();
        try (Reader r=Files.newBufferedReader(file)) { p.load(r); }
        c.preset=parse(p.getProperty("preset"), Preset.QUALITY);
        c.previous=parse(p.getProperty("previous"), Preset.QUALITY);
        return c;
    }
    private static Preset parse(String value, Preset fallback) {
        try {
            String name=value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
            if (name.equals("CRISP") || name.equals("BALANCED") || name.equals("SMOOTH")) return Preset.PERFORMANCE;
            return Preset.valueOf(name);
        }
        catch (IllegalArgumentException ex) { return fallback; }
    }
    public void save(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temp=Files.createTempFile(file.toAbsolutePath().getParent(), "anodic-", ".tmp");
        try {
            Properties p=new Properties(); p.setProperty("preset",preset.name()); p.setProperty("previous",previous.name());
            try (Writer w=Files.newBufferedWriter(temp)) { p.store(w,"Anodic: OFF, PERFORMANCE, QUALITY. F8 opens settings; Shift+F8 toggles."); }
            try { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
