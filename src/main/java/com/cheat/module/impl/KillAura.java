// language: Java, file: KillAura.java, target: MC 1.21.11 Fabric (yarn)
// vanilla packet order: attack at START of tick with the rotation the server already has;
// new rotation is applied only inside sendMovementPackets (ClientPlayerMixin). camera never moves.
// rotation = momentum model (accel/decel, small overshoot) -> GCD-quantized to real sensitivity.
// hit only when the SENT rotation ray intersects the target box (no "perfect center" aimbot signature).
// single-target lock + reaction delay (anti multi-aura / instant-reaction heuristics).
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
    private static final float SPRINT_CONE = 45f, SAFE_CONE = 20f;

    // ── settings (editable from the menu) ────────────────────────
    public double  attackRange  = 3.0;  // eye->hitbox
    public double  aimRange     = 4.2;
    public float   turnSpeed    = 34f;   // max deg/tick
    public boolean players      = true;
    public boolean hostiles     = true;
    public boolean throughWalls = false;

    // ── read by ClientPlayerMixin ────────────────────────────────
    public volatile boolean rotating = false;
    public volatile float   outYaw, outPitch;

    private float sentYaw, sentPitch;     // last rotation the server received
    private boolean haveSent = false;
    private float velYaw, velPitch;       // rotation momentum

    private final Random rng = new Random();
    private LivingEntity target;
    private int   hold = 0;               // min ticks before switching target
    private int   reaction = 0;           // ticks to wait after acquiring
    private int   delay = 0;
    private float cdThreshold = 0.93f;
    private Vec3d aimOff = new Vec3d(0.5, 0.8, 0.5), aimGoal = aimOff;
    private int   retarget = 0;
    private int   pause = 0;
    private Vec3d prevCenter;                 // target center last tick -> velocity for lead
    private LivingEntity prevFor;
    private Vec3d prevVt = Vec3d.ZERO;
    private Vec3d prevSelf;

    public void toggle() {
        enabled = !enabled;
        if (enabled) reset(); else target = null;   // off: keep rotating until eased back to camera
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null)
            mc.player.sendMessage(Text.literal("§7[Ghost] KillAura " + (enabled ? "§aON" : "§cOFF")), true);
    }

    private int tickN, lastSetbackTick = -100, streak, lastAttackTick = -100, lastHurtTick = -100, safeTicks, attackPause;
    private int sbHurt, sbSprint, sbAtk, sbRot, b1, b2, b3;
    private double lastDPos; private float lastDRot; private int syncOnly;

    /** server corrected position/rotation. tiny = just a re-sync; real setback -> short pause, keep the target
     *  (old version wiped the target + forced a new reaction delay each time, which is what ate the fight). */
    public void onSetback(double dPos, float dRot) {
        lastDPos = dPos; lastDRot = dRot;
        if (dPos < 0.1) b1++; else if (dPos < 0.3) b2++; else b3++;
        // < 0.25 block and no turn = the server just nudging position (lag comp / sync), not a rubberband. don't react.
        if (dPos < 0.25 && dRot < 0.6f) { syncOnly++; return; }
        setbacks++;
        MinecraftClient m = MinecraftClient.getInstance();
        boolean hurt = m.player != null && (m.player.hurtTime > 0 || tickN - lastHurtTick < 15);
        if (hurt) sbHurt++;
        if (m.player != null && m.player.isSprinting()) sbSprint++;
        if (tickN - lastAttackTick <= 3) sbAtk++;
        if (m.player != null && rotating && Math.abs(MathHelper.wrapDegrees(outYaw - m.player.getYaw())) > 20f) sbRot++;
        if (hurt) safeTicks = 20;      // rubberbanded while taking a hit -> next 1.5 s stay close to the camera angle (vanilla-like)
        streak = (tickN - lastSetbackTick < 20) ? streak + 1 : 1;
        lastSetbackTick = tickN;
        if (dPos > 6.0) { rotating = false; haveSent = false; velYaw = velPitch = 0; target = null; delay = 0; pause = 2; return; }  // real teleport
        if (dRot < 0.6f) {
            // position-only setback: our rotation state is still valid. keep rotating (resetting it makes vanilla snap the
            // camera angle into a packet and restarts the turn = lost hits). only hold the swing for a moment.
            attackPause = streak >= 3 ? 6 : 1;
            return;
        }
        rotating = false; haveSent = false; velYaw = velPitch = 0;     // server changed our angle -> resync
        pause = streak >= 3 ? 8 : 1;
    }

    private void reset() {
        target = null; rotating = false; haveSent = false; delay = 0; velYaw = velPitch = 0;
    }

    /** START_CLIENT_TICK, before vanilla input handling */
    public void onStartTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) { reset(); return; }
        tickN++;
        if (mc.player != null && mc.player.hurtTime > 0) lastHurtTick = tickN;
        if (safeTicks > 0) safeTicks--;
        if (!haveSent) { sentYaw = mc.player.getYaw(); sentPitch = mc.player.getPitch(); haveSent = true; }
        if (pause > 0) { pause--; rotating = false; haveSent = false; return; }

        boolean idle = !enabled || mc.currentScreen != null || mc.player.isDead() || mc.player.isUsingItem();

        // ── attack with the rotation the server already has ──────
        if (!idle && target != null && target.isAlive()) {
            if (delay > 0 || reaction > 0 || attackPause > 0) cDelay++;
            else if (mc.player.getAttackCooldownProgress(0.5f) < cdThreshold) cCd++;
            else {
                int st = hitState(mc, target);
                if (st == 0) {
                    mc.interactionManager.attackEntity(mc.player, target);   // attack, then swing (vanilla order)
                    mc.player.swingHand(Hand.MAIN_HAND);
                    cdThreshold = 0.91f + rng.nextFloat() * 0.08f;
                    delay = rng.nextInt(8) == 0 ? 1 : 0;     // occasional hesitation
                    cAtk++; lastAttackTick = tickN;
                } else if (st == 1) cRay++; else if (st == 2) cRange++; else cWall++;
            }
        }
        if (delay > 0 && !(false)) delay--;
        if (attackPause > 0) attackPause--;
        if (reaction > 0) reaction--;
        if (hold > 0) hold--;

        // ── target selection: lock, switch only after hold expires or target invalid ──
        if (idle) target = null;
        else if (target == null || !valid(mc, target) || distBox(mc.player.getEyePos(), target) > aimRange
                || (hold <= 0 && better(mc) != null && better(mc) != target)) {
            LivingEntity old = target;
            target = pickTarget(mc);
            if (target != null && target != old) { hold = 10 + rng.nextInt(8); reaction = 1 + rng.nextInt(3); }
        }

        if (target == null) {
            prevFor = null; prevVt = Vec3d.ZERO; prevSelf = null;
            // ease back to the camera instead of letting vanilla snap it in one packet
            float cy = mc.player.getYaw(), cp = mc.player.getPitch();
            if (!rotating || Math.hypot(MathHelper.wrapDegrees(cy - sentYaw), cp - sentPitch) < 1.0) {
                rotating = false; velYaw = velPitch = 0; return;
            }
            stepToward(mc, cy, cp, false);
            return;
        }

        if (--retarget <= 0) {                                        // slowly drifting aim point inside the box
            aimGoal = new Vec3d(0.5 + (rng.nextDouble() - 0.5) * 0.36,
                                0.55 + rng.nextDouble() * 0.33,
                                0.5 + (rng.nextDouble() - 0.5) * 0.36);
            retarget = 6 + rng.nextInt(12);
        }
        aimOff = aimOff.add(aimGoal.subtract(aimOff).multiply(0.2));

        Box b = target.getBoundingBox();
        // LEAD: the attack lands at the start of NEXT tick, when both we and the target have moved.
        // aim where the aim point will be, from where our eye will be (this is why it missed while walking/jumping).
        Vec3d center = b.getCenter();
        Vec3d vt = (prevFor == target && prevCenter != null) ? center.subtract(prevCenter) : Vec3d.ZERO;
        if (vt.lengthSquared() > 1.0) vt = Vec3d.ZERO;                 // teleport / lag spike
        vt = vt.multiply(0.65).add(prevVt.multiply(0.35)); prevVt = vt;     // smoothed target velocity
        prevCenter = center; prevFor = target;
        // self lead from the REAL displacement of the last tick (includes knockback / friction), not from
        // getVelocity() which is wrong right after a hit. next tick moves ~ the same distance, slightly decayed.
        Vec3d sc = mc.player.getBoundingBox().getCenter();
        Vec3d selfD = (prevSelf != null) ? sc.subtract(prevSelf) : Vec3d.ZERO;
        prevSelf = sc;
        if (selfD.lengthSquared() > 0.64) selfD = Vec3d.ZERO;          // teleport / setback
        Vec3d vp = selfD.multiply(0.92);
        Vec3d eye = mc.player.getEyePos().add(vp.x, vp.y * 0.4, vp.z);
        Vec3d p = new Vec3d(
                MathHelper.lerp(aimOff.x, b.minX, b.maxX) + vt.x,
                MathHelper.lerp(aimOff.y, b.minY, b.maxY) + vt.y * 0.5,
                MathHelper.lerp(aimOff.z, b.minZ, b.maxZ) + vt.z);
        double dx = p.x - eye.x, dy = p.y - eye.y, dz = p.z - eye.z;
        float wantYaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float wantPitch = MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -90f, 90f);
        stepToward(mc, wantYaw, wantPitch, true);
    }

    // ── momentum rotation: accelerates, decelerates, tiny overshoot; then GCD snap ──
    private void stepToward(MinecraftClient mc, float wantYaw, float wantPitch, boolean jitter) {
        float dYaw   = MathHelper.wrapDegrees(wantYaw - sentYaw);
        float dPitch = wantPitch - sentPitch;
        float dist   = (float) Math.hypot(dYaw, dPitch);

        float gain = 0.80f + rng.nextFloat() * 0.15f;   // target point is already lead-predicted -> aim (almost) straight at it
        float tYaw = dYaw * gain, tPit = dPitch * gain;
        float mag = (float) Math.hypot(tYaw, tPit);
        float cap = turnSpeed * (0.8f + rng.nextFloat() * 0.2f);
        if (mag > cap) { tYaw *= cap / mag; tPit *= cap / mag; }

        velYaw   = velYaw   * 0.20f + tYaw * 0.80f;
        velPitch = velPitch * 0.20f + tPit * 0.80f;
        float aYaw = velYaw, aPit = velPitch;
        if (jitter && dist > 2.5f) { aYaw += (float) rng.nextGaussian() * 0.32f; aPit += (float) rng.nextGaussian() * 0.18f; }

        // never fly far past the goal
        float lim = dist * 1.06f + 0.3f;
        float am = (float) Math.hypot(aYaw, aPit);
        if (am > lim) { aYaw *= lim / am; aPit *= lim / am; }

        // GCD: deltas a real mouse at this sensitivity can produce
        double sens = mc.options.getMouseSensitivity().getValue();
        double f = sens * 0.6 + 0.2;
        float gcd = (float) (f * f * f * 8.0 * 0.15);
        // sprinting: aura yaw may only deviate from the camera by 45 deg (forward-only inputs must still be able to
        // steer toward the camera, otherwise running + turning walks you off / gets setback)
        float cone = safeTicks > 0 ? SAFE_CONE : (mc.player.isSprinting() ? SPRINT_CONE : 180f);
        if (cone < 180f) {
            float off = MathHelper.wrapDegrees(sentYaw + aYaw - mc.player.getYaw());
            float cl = MathHelper.clamp(off, -cone, cone);
            aYaw += cl - off;
        }
        aYaw = Math.round(aYaw / gcd) * gcd;
        aPit = Math.round(aPit / gcd) * gcd;

        outYaw   = sentYaw + aYaw;
        outPitch = MathHelper.clamp(sentPitch + aPit, -90f, 90f);
        rotating = true;
    }

    /** mixin: right after the movement packet went out */
    public void onRotationSent(float yaw, float pitch) { sentYaw = yaw; sentPitch = pitch; }

    // ── last-sent rotation ray must hit the box, in reach, with LOS ──
    /** 0 = can hit, 1 = ray misses box, 2 = out of reach, 3 = wall in the way */
    private int hitState(MinecraftClient mc, LivingEntity t) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d end = eye.add(look(sentYaw, sentPitch).multiply(attackRange + 0.5));
        Optional<Vec3d> hit = t.getBoundingBox().expand(0.08).raycast(eye, end);   // small tolerance (servers expand ~0.03 too)
        if (hit.isEmpty()) return distBox(eye, t) > attackRange ? 2 : 1;
        if (eye.distanceTo(hit.get()) > attackRange) return 2;
        if (!throughWalls) {
            HitResult r = mc.world.raycast(new RaycastContext(eye, hit.get(),
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (r.getType() != HitResult.Type.MISS) return 3;
        }
        return 0;
    }

    // ── debug HUD: why the aura did / didn't swing (counters since last reset) ──
    public boolean debug = false;
    private int cAtk, cRay, cRange, cWall, cCd, cDelay;
    public void resetDebug() { cAtk = cRay = cRange = cWall = cCd = cDelay = 0; }
    public void renderDebug(net.minecraft.client.gui.DrawContext c) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!debug || mc.player == null) return;
        int tot = Math.max(1, cAtk + cRay + cRange + cWall + cCd + cDelay);
        String[] l = {
            "KA ticks with target: " + tot,
            "hit      " + cAtk   + "  " + 100 * cAtk   / tot + "%",
            "ray miss " + cRay   + "  " + 100 * cRay   / tot + "%",
            "range    " + cRange + "  " + 100 * cRange / tot + "%",
            "wall     " + cWall  + "  " + 100 * cWall  / tot + "%",
            "cooldown " + cCd    + "  " + 100 * cCd    / tot + "%",
            "delay    " + cDelay + "  " + 100 * cDelay / tot + "%",
            "setbacks: " + setbacks + "  (sync only: " + syncOnly + ")",
            String.format("last fix: pos %.2f  rot %.1f", lastDPos, lastDRot),
            "corrections <0.1 / <0.3 / >=0.3 blocks: " + b1 + " / " + b2 + " / " + b3,
            "setbacks while hurt/sprint/atk/rot: " + sbHurt + "/" + sbSprint + "/" + sbAtk + "/" + sbRot + (safeTicks > 0 ? "  [SAFE]" : ""),
            "movefix vel/jump: " + mfVel + " / " + mfJump + (rotating ? "  [rotating]" : "") };
        for (int i = 0; i < l.length; i++) c.drawText(mc.textRenderer, l[i], 6, 6 + i * 10, 0xFFFFFF55, true);
    }
    private int setbacks = 0;
    public static int mfVel, mfJump;   // how many times the move-fix hooks actually ran (0 while walking = hook not applied)
    private static Vec3d look(float yaw, float pitch) {
        float fp = pitch * 0.017453292f, fy = -yaw * 0.017453292f;
        float cy = MathHelper.cos(fy), sy = MathHelper.sin(fy), cp = MathHelper.cos(fp), sp = MathHelper.sin(fp);
        return new Vec3d(sy * cp, -sp, cy * cp);
    }

    private LivingEntity better(MinecraftClient mc) {
        Vec3d eye = mc.player.getEyePos();
        LivingEntity c = pickTarget(mc);
        // only worth switching if clearly closer (avoids ping-pong between two targets)
        if (c != null && target != null && distBox(eye, c) + 0.6 > distBox(eye, target)) return target;
        return c;
    }

    private LivingEntity pickTarget(MinecraftClient mc) {
        Vec3d eye = mc.player.getEyePos();
        return mc.world.getEntitiesByClass(LivingEntity.class,
                        mc.player.getBoundingBox().expand(aimRange + 1), e -> valid(mc, e)).stream()
                .filter(e -> distBox(eye, e) <= aimRange)
                .min(Comparator.comparingDouble(e -> distBox(eye, e)))
                .orElse(null);
    }

    private boolean valid(MinecraftClient mc, LivingEntity e) {
        if (e == mc.player || !e.isAlive()) return false;
        if (e instanceof PlayerEntity) return players && !((PlayerEntity) e).isSpectator();
        if (e instanceof HostileEntity) return hostiles;
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
