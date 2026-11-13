// language: Java, file: ClientPlayNetworkHandlerMixin.java
// server position/rotation correction (setback) -> tell KillAura to back off. require=0: never crashes if the name moves.
package com.cheat.mixin;

import com.cheat.GhostMod;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onPlayerPositionLook", at = @At("TAIL"), require = 0)
    private void ghost$setback(CallbackInfo ci) {
        GhostMod.MODULES.killAura.onSetback();
    }
}
