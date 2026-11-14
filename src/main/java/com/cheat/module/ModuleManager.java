// language: Java, file: ModuleManager.java
// KillAura only. no keybinds, no menu: Aurora Client toggles it, settings are the defaults in KillAura.java.
package com.cheat.module;

import com.cheat.module.impl.KillAura;
import net.minecraft.client.MinecraftClient;

public class ModuleManager {

    public final KillAura killAura = new KillAura();

    public void init() {}

    public void onStartTick(MinecraftClient mc) {
        if (killAura.enabled || killAura.rotating) killAura.onStartTick(mc);
    }

    public void onTick(MinecraftClient mc) {}
}
