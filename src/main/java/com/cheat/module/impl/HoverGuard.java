// language: Java, file: HoverGuard.java
// Aurora's Hover (zero vertical velocity + noGravity) is exactly what Fly/hover checks look for: Y stays constant while
// airborne. nothing here can make that look legal. what this does is stop the damage: the module switches itself off on
// the first server setback (no flag loop) and after MAX_TICKS (long hangs are what get logged and punished).
package com.cheat.module.impl;

public final class HoverGuard {
    public static final int MAX_TICKS = 30;           // 1.5 s per activation
    public static volatile boolean setback = false;   // set by KillAura.onSetback on a real (>=0.25 block) correction
    private HoverGuard() {}
}
