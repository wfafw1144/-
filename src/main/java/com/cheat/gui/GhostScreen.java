// language: Java, file: GhostScreen.java — immediate-mode menu, opened with Right Shift.
// input is polled from GLFW (stable across 1.21.x input-API changes); drawing via DrawContext.fill/drawText.
package com.cheat.gui;

import com.cheat.module.ModuleManager;
import com.cheat.module.impl.KillAura;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class GhostScreen extends Screen {

    private final ModuleManager mm;
    private boolean prevDown, prevShift;
    private int dragging = -1;     // slider id being dragged

    private static final int X = 30, Y = 30, W = 190, ROW = 17;

    public GhostScreen(ModuleManager mm) {
        super(Text.literal("GhostMod"));
        this.mm = mm;
    }

    @Override public boolean shouldPause() { return false; }

    private long win() { return MinecraftClient.getInstance().getWindow().getHandle(); }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        // no super.render: it would draw the blurred background a second time and crash on 1.21.x
        long w = win();
        boolean down  = GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean shift = GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean click = down && !prevDown;
        if (shift && !prevShift && prevShiftSeenUp) { close(); return; }
        if (!shift) prevShiftSeenUp = true;
        prevShift = shift;
        if (!down) dragging = -1;

        KillAura ka = mm.killAura;
        int y = Y;
        c.fill(X - 4, y - 4, X + W + 4, y + ROW * 13 + 8, 0xD0101018);
        c.fill(X - 4, y - 4, X + W + 4, y + ROW, 0xFF6A3DE8);
        text(c, "GhostMod", X + 4, y + 4, 0xFFFFFFFF);
        y += ROW + 4;


        y += 6;
        text(c, "KillAura settings", X + 4, y + 2, 0xFFAAAAFF); y += ROW;
        y = toggle(c, mx, my, click, "Players",       ka.players,      y, () -> ka.players = !ka.players);
        y = toggle(c, mx, my, click, "Hostiles",      ka.hostiles,     y, () -> ka.hostiles = !ka.hostiles);
        y = toggle(c, mx, my, click, "Through walls", ka.throughWalls, y, () -> ka.throughWalls = !ka.throughWalls);
        y = toggle(c, mx, my, click, "Knockback guard", ka.kbGuard, y, () -> ka.kbGuard = !ka.kbGuard);
        y = toggle(c, mx, my, click, "Debug HUD", ka.debug, y, () -> { ka.debug = !ka.debug; ka.resetDebug(); });
        ka.attackRange = slider(c, mx, my, click, down, 0, "Reach", ka.attackRange, 2.0, 3.3, y); y += ROW;
        ka.turnSpeed   = (float) slider(c, mx, my, click, down, 1, "Turn speed", ka.turnSpeed, 8, 60, y); y += ROW;
        ka.moveCone    = (float) slider(c, mx, my, click, down, 2, "Moving cone (deg)", ka.moveCone, 5, 180, y); y += ROW;
        ka.airCone     = (float) slider(c, mx, my, click, down, 3, "Air cone (deg)", ka.airCone, 5, 180, y); y += ROW;

        prevDown = down;
    }

    private boolean prevShiftSeenUp = false;

    private int toggle(DrawContext c, int mx, int my, boolean click, String name, boolean on, int y, Runnable r) {
        boolean hover = mx >= X && mx <= X + W && my >= y && my < y + ROW - 1;
        c.fill(X, y, X + W, y + ROW - 1, hover ? 0xFF26263A : 0xFF1B1B28);
        c.fill(X + W - 14, y + 3, X + W - 4, y + ROW - 4, on ? 0xFF3DDC84 : 0xFF555566);
        text(c, name, X + 4, y + 4, on ? 0xFFFFFFFF : 0xFFAAAAAA);
        if (hover && click) r.run();
        return y + ROW;
    }

    private double slider(DrawContext c, int mx, int my, boolean click, boolean down, int id,
                          String name, double v, double min, double max, int y) {
        boolean hover = mx >= X && mx <= X + W && my >= y && my < y + ROW - 1;
        if (hover && click) dragging = id;
        if (dragging == id && down) v = min + (max - min) * Math.max(0, Math.min(1, (mx - X) / (double) W));
        c.fill(X, y, X + W, y + ROW - 1, 0xFF1B1B28);
        int fillW = (int) (W * (v - min) / (max - min));
        c.fill(X, y, X + fillW, y + ROW - 1, 0xFF4B2FB0);
        text(c, name + ": " + String.format("%.2f", v), X + 4, y + 4, 0xFFFFFFFF);
        return v;
    }

    private void text(DrawContext c, String s, int x, int y, int color) {
        c.drawText(textRenderer, s, x, y, color, false);
    }
}
