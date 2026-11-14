// language: Java, file: EntityMixin.java
// MOVE FIX at the exact point where yaw turns into velocity: Entity.updateVelocity(speed, input).
// servers brute-force the 8 legal key combos with the yaw from our packet (aura yaw), so we pick the
// legal combo whose world direction (with aura yaw) is closest to where the camera wants to go, and run
// the velocity step with the aura yaw. require=0 + counters in the debug HUD show whether it applied.
package com.cheat.mixin;

import com.cheat.GhostMod;
import com.cheat.module.impl.KillAura;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Unique private float ghost$camYaw;
    @Unique private boolean ghost$swapped;

    @ModifyVariable(method = "updateVelocity", at = @At("HEAD"), argsOnly = true, require = 0)
    private Vec3d ghost$velIn(Vec3d in) {
        if (!((Object) this instanceof ClientPlayerEntity p)) return in;
        KillAura ka = GhostMod.MODULES.killAura;
        if (!ka.rotating) return in;
        ghost$camYaw = p.getYaw();
        ghost$swapped = true;
        KillAura.mfVel++;
        p.setYaw(ka.outYaw);
        double mag = Math.max(Math.abs(in.x), Math.abs(in.z));
        if (mag < 1.0E-4) return in;
        double ca = Math.toRadians(ghost$camYaw), aa = Math.toRadians(ka.outYaw);
        double wx = in.x * Math.cos(ca) - in.z * Math.sin(ca);
        double wz = in.z * Math.cos(ca) + in.x * Math.sin(ca);
        double wl = Math.hypot(wx, wz);
        if (wl < 1.0E-6) return in;
        wx /= wl; wz /= wl;
        double bestScore = -2, bx = in.x, bz = in.z;
        // sprinting needs a forward key on the server's side too: while sprinting only forward combos are legal
        int szMin = p.isSprinting() ? 1 : -1;
        for (int sx = -1; sx <= 1; sx++) for (int sz = szMin; sz <= 1; sz++) {
            if (sx == 0 && sz == 0) continue;
            double cx = sx * mag, cz = sz * mag;
            double ux = cx * Math.cos(aa) - cz * Math.sin(aa);
            double uz = cz * Math.cos(aa) + cx * Math.sin(aa);
            double score = (ux * wx + uz * wz) / Math.hypot(ux, uz);
            if (score > bestScore) { bestScore = score; bx = cx; bz = cz; }
        }
        return new Vec3d(bx, in.y, bz);
    }

    @Inject(method = "updateVelocity", at = @At("TAIL"), require = 0)
    private void ghost$velOut(float speed, Vec3d in, CallbackInfo ci) {
        if (!ghost$swapped) return;
        ghost$swapped = false;
        ((Entity) (Object) this).setYaw(ghost$camYaw);
    }
}
