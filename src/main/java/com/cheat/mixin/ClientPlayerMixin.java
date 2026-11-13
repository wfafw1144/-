// language: Java, file: ClientPlayerMixin.java
// swaps yaw/pitch for the duration of sendMovementPackets only -> server gets the aura rotation,
// camera and movement input this tick are untouched.
package com.cheat.mixin;

import com.cheat.GhostMod;
import com.cheat.module.impl.KillAura;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerMixin {

    @Unique private float ghost$yaw, ghost$pitch;
    @Unique private boolean ghost$swapped;

    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void ghost$head(CallbackInfo ci) {
        KillAura ka = GhostMod.MODULES.killAura;
        ClientPlayerEntity p = (ClientPlayerEntity) (Object) this;
        ghost$swapped = ka.rotating;
        if (!ghost$swapped) return;
        ghost$yaw = p.getYaw(); ghost$pitch = p.getPitch();
        p.setYaw(ka.outYaw); p.setPitch(ka.outPitch);
    }

    @Inject(method = "sendMovementPackets", at = @At("TAIL"))
    private void ghost$tail(CallbackInfo ci) {
        if (!ghost$swapped) return;
        ClientPlayerEntity p = (ClientPlayerEntity) (Object) this;
        GhostMod.MODULES.killAura.onRotationSent(p.getYaw(), p.getPitch());
        p.setYaw(ghost$yaw); p.setPitch(ghost$pitch);
        ghost$swapped = false;
    }
}
