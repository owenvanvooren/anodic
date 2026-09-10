package com.metallum.render;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import uno.owen.anodic.TemporalAA;
import uno.owen.anodic.mixin.*;

/** Version-pinned bridge sharing Metallum's render command buffer and fence. */
public final class AnodicTemporalBridge {
    public static void resolve(RenderTarget target) {
        var backend=((CommandAccess)RenderSystem.getDevice().createCommandEncoder()).anodic$backend();
        if (!(backend instanceof MetalCommandEncoder encoder)) return;
        var color=(MetalGpuTexture)target.getColorTexture();
        var depth=(MetalGpuTexture)target.getDepthTexture();
        encoder.submitRenderPass();
        encoder.flushPendingClear(color);encoder.flushPendingClear(depth);encoder.endEncoder();
        TemporalAA.encode(encoder.commandBuffer(),((MetalEncoderAccess)(Object)encoder).anodic$fence(),color.nativeHandle(),depth.nativeHandle());
        color.markContentsDirty();
    }
}
