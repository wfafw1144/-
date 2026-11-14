// language: Java, file: ClientPlayNetworkHandlerMixin.java
// server position/rotation correction. measures how far the server actually moved/turned us:
// ~0 = harmless re-sync, big = real setback. require=0: never crashes if the name moves.
package com.cheat.mixin;

import com.cheat.GhostMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Unique private Vec3d ghost$pos;
    @Unique private float ghost$yaw, ghost$pitch;

    @Inject(method = "onPlayerPositionLook", at = @At("HEAD"), require = 0)
    private void ghost$before(CallbackInfo ci) {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) { ghost$pos = null; return; }
        ghost$pos = p.getBoundingBox().getCenter();
        ghost$yaw = p.getYaw(); ghost$pitch = p.getPitch();
    }

    @Inject(method = "onPlayerPositionLook", at = @At("TAIL"), require = 0)
    private void ghost$after(CallbackInfo ci) {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null || ghost$pos == null) return;
        double dPos = p.getBoundingBox().getCenter().distanceTo(ghost$pos);
        float dRot = (float) Math.hypot(MathHelper.wrapDegrees(p.getYaw() - ghost$yaw), p.getPitch() - ghost$pitch);
        GhostMod.MODULES.killAura.onSetback(dPos, dRot);
    }
}
