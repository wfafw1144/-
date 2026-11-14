// language: Java, file: GhostMod.java, target: MC 1.21.1 Fabric client
package com.cheat;

import com.cheat.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class GhostMod implements ClientModInitializer {

    public static final ModuleManager MODULES = new ModuleManager();

    @Override
    public void onInitializeClient() {
        MODULES.init();
        HudRenderCallback.EVENT.register((ctx, tick) -> { MODULES.staff.render(ctx); MODULES.killAura.renderDebug(ctx); });
        ClientTickEvents.START_CLIENT_TICK.register(mc -> MODULES.onStartTick(mc));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;
            MODULES.onTick(mc);
        });
    }
}
