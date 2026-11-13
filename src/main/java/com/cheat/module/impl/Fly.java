// language: Java, file: Fly.java
package com.cheat.module.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.text.Text;

public class Fly {
    public boolean enabled = false;

    public void toggle(MinecraftClient mc) {
        enabled = !enabled;
        if (!enabled && mc.player != null) {
            PlayerAbilities ab = mc.player.getAbilities();
            ab.flying    = false;
            ab.allowFlying = false;
            mc.player.sendAbilitiesUpdate();
        }
        msg(mc, "Fly " + (enabled ? "§aON" : "§cOFF"));
    }

    public void onTick(MinecraftClient mc) {
        if (mc.player == null) return;
        PlayerAbilities ab = mc.player.getAbilities();
        ab.allowFlying = true;
        ab.flying      = true;
        mc.player.sendAbilitiesUpdate();

        double vy = 0;
        if (mc.options.jumpKey.isPressed())  vy =  0.12;
        if (mc.options.sneakKey.isPressed()) vy = -0.12;
        mc.player.setVelocity(mc.player.getVelocity().x, vy, mc.player.getVelocity().z);
    }

    private void msg(MinecraftClient mc, String s) {
        if (mc.player != null)
            mc.player.sendMessage(Text.literal("§7[Ghost] " + s), true);
    }
}
