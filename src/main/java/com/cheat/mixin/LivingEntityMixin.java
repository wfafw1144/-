// language: Java, file: LivingEntityMixin.java
// MOVE FIX. servers simulate our movement with the yaw from the movement packet. with silent aura
// rotation the client walked with the camera yaw while the server assumed the aura yaw -> position
// mismatch -> setback ("pulled back for a second"). fix: run travel()/jump() with the SAME yaw that
// goes into this tick's packet, and rotate the input vector by (camera - aura) so we still walk where
// the camera points. require=0: if a name moves, the fix silently disables instead of crashing.
package com.cheat.mixin;

import com.cheat.GhostMod;
import com.cheat.module.impl.KillAura;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Unique private float ghost$camYaw;
    @Unique private boolean ghost$swapped;

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true, require = 0)
    private Vec3d ghost$travelIn(Vec3d in) {
        if (!((Object) this instanceof ClientPlayerEntity p)) return in;
        KillAura ka = GhostMod.MODULES.killAura;
        if (!ka.rotating) return in;
        ghost$camYaw = p.getYaw();
        ghost$swapped = true;
        p.setYaw(ka.outYaw);
        // rotate input by (camera - aura) so world direction stays = camera direction
        double d = Math.toRadians(ghost$camYaw - ka.outYaw);
        double c = Math.cos(d), s = Math.sin(d);
        return new Vec3d(in.x * c - in.z * s, in.y, in.z * c + in.x * s);
    }

    @Inject(method = "travel", at = @At("TAIL"), require = 0)
    private void ghost$travelOut(Vec3d in, CallbackInfo ci) {
        if (!ghost$swapped) return;
        ghost$swapped = false;
        ((LivingEntity) (Object) this).setYaw(ghost$camYaw);
    }

    // sprint-jump boost is applied along yaw -> must match what the server will assume
    @Inject(method = "jump", at = @At("HEAD"), require = 0)
    private void ghost$jumpIn(CallbackInfo ci) {
        if (!((Object) this instanceof ClientPlayerEntity p)) return;
        KillAura ka = GhostMod.MODULES.killAura;
        if (!ka.rotating || ghost$swapped) return;
        ghost$camYaw = p.getYaw();
        ghost$swapped = true;
        p.setYaw(ka.outYaw);
    }

    @Inject(method = "jump", at = @At("TAIL"), require = 0)
    private void ghost$jumpOut(CallbackInfo ci) {
        if (!ghost$swapped) return;
        ghost$swapped = false;
        ((LivingEntity) (Object) this).setYaw(ghost$camYaw);
    }
}
