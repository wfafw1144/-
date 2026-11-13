// language: Java, file: StaffList.java, target: MC 1.21.11 Fabric
// client-side staff detector (reads tab list / scoreboard teams / world entities, sends nothing):
//  1. rank keyword in tab-prefix/suffix (RU+EN defaults, extra words from config/ghostmod-staff.txt)
//  2. SPECTATOR gamemode in tab list  (vanish via spectator)
//  3. player entity in world that is NOT in tab list  (packet-level vanish)  -> marked "?"
//  4. staff that dropped out of the tab list without a quit -> "пропал из таба", kept 90 s
//  "watching": spectator sitting on top of a player, or look ray within ~12 deg of a player
// limits: if the server hides staff from tab AND world entirely (no packets at all) nothing can see them.
package com.cheat.module.impl;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class StaffList {

    public boolean enabled = true;
    public boolean alerts  = true;

    public static class Entry {
        public String name, reason, watching = "";
        public boolean inWorld, onMe, uncertain;
        long lastSeen;
    }

    public final List<Entry> entries = new CopyOnWriteArrayList<>();

    // FILL: ReallyWorld rank names are guessed. add the real ones (one per line, lowercase) to
    // .minecraft/config/ghostmod-staff.txt  — read from the tab prefix, e.g. "[Модератор]".
    private final Set<String> keywords = new HashSet<>(Arrays.asList(
            "admin", "adm", "администратор", "админ", "модер", "модератор", "мод", "moder", "moderator", "mod",
            "helper", "хелпер", "хелп", "помощник", "owner", "овнер", "владелец", "куратор", "curator",
            "стажер", "стажёр", "trainee", "support", "саппорт", "staff", "стафф", "dev", "разраб", "developer",
            "главный", "гл", "sr", "jr", "gm", "ст", "мл", "creator", "founder", "основатель", "персонал"));

    private final Map<String, Entry> memory = new HashMap<>();   // staff seen -> for "пропал из таба"
    private final Set<String> announced = new HashSet<>();
    private final Map<String, Long> lastMeAlert = new HashMap<>();
    private int tick;

    public StaffList() {
        try {
            Path p = FabricLoader.getInstance().getConfigDir().resolve("ghostmod-staff.txt");
            if (Files.exists(p))
                for (String l : Files.readAllLines(p)) { l = l.trim().toLowerCase(Locale.ROOT); if (!l.isEmpty()) keywords.add(l); }
        } catch (Throwable ignored) {}
    }

    public void toggle() {
        enabled = !enabled;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("§7[Ghost] StaffList " + (enabled ? "§aON" : "§cOFF")), true);
    }

    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) { entries.clear(); memory.clear(); announced.clear(); return; }
        if (++tick % 10 != 0) return;
        long now = System.currentTimeMillis();

        Map<String, PlayerListEntry> tab = new HashMap<>();
        for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) tab.put(e.getProfile().name(), e);
        Map<String, PlayerEntity> world = new HashMap<>();
        for (PlayerEntity p : mc.world.getPlayers()) world.put(p.getGameProfile().name(), p);

        Map<String, Entry> out = new LinkedHashMap<>();

        for (Map.Entry<String, PlayerListEntry> te : tab.entrySet()) {
            String name = te.getKey();
            if (name.equals(mc.player.getGameProfile().name())) continue;
            PlayerListEntry e = te.getValue();
            String rank = rankOf(e, name);
            boolean spec = e.getGameMode() == GameMode.SPECTATOR;
            if (rank == null && !spec) continue;
            Entry en = new Entry();
            en.name = name;
            en.reason = (rank != null ? "ранг " + rank : "") + (rank != null && spec ? " + " : "") + (spec ? "спектатор" : "");
            out.put(name, en);
        }

        // in world but not in tab list -> hidden (NPCs look the same, hence "?")
        for (String n : world.keySet()) {
            if (n.equals(mc.player.getGameProfile().name()) || tab.containsKey(n)) continue;
            Entry en = new Entry();
            en.name = n; en.reason = "в мире, но нет в табе"; en.uncertain = true;
            out.put(n, en);
        }

        // staff that vanished from tab without quitting
        for (Entry old : new ArrayList<>(memory.values())) {
            if (out.containsKey(old.name) || tab.containsKey(old.name)) continue;
            if (now - old.lastSeen > 90_000) { memory.remove(old.name); continue; }
            Entry en = new Entry();
            en.name = old.name; en.reason = "пропал из таба (ваниш?)"; en.uncertain = true;
            out.put(old.name, en);
        }

        for (Entry en : out.values()) {
            PlayerEntity ent = world.get(en.name);
            en.inWorld = ent != null;
            if (!en.reason.startsWith("пропал")) { en.lastSeen = now; if (!en.uncertain) memory.put(en.name, en); }
            if (ent != null) watching(mc, ent, en);
            if (alerts && announced.add(en.name))
                mc.player.sendMessage(Text.literal("§c[Staff] §f" + en.name + " §7— " + en.reason + (en.inWorld ? " §a(рядом)" : "")), false);
            if (alerts && en.onMe) {
                Long last = lastMeAlert.get(en.name);
                if (last == null || now - last > 5000) {
                    lastMeAlert.put(en.name, now);
                    mc.player.sendMessage(Text.literal("§4[Staff] §f" + en.name + " §cСМОТРИТ НА ТЕБЯ"), true);
                }
            }
        }
        announced.retainAll(out.keySet());   // allow a re-announce after they left and came back
        entries.clear(); entries.addAll(out.values());
    }

    private String rankOf(PlayerListEntry e, String name) {
        StringBuilder sb = new StringBuilder();
        if (e.getDisplayName() != null) sb.append(e.getDisplayName().getString().replace(name, " "));
        Team t = e.getScoreboardTeam();
        if (t != null) sb.append(' ').append(t.getPrefix().getString()).append(' ').append(t.getSuffix().getString());
        for (String tok : sb.toString().toLowerCase(Locale.ROOT).split("[^\\p{L}]+")) {
            if (tok.isEmpty()) continue;
            for (String k : keywords)
                if (tok.equals(k) || (k.length() >= 4 && tok.startsWith(k))) return tok;
        }
        return null;
    }

    private void watching(MinecraftClient mc, PlayerEntity ent, Entry en) {
        PlayerEntity best = null; double bestScore = 1e9; boolean sit = false;
        for (PlayerEntity o : mc.world.getPlayers()) {
            if (o == ent) continue;
            double d = ent.getPos().distanceTo(o.getPos());
            if (d < 2.2 && d < bestScore) { best = o; bestScore = d; sit = true; }       // spectating = parked on target
        }
        if (best == null) {
            Vec3d eye = ent.getEyePos(), look = ent.getRotationVec(1f);
            double bestAng = 12.0;
            for (PlayerEntity o : mc.world.getPlayers()) {
                if (o == ent) continue;
                Vec3d to = o.getBoundingBox().getCenter().subtract(eye);
                if (to.length() > 40 || to.length() < 0.5) continue;
                double ang = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, look.dotProduct(to.normalize())))));
                if (ang < bestAng) { bestAng = ang; best = o; }
            }
        }
        if (best != null) {
            boolean me = best == mc.player;
            en.watching = (sit ? "рядом с " : "смотрит на ") + (me ? "ТОБОЙ" : best.getGameProfile().name());
            en.onMe = me;
        }
    }

    public void render(DrawContext c) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled || mc.player == null || mc.options.hudHidden) return;
        int x = mc.getWindow().getScaledWidth() - 6, y = 6;
        String head = "Staff (" + entries.size() + ")";
        c.drawText(mc.textRenderer, head, x - mc.textRenderer.getWidth(head), y, entries.isEmpty() ? 0xFF55FF55 : 0xFFFF5555, true);
        y += 11;
        for (Entry e : entries) {
            String line = e.name + " [" + e.reason + "]" + (e.watching.isEmpty() ? "" : " → " + e.watching);
            int col = e.onMe ? 0xFFFF2222 : e.uncertain ? 0xFFFFCC44 : 0xFFFFFFFF;
            c.drawText(mc.textRenderer, line, x - mc.textRenderer.getWidth(line), y, col, true);
            y += 10;
        }
    }
}
