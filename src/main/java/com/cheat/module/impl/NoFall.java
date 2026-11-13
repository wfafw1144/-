// language: Java, file: NoFall.java
package com.cheat.module.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class NoFall {
    public boolean enabled = false;

    public void toggle() {
        enabled = !enabled;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null)
            mc.player.sendMessage(Text.literal("§7[Ghost] NoFall " + (enabled ? "§aON" : "§cOFF")), true);
    }

    public void onTick(MinecraftClient mc) {
        if (mc.player == null) return;
        mc.player.fallDistance = 0f;
    }
}
