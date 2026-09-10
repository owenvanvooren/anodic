package uno.owen.anodic.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uno.owen.anodic.*;

@Mixin(Minecraft.class)
public abstract class SmokeMixin {
    @Unique private int anodic$ticks;
    @Unique private int anodic$worldTicks;
    @Unique private boolean anodic$started;
    @Unique private long anodic$before;
    @Inject(method="tick",at=@At("RETURN"))
    private void anodic$test(CallbackInfo ci) {
        if (!Boolean.getBoolean("anodic.smoke")) return;
        Minecraft mc=(Minecraft)(Object)this;
        if (++anodic$ticks>2400) throw new IllegalStateException("Anodic smoke timed out");
        if (!anodic$started && mc.isGameLoadFinished() && mc.gui.overlay()==null) {
            anodic$started=true;
            mc.createWorldOpenFlows().createFreshLevel("anodic-smoke-"+System.currentTimeMillis(),
                new LevelSettings("Anodic smoke",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),
                new WorldOptions(12345L,false,false),
                provider -> provider.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.gui.screen());
        }
        if (mc.level==null || mc.player==null) return;
        anodic$worldTicks++;
        if(mc.gui.screen()==null){if(anodic$worldTicks>70)mc.player.setYRot(mc.player.getYRot()+.15f);mc.player.setXRot(12f);}
        if(anodic$worldTicks==70) {
            try {
                var frame=TemporalAA.class.getDeclaredField("frame");frame.setAccessible(true);
                if(frame.getInt(null)<8)throw new IllegalStateException("Stationary temporal history keeps resetting");
            } catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        }
        if (anodic$worldTicks==100) {
            if (TemporalAA.resolvedFrames==0) throw new IllegalStateException("No filtered world frames");
            anodic$before=(MetalAA.filteredFrames+TemporalAA.resolvedFrames);
            ((KeyInvoker)mc.keyboardHandler).anodic$press(mc.getWindow().handle(),1,new KeyEvent(297,0,1));
            if (Anodic.config.preset!=AnodicConfig.Preset.OFF) throw new IllegalStateException("Shift+F8 failed");
        }
        if (anodic$worldTicks==120) {
            if ((MetalAA.filteredFrames+TemporalAA.resolvedFrames)!=anodic$before) throw new IllegalStateException("Off did not bypass");
            Anodic.config.preset=AnodicConfig.Preset.PERFORMANCE;TemporalAA.settingsChanged();
        }
        if (anodic$worldTicks==140) {
            if ((MetalAA.filteredFrames+TemporalAA.resolvedFrames)<=anodic$before) throw new IllegalStateException("Performance did not resume");
            Anodic.config.preset=AnodicConfig.Preset.QUALITY;
        }
        if (anodic$worldTicks==160) {
            ((KeyInvoker)mc.keyboardHandler).anodic$press(mc.getWindow().handle(),1,new KeyEvent(297,0,0));
            if (!(mc.gui.screen() instanceof AnodicScreen)) throw new IllegalStateException("F8 settings failed");
            anodic$before=(MetalAA.filteredFrames+TemporalAA.resolvedFrames);
        }
        if (anodic$worldTicks==175) Screenshot.grab(mc.gameDirectory,"anodic-settings.png",mc.gameRenderer.mainRenderTarget(),1,message -> {});
        if (anodic$worldTicks==180) {
            if ((MetalAA.filteredFrames+TemporalAA.resolvedFrames)!=anodic$before) throw new IllegalStateException("Settings screen did not bypass AA");
            ((KeyInvoker)mc.keyboardHandler).anodic$press(mc.getWindow().handle(),1,new KeyEvent(297,0,0));
            if (mc.gui.screen()!=null) throw new IllegalStateException("F8 close failed");
            Anodic.config.preset=AnodicConfig.Preset.QUALITY;Anodic.save();
        }
        if (anodic$worldTicks==200) {
            if ((MetalAA.filteredFrames+TemporalAA.resolvedFrames)<=anodic$before) throw new IllegalStateException("Quality did not resume");
            Anodic.LOG.info("ANODIC_SMOKE_PASS: world rendering, Off bypass, Performance/Quality F8/Shift+F8 and settings screen passed; {} filtered frames",(MetalAA.filteredFrames+TemporalAA.resolvedFrames));
            mc.stop();
        }
    }
}
