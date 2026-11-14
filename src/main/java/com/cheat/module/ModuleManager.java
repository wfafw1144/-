// language: Java, file: ModuleManager.java
// only KillAura remains. no keybind for it (Aurora's own toggle/bind drives it). RShift opens the settings menu.
package com.cheat.module;

import com.cheat.gui.GhostScreen;
import com.cheat.module.impl.KillAura;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class ModuleManager {

    public final KillAura killAura = new KillAura();

    // 1.21.9+: KeyBinding takes a Category object, not a string
    private static final KeyBinding.Category CAT = KeyBinding.Category.create(Identifier.of("ghostmod", "main"));
    private KeyBinding keyMenu;

    public void init() {
        keyMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ghostmod.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CAT));
    }

    public void onStartTick(MinecraftClient mc) {
        if (killAura.enabled || killAura.rotating) killAura.onStartTick(mc);
    }

    public void onTick(MinecraftClient mc) {
        while (keyMenu.wasPressed()) if (mc.currentScreen == null) mc.setScreen(new GhostScreen(this));
    }
}
