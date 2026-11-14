// language: Java, file: ModuleManager.java
// KillAura only. keys are polled straight from GLFW, NOT registered as KeyBindings -> they never show up in
// Options > Controls. R toggles KillAura (through Aurora's own module when Aurora is present, so its state stays
// in sync), Right Shift opens the settings menu. Only reacts while no screen (chat/inventory/menu) is open.
package com.cheat.module;

import com.cheat.gui.GhostScreen;
import com.cheat.module.impl.KillAura;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public class ModuleManager {

    public final KillAura killAura = new KillAura();

    private boolean prevAura, prevMenu;

    public void init() {}

    public void onStartTick(MinecraftClient mc) {
        if (killAura.enabled || killAura.rotating) killAura.onStartTick(mc);
    }

    public void onTick(MinecraftClient mc) {
        long w = mc.getWindow().getHandle();
        boolean aura = GLFW.glfwGetKey(w, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
        boolean menu = GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        if (mc.currentScreen == null) {
            if (aura && !prevAura) toggleAura();
            if (menu && !prevMenu) mc.setScreen(new GhostScreen(this));
        }
        prevAura = aura; prevMenu = menu;   // updated every tick so closing a screen with the key held doesn't re-trigger
    }

    private void toggleAura() {
        try {
            Class<?> c = Class.forName("com.aurora.client.module.impl.combat.KillAuraModule");
            Object m = c.getMethod("getInstance").invoke(null);
            if (m != null) { c.getMethod("toggle").invoke(m); return; }
        } catch (Throwable ignored) {}
        killAura.toggle();   // Aurora not installed -> standalone toggle
    }
}
