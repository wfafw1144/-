// language: Java, file: KillAura.java, target: MC 1.21.1 Fabric (yarn 1.21.1+build.3)
// design: vanilla packet order. attack runs at START of tick using the rotation the server
// already got last tick; the new rotation is applied only inside sendMovementPackets (mixin).
// camera never moves. rotations are smoothed, GCD-quantized to the player's sensitivity,
// attack only fires when the *sent* rotation ray actually hits the target box.
package com.cheat.module.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Comparator;
import java.util.Optional;
import java.util.Random;

public class KillAura {

    public boolean enabled = false;

    // ── settings ─────────────────────────────────────────────────
    private static final double ATTACK_RANGE  = 2.85;  // eye->hitbox; margin for 100+ ms target ping (lagcomp'd reach checks)
    private static final double AIM_RANGE     = 4.2;   // start turning before in range
    private static final boolean PLAYERS      = true;
    private static final boolean HOSTILES     = true;
    private static final boolean THROUGH_WALLS = false;
    private static final float  MAX_TURN      = 38f;   // deg/tick cap
    private static final float  MIN_TURN      = 3f;

    // ── state read by ClientPlayerMixin ──────────────────────────
    public volatile boolean rotating = false;
    public volatile float   outYaw, outPitch;

    // last rotation the server actually received
    private float sentYaw, sentPitch;
    private boolean haveSent = false;

    private final Random rng = new Random();
    private LivingEntity target;
    private int   delay = 0;
    private float cdThreshold = 0.93f;
    private Vec3d aimOff = new Vec3d(0, 0.85, 0);    // fraction offsets inside box, low-passed
    private Vec3d aimGoal = aimOff;
    private int   retarget = 0;
    private int   pause = 0;

    public void toggle() {
        enabled = !enabled;
        if (enabled) reset(); else target = null;   // off: keep rotating=true until rotation is eased back to camera
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null)
            mc.player.sendMessage(Text.literal("§7[Ghost] KillAura " + (enabled ? "§aON" : "§cOFF")), true);
    }

    /** server teleported/rubberbanded us: client already took the server rotation, so drop everything and sit out a bit */
    public void onSetback() {
        rotating = false; haveSent = false; target = null; pause = 12; delay = 0;
    }

    private void reset() {
        target = null; rotating = false; haveSent = false; delay = 0;
    }

    /** called from START_CLIENT_TICK, before vanilla input handling */
    public void onStartTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) { reset(); return; }
        if (!haveSent) { sentYaw = mc.player.getYaw(); sentPitch = mc.player.getPitch(); haveSent = true; }
        if (pause > 0) { pause--; rotating = false; haveSent = false; return; }

        if (mc.currentScreen != null || mc.player.isDead() || mc.player.isUsingItem()) { target = null; }

        // ── attack with rotation the server already has ──────────
        if (enabled && target != null && target.isAlive() && delay <= 0 && canHitWithSent(mc, target)
                && mc.player.getAttackCooldownProgress(0.5f) >= cdThreshold) {
            mc.interactionManager.attackEntity(mc.player, target);   // packet order: attack, then swing (vanilla)
            mc.player.swingHand(Hand.MAIN_HAND);
            cdThreshold = 0.92f + rng.nextFloat() * 0.07f;
            delay = rng.nextInt(4) == 0 ? 1 : 0;                      // occasional 1-tick hesitation
        } else if (delay > 0) delay--;

        // ── pick target + compute rotation for THIS tick's movement packet ──
        target = (enabled && mc.currentScreen == null && !mc.player.isDead() && !mc.player.isUsingItem()) ? pickTarget(mc) : null;
        if (target == null) {
            // no target: ease the rotation back to the camera instead of letting vanilla snap it in one packet
            float cy = mc.player.getYaw(), cp = mc.player.getPitch();
            if (!rotating || Math.hypot(MathHelper.wrapDegrees(cy - sentYaw), cp - sentPitch) < 1.0) { rotating = false; return; }
            stepToward(mc, cy, cp, false);
            return;
        }

        if (--retarget <= 0) {                                        // slow drifting aim point
            aimGoal = new Vec3d(0.5 + (rng.nextDouble() - 0.5) * 0.6,
                                0.55 + rng.nextDouble() * 0.35,
                                0.5 + (rng.nextDouble() - 0.5) * 0.6);
            retarget = 6 + rng.nextInt(10);
        }
        aimOff = aimOff.add(aimGoal.subtract(aimOff).multiply(0.25));

        Box b = target.getBoundingBox();
        Vec3d eye = mc.player.getEyePos();
        Vec3d p = new Vec3d(
                MathHelper.lerp(aimOff.x, b.minX, b.maxX),
                MathHelper.lerp(aimOff.y, b.minY, b.maxY),
                MathHelper.lerp(aimOff.z, b.minZ, b.maxZ));
        // if the eye is inside the aim column anyway, closest-point keeps pitch sane at point blank
        double dx = p.x - eye.x, dy = p.y - eye.y, dz = p.z - eye.z;
        float wantYaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float wantPitch = MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -90f, 90f);

        stepToward(mc, wantYaw, wantPitch, true);
    }

    private void stepToward(MinecraftClient mc, float wantYaw, float wantPitch, boolean jitter) {
        float dYaw   = MathHelper.wrapDegrees(wantYaw - sentYaw);
        float dPitch = wantPitch - sentPitch;
        float dist   = (float) Math.hypot(dYaw, dPitch);

        // ease: big turns are fast, final approach slows (no constant-speed snap)
        float step = MathHelper.clamp(dist * (0.45f + rng.nextFloat() * 0.2f), MIN_TURN, MAX_TURN);
        if (dist <= step) step = dist;
        float k = dist < 1e-4f ? 0f : step / dist;
        dYaw   *= k;
        dPitch *= k;
        // small independent jitter, only while still turning
        if (jitter && dist > 2f) { dYaw += (float) rng.nextGaussian() * 0.35f; dPitch += (float) rng.nextGaussian() * 0.2f; }

        // GCD: snap delta to what a real mouse at this sensitivity can produce
        double sens = mc.options.getMouseSensitivity().getValue();
        double f = sens * 0.6 + 0.2;
        float gcd = (float) (f * f * f * 8.0 * 0.15);
        dYaw   = Math.round(dYaw   / gcd) * gcd;
        dPitch = Math.round(dPitch / gcd) * gcd;

        outYaw   = sentYaw + dYaw;
        outPitch = MathHelper.clamp(sentPitch + dPitch, -90f, 90f);
        rotating = true;
    }

    /** mixin calls this right after the movement packet went out */
    public void onRotationSent(float yaw, float pitch) { sentYaw = yaw; sentPitch = pitch; }

    // ── does the last-sent rotation ray hit the target box, in reach, with LOS ──
    private boolean canHitWithSent(MinecraftClient mc, LivingEntity t) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = look(sentYaw, sentPitch);
        Vec3d end = eye.add(look.multiply(ATTACK_RANGE + 0.5));
        Optional<Vec3d> hit = t.getBoundingBox().raycast(eye, end);
        if (hit.isEmpty()) return false;
        if (eye.distanceTo(hit.get()) > ATTACK_RANGE) return false;
        if (!THROUGH_WALLS) {
            HitResult r = mc.world.raycast(new RaycastContext(eye, hit.get(),
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (r.getType() != HitResult.Type.MISS) return false;
        }
        return true;
    }

    private static Vec3d look(float yaw, float pitch) {
        float fp = pitch * 0.017453292f, fy = -yaw * 0.017453292f;
        float cy = MathHelper.cos(fy), sy = MathHelper.sin(fy), cp = MathHelper.cos(fp), sp = MathHelper.sin(fp);
        return new Vec3d(sy * cp, -sp, cy * cp);
    }

    // ── sticky nearest target ────────────────────────────────────
    private LivingEntity pickTarget(MinecraftClient mc) {
        Vec3d eye = mc.player.getEyePos();
        if (target != null && valid(mc, target) && distBox(eye, target) <= AIM_RANGE) return target;
        return mc.world.getEntitiesByClass(LivingEntity.class,
                        mc.player.getBoundingBox().expand(AIM_RANGE + 1), e -> valid(mc, e)).stream()
                .filter(e -> distBox(eye, e) <= AIM_RANGE)
                .min(Comparator.comparingDouble(e -> distBox(eye, e)))
                .orElse(null);
    }

    private boolean valid(MinecraftClient mc, LivingEntity e) {
        if (e == mc.player || !e.isAlive()) return false;
        if (e instanceof PlayerEntity) return PLAYERS && !((PlayerEntity) e).isSpectator();
        if (e instanceof HostileEntity) return HOSTILES;
        return false;
    }

    private static double distBox(Vec3d eye, Entity e) {
        Box b = e.getBoundingBox();
        double x = MathHelper.clamp(eye.x, b.minX, b.maxX) - eye.x;
        double y = MathHelper.clamp(eye.y, b.minY, b.maxY) - eye.y;
        double z = MathHelper.clamp(eye.z, b.minZ, b.maxZ) - eye.z;
        return Math.sqrt(x * x + y * y + z * z);
    }
}
