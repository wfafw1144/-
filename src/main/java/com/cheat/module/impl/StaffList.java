// language: Java, file: StaffList.java, target: MC 1.21.11 Fabric (yarn)
// Finds staff even when they are hidden from the tab list. Passive: reads only what the server already sent.
// signals (strongest first):
//   1. rank words (admin/mod/helper/curator/...) in tab display name, scoreboard-team prefix/suffix or team name
//   2. names that sit in a scoreboard team but NOT in the tab list  (vanish plugins usually forget the team packet)
//   3. spectator game-mode (tab entry or world entity)
//   4. player entities in the world that are not in the tab list / are invisible
// "who is he watching": a spectator entity is teleported onto its target every tick -> nearest non-spectator
// player within 1 block (YOU included) = target. needs the server to actually send the spectator entity to us.
package com.cheat.module.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

import java.util.*;
import java.util.regex.Pattern;

public class StaffList {

    public boolean enabled = true;
    public boolean alerts = true;      // chat line on new staff / when staff starts watching YOU

    private static final Pattern RANK = Pattern.compile(
            "(?<![\\p{L}])(admin\\p{L}*|adm|mod|moder\\p{L}*|helper|hlp|owner|staff|support|curator|dev|developer|"
          + "админ\\p{L}*|модер\\p{L}*|хелпер\\p{L}*|"   // админ* модер* хелпер*
          + "куратор\\p{L}*|стаж[её]р|саппорт|"  // куратор* стажёр саппорт
          + "стафф|владелец|создатель|"      // стафф владелец создатель
          + "разраб\\p{L}*|помощник)(?![\\p{L}])",                           // разраб* помощник
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private static final class E {
        String name, reason = "", target;
        int sev = -1, stamp = -1, first, last;
        boolean staff, announced, watchedMe;
        double dist = -1;
        boolean spec;
    }

    private final Map<String, E> map = new LinkedHashMap<>();
    private int tick;
    private Object lastWorld;

    private E mark(String name, String reason, int sev, boolean staff) {
        String k = name.toLowerCase(Locale.ROOT);
        E e = map.get(k);
        if (e == null) { e = new E(); e.name = name; e.first = tick; map.put(k, e); }
        if (e.stamp != tick) { e.stamp = tick; e.sev = -1; }
        if (sev >= e.sev) { e.sev = sev; e.reason = reason; }
        if (staff) e.staff = true;
        e.last = tick;
        return e;
    }

    private static String deco(String shown, String name) {
        return shown == null ? "" : shown.replace(name, "");
    }

    public void tick(MinecraftClient mc) {
        ClientPlayNetworkHandler nh = mc.getNetworkHandler();
        if (!enabled || mc.world == null || mc.player == null || nh == null) { map.clear(); return; }
        if (mc.world != lastWorld) { lastWorld = mc.world; map.clear(); }
        tick++;
        String me = mc.player.getName().getString();
        Scoreboard sb = mc.world.getScoreboard();

        Set<String> inTab = new HashSet<>();
        Set<String> specTab = new HashSet<>();
        for (PlayerListEntry pe : nh.getPlayerList()) {
            String n = pe.getProfile().name();
            if (n == null || n.equalsIgnoreCase(me)) continue;
            inTab.add(n.toLowerCase(Locale.ROOT));
            String d = deco(pe.getDisplayName() == null ? null : pe.getDisplayName().getString(), n);
            Team t = sb.getScoreHolderTeam(n);
            if (t != null) d += " " + t.getPrefix().getString() + t.getSuffix().getString() + " " + t.getName();
            if (RANK.matcher(d).find()) mark(n, "tab: " + d.trim().replaceAll("\\s+", " "), 3, true);
            else if (pe.getGameMode() == GameMode.SPECTATOR) { mark(n, "spectator (tab)", 2, false).spec = true; specTab.add(n.toLowerCase(Locale.ROOT)); }
        }

        if (tick % 10 == 1) {
            for (Team t : sb.getTeams()) {
                String d = t.getPrefix().getString() + t.getSuffix().getString() + " " + t.getName();
                boolean staffTeam = RANK.matcher(d).find();
                for (String n : t.getPlayerList()) {
                    if (n == null || !NAME.matcher(n).matches() || n.equalsIgnoreCase(me)) continue;
                    boolean inT = inTab.contains(n.toLowerCase(Locale.ROOT));
                    if (staffTeam) mark(n, (inT ? "team: " : "HIDDEN team: ") + d.trim().replaceAll("\\s+", " "), inT ? 3 : 4, true);
                    else if (!inT) mark(n, "hidden (in team, not in tab)", 2, false);
                }
            }
        }

        List<AbstractClientPlayerEntity> ps = mc.world.getPlayers();
        for (AbstractClientPlayerEntity p : ps) {
            if (p == mc.player) continue;
            String n = p.getName().getString();
            String k = n.toLowerCase(Locale.ROOT);
            if (p.isSpectator()) mark(n, "spectator", 2, false).spec = true;
            else if (!inTab.contains(k)) mark(n, "in world, not in tab", 1, false);
            else if (p.isInvisible()) mark(n, "invisible", 1, false);
        }

        // spectate target + distance for everything we can see as an entity
        for (E e : map.values()) {
            if (e.last != tick) continue;
            AbstractClientPlayerEntity ent = null;
            for (AbstractClientPlayerEntity p : ps) if (p.getName().getString().equalsIgnoreCase(e.name)) { ent = p; break; }
            e.target = null; e.dist = -1;
            if (ent == null) continue;
            e.dist = Math.sqrt(ent.squaredDistanceTo(mc.player));
            if (!(ent.isSpectator() || e.spec || e.staff)) continue;
            double best = 1.0;
            for (AbstractClientPlayerEntity q : ps) {
                if (q == ent || q.isSpectator()) continue;
                double d = ent.squaredDistanceTo(q);
                if (d < best) { best = d; e.target = q == mc.player ? "YOU" : q.getName().getString(); }
            }
        }

        if (alerts) for (E e : map.values()) {
            if (e.last != tick) continue;
            if (e.staff && !e.announced) { e.announced = true; say(mc, "§e[Staff] §c" + e.name + " §7" + e.reason); }
            boolean me2 = "YOU".equals(e.target);
            if (me2 && !e.watchedMe && (e.staff || e.spec)) say(mc, "§4[Staff] §c" + e.name + " §fсмотрит на тебя");   // смотрит на тебя
            e.watchedMe = me2;
        }

        map.values().removeIf(e -> tick - e.last > 400);   // forget 20 s after the last signal
    }

    private static void say(MinecraftClient mc, String s) {
        try { mc.inGameHud.getChatHud().addMessage(Text.literal(s)); } catch (Throwable ignored) {}
    }

    public void render(DrawContext c) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled || mc.player == null || mc.options.hudHidden || map.isEmpty()) return;
        List<E> l = new ArrayList<>(map.values());
        l.sort(Comparator.comparingInt((E e) -> e.staff ? 0 : 1).thenComparingInt(e -> -e.sev).thenComparing(e -> e.name));
        List<String> lines = new ArrayList<>(); List<Integer> cols = new ArrayList<>();
        lines.add("Staff [" + (int) l.stream().filter(e -> e.staff).count() + "]"); cols.add(0xFFFFFFFF);
        int shown = 0;
        for (E e : l) {
            if (shown++ >= 14) break;
            boolean live = tick - e.last <= 15;
            String r = e.reason.length() > 28 ? e.reason.substring(0, 28) + ".." : e.reason;
            String s = e.name + "  " + r;
            if (live && e.target != null) s += "  -> " + e.target;
            if (live && e.dist >= 0) s += "  " + (int) e.dist + "m";
            if (!live) s += "  (gone " + ((tick - e.last) / 20) + "s)";
            lines.add(s);
            int col = !live ? 0xFF777777 : "YOU".equals(e.target) ? 0xFFFF2222 : e.staff ? 0xFFFF6666 : e.sev >= 2 ? 0xFFFFAA00 : 0xFF999999;
            cols.add(col);
        }
        int w = 0;
        for (String s : lines) w = Math.max(w, mc.textRenderer.getWidth(s));
        int x = mc.getWindow().getScaledWidth() - w - 10, y = 6;
        c.fill(x - 4, y - 3, x + w + 4, y + lines.size() * 10 + 1, 0x90000000);
        for (int i = 0; i < lines.size(); i++) c.drawText(mc.textRenderer, lines.get(i), x, y + i * 10, cols.get(i), true);
    }
}
