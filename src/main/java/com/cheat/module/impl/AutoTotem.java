// language: Java, file: AutoTotem.java, target: MC 1.21.11 Fabric (yarn)
// Replaces Aurora's AutoTotem tick. Aurora fired an inventory window click (SWAP -> offhand) the same tick HP dropped,
// with no window ever opened = textbook AutoTotem signature (Grim/Vulcan/custom inventory checks).
// Here: human reaction delay, and a totem in the HOTBAR goes through the exact packet sequence of scroll + F:
//   UpdateSelectedSlot(totem) -> PlayerAction SWAP_ITEM_WITH_OFFHAND -> (next tick) UpdateSelectedSlot(original)
// No inventory window is touched. Only a totem buried in the main inventory still needs a window click (fallback).
// threshold / pause / notify are read from Aurora's own AutoTotem settings (reflection).
package com.cheat.module.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

public class AutoTotem {
    private final Random rng = new Random();
    private int cooldown = 0, reaction = -1, restoreSlot = -1;

    private static Object setting(Object mod, String name) {
        try {
            for (Class<?> c = mod.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField(name); f.setAccessible(true);
                    Object s = f.get(mod); Method g = s.getClass().getMethod("get"); g.setAccessible(true);
                    return g.invoke(s);
                } catch (NoSuchFieldException ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }
    private static double num(Object mod, String n, double d) { Object o = setting(mod, n); return o instanceof Number ? ((Number) o).doubleValue() : d; }
    private static boolean bool(Object mod, String n, boolean d) { Object o = setting(mod, n); return o instanceof Boolean ? (Boolean) o : d; }

    public void tick(MinecraftClient mc, Object mod) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;
        if (restoreSlot >= 0) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(restoreSlot));
            restoreSlot = -1;
            return;
        }
        if (cooldown > 0) { cooldown--; return; }
        if (mc.currentScreen != null || p.isDead()) return;

        double thr = num(mod, "threshold", 10);
        if (p.getHealth() + p.getAbsorptionAmount() > thr || p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) { reaction = -1; return; }
        if (reaction < 0) reaction = 1 + rng.nextInt(2);     // 1-2 ticks "reaction"
        if (reaction-- > 0) return;
        reaction = -1;

        PlayerInventory inv = p.getInventory();
        int hot = -1, main = -1;
        for (int i = 0; i < 9 && hot < 0; i++) if (inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) hot = i;
        for (int i = 9; i < 36 && main < 0; i++) if (inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) main = i;

        if (hot >= 0) {
            int sel = inv.getSelectedSlot();
            if (hot != sel) {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(hot));
                restoreSlot = sel;
            }
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        } else if (main >= 0) {
            mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, main, 40, SlotActionType.SWAP, p);   // fallback, window click
        } else return;

        cooldown = (int) Math.max(4, num(mod, "pause", 6)) + rng.nextInt(3);
        if (bool(mod, "notify", true)) p.sendMessage(Text.literal("Auto Totem: totem to offhand"), true);
    }
}
