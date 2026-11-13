// language: Java, file: Speed.java
package com.cheat.module.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public class Speed {
    public boolean enabled = false;
    private static final float MULT = 2.6f;

    public void toggle() {
        enabled = !enabled;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null)
            mc.player.sendMessage(Text.literal("§7[Ghost] Speed " + (enabled ? "§aON" : "§cOFF")), true);
    }

    public void onTick(MinecraftClient mc) {
        if (mc.player == null) return;
        Vec3d v = mc.player.getVelocity();
        if (Math.abs(v.x) > 0.01 || Math.abs(v.z) > 0.01)
            mc.player.setVelocity(v.x * MULT, v.y, v.z * MULT);
    }
}
