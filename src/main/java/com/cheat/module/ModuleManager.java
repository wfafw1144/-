// language: Java, file: ModuleManager.java
// бинды: R=KillAura  F=Fly  G=Speed  H=NoFall
package com.cheat.module;

import com.cheat.module.impl.Fly;
import com.cheat.module.impl.KillAura;
import com.cheat.module.impl.NoFall;
import com.cheat.module.impl.Speed;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import com.cheat.gui.GhostScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class ModuleManager {

    public final KillAura killAura = new KillAura();
    public final Fly       fly     = new Fly();
    public final Speed     speed   = new Speed();
    public final NoFall    noFall  = new NoFall();

    // 1.21.9+: KeyBinding takes a Category object, not a string
    private static final KeyBinding.Category CAT = KeyBinding.Category.create(Identifier.of("ghostmod", "main"));

    private KeyBinding keyKillAura;
    private KeyBinding keyFly;
    private KeyBinding keySpeed;
    private KeyBinding keyNoFall;
    private KeyBinding keyMenu;

    public void init() {
        keyKillAura = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ghostmod.killaura", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, CAT));
        keyFly = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ghostmod.fly",      InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F, CAT));
        keySpeed = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ghostmod.speed",    InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, CAT));
        keyNoFall = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ghostmod.nofall",   InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, CAT));
        keyMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ghostmod.menu",     InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CAT));
    }

    public void onStartTick(MinecraftClient mc) {
        if (killAura.enabled || killAura.rotating) killAura.onStartTick(mc);
    }

    public void onTick(MinecraftClient mc) {
        // обработка нажатий
        while (keyKillAura.wasPressed()) killAura.toggle();
        while (keyFly.wasPressed())      fly.toggle(mc);
        while (keySpeed.wasPressed())    speed.toggle();
        while (keyNoFall.wasPressed())   noFall.toggle();
        while (keyMenu.wasPressed())     if (mc.currentScreen == null) mc.setScreen(new GhostScreen(this));

        // тики модулей
        if (fly.enabled)      fly.onTick(mc);
        if (speed.enabled)    speed.onTick(mc);
        if (noFall.enabled)   noFall.onTick(mc);
    }
}
