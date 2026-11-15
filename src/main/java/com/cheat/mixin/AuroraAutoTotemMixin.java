// language: Java, file: AuroraAutoTotemMixin.java
// Aurora's AutoTotemModule.onTick() is cancelled, GhostMod's AutoTotem runs instead (module on/off + settings stay Aurora's).
package com.cheat.mixin;

import com.cheat.GhostMod;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.aurora.client.module.impl.combat.AutoTotemModule", remap = false)
public abstract class AuroraAutoTotemMixin {
    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ghost$replaceTick(CallbackInfo ci) {
        GhostMod.MODULES.autoTotem.tick(MinecraftClient.getInstance(), this);
        ci.cancel();
    }
}
