package com.example.clientmod;

import net.minecraft.client.Minecraft;

import java.io.*;
import java.util.*;

/** Modules, settings, themes and the save file (clientmod.properties in the game folder). */
public class Cfg {
    static class Mod {
        final String name, cat;
        boolean on;
        int key = -1;
        Mod(String n, String c, boolean o) { name = n; cat = c; on = o; }
    }

    static final String[] CATS = {"HUD", "Combat", "Movement", "Visuals", "Player", "Misc", "Settings"};
    static final Map<String, Mod> MODS = new LinkedHashMap<>();
    static final Set<String> friends = new HashSet<>(), enemies = new HashSet<>();
    static final Map<String, int[]> POS = new LinkedHashMap<>();

    static final String[] TN = {"Purple", "Ocean", "Fire", "Mint", "Sakura", "Mono", "Rainbow"};
    static final int[] TC = {0x9b3dff, 0x33b5ff, 0xff5a2a, 0x33ffaa, 0xff7ac8, 0xdddddd, 0xffffff};
    static final String[] PN = {"Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink", "White", "Rainbow"};
    static final int[] PC = {0xff3030, 0xff8c1a, 0xffe033, 0x33ff55, 0x33f0ff, 0x3366ff, 0x9b3dff, 0xff4fd8, 0xffffff, 0xffffff};
    static final int RAINBOW = 9;

    static int theme = 0;
    static float hudScale = 1f;
    static int bgAlpha = 120;
    static int skyIdx = 6, skyRGB = 0x9b3dff;
    static int partIdx = 7, partRGB = 0xff4fd8;
    static int fogIdx = 4, fogRGB = 0x33f0ff;
    static float fogDensity = 0.03f;
    static String buyItem = "";
    static long buyMax = -1;

    static void add(String n, String c, boolean on) { MODS.put(n, new Mod(n, c, on)); }

    static void pos(String n, int x, int y) { POS.put(n, new int[]{x, y}); }

    static {
        add("Watermark", "HUD", true); add("ArrayList", "HUD", true); add("Coordinates", "HUD", true);
        add("FPS", "HUD", true); add("CPS", "HUD", true); add("Keystrokes", "HUD", false);
        add("Armor", "HUD", true); add("Effects", "HUD", true); add("Ping", "HUD", true);
        add("Speed", "HUD", false); add("TargetHUD", "HUD", true); add("Zoom", "HUD", true);

        add("AttackAura", "Combat", false); add("Criticals", "Combat", false); add("BowAim", "Combat", false);
        add("AutoTotem", "Combat", false); add("AutoPearl", "Combat", false); add("AutoBuy", "Combat", false);

        add("Fly", "Movement", false); add("Sprint", "Movement", false);
        add("NoFall", "Movement", false); add("Spider", "Movement", false);

        add("XRay", "Visuals", false); add("ESP", "Visuals", false); add("Tracers", "Visuals", false);
        add("FullBright", "Visuals", false); add("BlockOutline", "Visuals", false); add("Fog", "Visuals", false);
        add("MotionBlur", "Visuals", false); add("Sky", "Visuals", false); add("Particles", "Visuals", false);

        add("Cosmetics", "Player", false); add("Animations", "Player", false); add("AutoRespawn", "Player", false);

        add("AutoReconnect", "Misc", false); add("AntiAFK", "Misc", false);
        add("Notifications", "Misc", true); add("FriendSystem", "Misc", true);

        pos("Watermark", 4, 4); pos("Coordinates", 4, 22); pos("FPS", 4, 52); pos("CPS", 4, 66);
        pos("Ping", 4, 80); pos("Speed", 4, 94); pos("Keystrokes", 4, 112); pos("Armor", 4, 176);
        pos("ArrayList", -1, 4); pos("Effects", -1, 120); pos("TargetHUD", -2, 150);
    }

    static boolean on(String n) { Mod m = MODS.get(n); return m != null && m.on; }

    static Mod find(String n) {
        for (Mod m : MODS.values()) if (m.name.equalsIgnoreCase(n)) return m;
        return null;
    }

    // ----- colours -----
    static float[] rgb(int c) {
        return new float[]{((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
    }

    static int hue(float off) {
        float h = ((System.currentTimeMillis() % 6000L) / 6000f + off) % 1f;
        return java.awt.Color.HSBtoRGB(h, 0.75f, 1f) & 0xFFFFFF;
    }

    static float[] rainbow(float off) { return rgb(hue(off)); }

    static int accent(float off) { return theme == TN.length - 1 ? hue(off) : TC[theme]; }

    static float[] accentF(float off) { return rgb(accent(off)); }

    static float[] skyCol() { return skyIdx == RAINBOW ? rainbow(0f) : rgb(skyRGB); }

    static float[] partCol(Random r) { return partIdx == RAINBOW ? rainbow(r.nextFloat()) : rgb(partRGB); }

    static float[] fogCol() { return fogIdx == RAINBOW ? rainbow(0f) : rgb(fogRGB); }

    static void setSky(int i) { skyIdx = i; if (i >= 0 && i != RAINBOW) skyRGB = PC[i]; }

    static void setPart(int i) { partIdx = i; if (i >= 0 && i != RAINBOW) partRGB = PC[i]; }

    static void setFog(int i) { fogIdx = i; if (i >= 0 && i != RAINBOW) fogRGB = PC[i]; }

    static String presetName(int i) { return i < 0 ? "Custom" : PN[i]; }

    // ----- save / load -----
    static File file() { return new File(Minecraft.getInstance().gameDir, "clientmod.properties"); }

    static void save() {
        Properties p = new Properties();
        for (Mod m : MODS.values()) {
            p.setProperty("mod." + m.name, "" + m.on);
            p.setProperty("key." + m.name, "" + m.key);
        }
        for (Map.Entry<String, int[]> e : POS.entrySet())
            p.setProperty("pos." + e.getKey(), e.getValue()[0] + "," + e.getValue()[1]);
        p.setProperty("theme", "" + theme);
        p.setProperty("scale", "" + hudScale);
        p.setProperty("bg", "" + bgAlpha);
        p.setProperty("skyIdx", "" + skyIdx); p.setProperty("skyRGB", "" + skyRGB);
        p.setProperty("partIdx", "" + partIdx); p.setProperty("partRGB", "" + partRGB);
        p.setProperty("fogIdx", "" + fogIdx); p.setProperty("fogRGB", "" + fogRGB);
        p.setProperty("fogDensity", "" + fogDensity);
        p.setProperty("buyItem", buyItem);
        p.setProperty("buyMax", "" + buyMax);
        p.setProperty("friends", String.join(",", friends));
        p.setProperty("enemies", String.join(",", enemies));
        try (FileOutputStream o = new FileOutputStream(file())) {
            p.store(o, "ClientMod");
        } catch (IOException ignored) { }
    }

    static void load() {
        File f = file();
        if (!f.exists()) return;
        Properties p = new Properties();
        try (FileInputStream i = new FileInputStream(f)) {
            p.load(i);
        } catch (IOException e) { return; }
        try {
            for (Mod m : MODS.values()) {
                m.on = Boolean.parseBoolean(p.getProperty("mod." + m.name, "" + m.on));
                m.key = Integer.parseInt(p.getProperty("key." + m.name, "" + m.key));
            }
            for (String k : new ArrayList<>(POS.keySet())) {
                String v = p.getProperty("pos." + k);
                if (v != null) { String[] s = v.split(","); POS.put(k, new int[]{Integer.parseInt(s[0]), Integer.parseInt(s[1])}); }
            }
            theme = Math.min(Integer.parseInt(p.getProperty("theme", "0")), TN.length - 1);
            hudScale = Float.parseFloat(p.getProperty("scale", "1"));
            bgAlpha = Integer.parseInt(p.getProperty("bg", "120"));
            skyIdx = Integer.parseInt(p.getProperty("skyIdx", "6")); skyRGB = Integer.parseInt(p.getProperty("skyRGB", "" + skyRGB));
            partIdx = Integer.parseInt(p.getProperty("partIdx", "7")); partRGB = Integer.parseInt(p.getProperty("partRGB", "" + partRGB));
            fogIdx = Integer.parseInt(p.getProperty("fogIdx", "4")); fogRGB = Integer.parseInt(p.getProperty("fogRGB", "" + fogRGB));
            fogDensity = Float.parseFloat(p.getProperty("fogDensity", "0.03"));
            buyItem = p.getProperty("buyItem", "");
            buyMax = Long.parseLong(p.getProperty("buyMax", "-1"));
            for (String s : p.getProperty("friends", "").split(",")) if (!s.isEmpty()) friends.add(s);
            for (String s : p.getProperty("enemies", "").split(",")) if (!s.isEmpty()) enemies.add(s);
        } catch (Exception ignored) { }
    }
}
