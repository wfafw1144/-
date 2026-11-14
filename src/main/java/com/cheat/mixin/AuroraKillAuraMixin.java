// language: Java, file: AuroraKillAuraMixin.java
// Aurora Client integration (optional: if Aurora isn't installed this target never loads and the mixin is inert).
// Aurora's KillAuraModule.onTick() is cancelled and GhostMod's KillAura runs instead; Aurora's toggle / bind / GUI
// switch ours on and off. No Aurora code is copied or modified, nothing needs patching in its jar.
package com.cheat.mixin;

import com.cheat.GhostMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.aurora.client.module.impl.combat.KillAuraModule", remap = false)
public abstract class AuroraKillAuraMixin {

    // Module.onTick() only runs while the module is enabled -> mirror that into our aura
    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ghost$replaceTick(CallbackInfo ci) {
        GhostMod.MODULES.killAura.enabled = true;
        ci.cancel();
    }

    @Inject(method = "onDisable", at = @At("HEAD"), remap = false, require = 0)
    private void ghost$off(CallbackInfo ci) {
        GhostMod.MODULES.killAura.enabled = false;   // rotation eases back to camera on its own
    }
}
