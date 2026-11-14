// language: Java, file: AuroraBridge.java
// Copies Aurora's KillAura GUI settings into GhostMod's KillAura every tick (reflection: no compile-time dependency on Aurora).
// If a field is missing (Aurora update renamed it) that setting just keeps its standalone default.
package com.cheat;

import com.cheat.module.impl.KillAura;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public final class AuroraBridge {
    private static final double MAX_REACH = 3.3;   // our aura is range-sensitive: Aurora slider goes to 6, anti-cheats don't
    private static final Map<String, Field> FIELDS = new HashMap<>();
    private static final Map<Class<?>, Method> GETTERS = new HashMap<>();
    private static Method shieldBreaker;
    private static boolean shieldTried;

    private AuroraBridge() {}

    private static Object val(Object mod, String name) {
        try {
            Field f = FIELDS.get(name);
            if (f == null) {
                for (Class<?> c = mod.getClass(); c != null && f == null; c = c.getSuperclass()) {
                    try { f = c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
                }
                if (f == null) return null;
                f.setAccessible(true);
                FIELDS.put(name, f);
            }
            Object setting = f.get(mod);
            if (setting == null) return null;
            Method g = GETTERS.get(setting.getClass());
            if (g == null) { g = setting.getClass().getMethod("get"); g.setAccessible(true); GETTERS.put(setting.getClass(), g); }
            return g.invoke(setting);
        } catch (Throwable t) { return null; }
    }

    private static boolean bool(Object mod, String name, boolean def) {
        Object o = val(mod, name);
        return o instanceof Boolean ? (Boolean) o : def;
    }

    private static double num(Object mod, String name, double def) {
        Object o = val(mod, name);
        return o instanceof Number ? ((Number) o).doubleValue() : def;
    }

    private static boolean shieldBreakerOn(Object mod) {
        try {
            if (!shieldTried) {
                shieldTried = true;
                for (Class<?> c = mod.getClass(); c != null && shieldBreaker == null; c = c.getSuperclass()) {
                    try { shieldBreaker = c.getDeclaredMethod("isShieldBreakerOn"); shieldBreaker.setAccessible(true); }
                    catch (NoSuchMethodException ignored) {}
                }
            }
            return shieldBreaker != null && (Boolean) shieldBreaker.invoke(mod);
        } catch (Throwable t) { return false; }
    }

    public static void sync(Object mod, KillAura k) {
        // targets: "Игроки" (players) / "Мобы" (mobs) / "Все" (all)
        Object t = val(mod, "targets");
        if (t != null) {
            String s = t.toString();
            boolean all = s.equals("Все");
            k.players = all || s.equals("Игроки");
            k.hostiles = all || s.equals("Мобы");   // "mobs on"; hostileOnly narrows it
        }
        double r = num(mod, "range", Double.NaN);
        if (!Double.isNaN(r)) {
            k.attackRange = Math.max(1.0, Math.min(r, MAX_REACH));
            k.aimRange = k.attackRange + 1.2;
        }
        k.maxTargets      = (int) Math.round(num(mod, "maxTargets", k.maxTargets));
        k.hostileOnly     = bool(mod, "hostileOnly", k.hostileOnly);
        k.waitCooldown    = bool(mod, "waitCooldown", k.waitCooldown);
        k.interval        = (int) Math.max(1, Math.round(num(mod, "interval", k.interval)));
        boolean sight     = bool(mod, "needSight", !k.throughWalls);
        k.throughWalls    = !sight || bool(mod, "throughWalls", false);
        k.whileBlocking   = bool(mod, "whileBlocking", k.whileBlocking);
        k.skipShields     = bool(mod, "skipShields", k.skipShields);
        k.shieldBreakerOn = k.skipShields && shieldBreakerOn(mod);
        k.swing           = bool(mod, "swing", k.swing);
        k.onlyJumping     = bool(mod, "onlyJumping", k.onlyJumping);
    }
}
