// language: Java, file: AuroraHoverMixin.java
// Aurora Client Hover guard. inert if Aurora isn't installed. require=0 -> never crashes on a renamed method.
package com.cheat.mixin;

import com.cheat.module.impl.HoverGuard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.aurora.client.module.impl.movement.HoverModule", remap = false)
public abstract class AuroraHoverMixin {

    @Unique private int ghost$ticks;

    @Inject(method = "onEnable", at = @At("HEAD"), remap = false, require = 0)
    private void ghost$on(CallbackInfo ci) { ghost$ticks = 0; HoverGuard.setback = false; }

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ghost$tick(CallbackInfo ci) {
        boolean sb = HoverGuard.setback;
        if (!sb && ++ghost$ticks <= HoverGuard.MAX_TICKS) return;
        HoverGuard.setback = false;
        ci.cancel();
        try { getClass().getMethod("setEnabled", boolean.class).invoke(this, false); } catch (Throwable ignored) {}
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null)
            mc.player.sendMessage(Text.literal("§7[Ghost] Hover off: " + (sb ? "server setback" : "time limit")), true);
    }
}
