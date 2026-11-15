// language: Java, file: MinecraftClientMixin.java
// holds back vanilla item use (shield re-raise) for a few ticks after KillAura released the shield to hit
package com.cheat.mixin;

import com.cheat.module.impl.KillAura;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true, require = 0)
    private void ghost$holdUse(CallbackInfo ci) {
        if (KillAura.useBlock > 0) ci.cancel();
    }
}
