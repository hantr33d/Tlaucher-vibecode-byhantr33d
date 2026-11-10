package com.example.clientmod;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.StringTextComponent;

import java.util.*;

/** All HUD elements + the drag-and-drop HUD editor. */
public class Hud {
    static final Minecraft mc = Minecraft.getInstance();
    static final String[] EL = {"Watermark", "ArrayList", "Coordinates", "FPS", "CPS", "Keystrokes",
            "Armor", "Effects", "Ping", "Speed", "TargetHUD"};
    static final Map<String, int[]> SIZE = new HashMap<>();

    static FontRenderer f() { return mc.fontRenderer; }

    static int sw() { return (int) (mc.getMainWindow().getScaledWidth() / Cfg.hudScale); }

    static int sh() { return (int) (mc.getMainWindow().getScaledHeight() / Cfg.hudScale); }

    static int rx(int x, int w) { return x == -1 ? sw() - w - 4 : x == -2 ? sw() / 2 - w / 2 : x; }

    static void box(MatrixStack ms, int x, int y, int w, int h) {
        AbstractGui.fill(ms, x, y, x + w, y + h, Cfg.bgAlpha << 24);
        AbstractGui.fill(ms, x, y, x + 2, y + h, 0xFF000000 | Cfg.accent(y * 0.01f));
    }

    static int[] line(MatrixStack ms, int x, int y, String s) {
        int w = f().getStringWidth(s) + 9;
        box(ms, x, y, w, 12);
        f().drawStringWithShadow(ms, s, x + 5, y + 2, 0xFFFFFFFF);
        return new int[]{w, 12};
    }

    static String time(int sec) { return (sec / 60) + ":" + String.format("%02d", sec % 60); }

    static void renderAll(MatrixStack ms) {
        float s = Cfg.hudScale;
        ms.push();
        ms.scale(s, s, 1f);
        for (String el : EL) {
            if (!Cfg.on(el)) continue;
            int[] p = Cfg.POS.get(el);
            int[] sz = SIZE.getOrDefault(el, new int[]{80, 12});
            SIZE.put(el, draw(el, ms, rx(p[0], sz[0]), p[1], false));
        }
        notifications(ms);
        ms.pop();
    }

    static void notifications(MatrixStack ms) {
        long now = System.currentTimeMillis();
        ClientMod.NOTES.removeIf(n -> now - (Long) n[1] > 2500);
        int i = 0;
        for (Object[] n : ClientMod.NOTES) {
            String t = (String) n[0];
            int w = f().getStringWidth(t) + 12;
            int x = sw() - w - 6, y = sh() - 30 - i * 16;
            box(ms, x, y, w, 14);
            f().drawStringWithShadow(ms, t, x + 7, y + 3, 0xFFFFFFFF);
            i++;
        }
    }

    static int[] draw(String el, MatrixStack ms, int x, int y, boolean editor) {
        switch (el) {
            case "Watermark":
                return line(ms, x, y, "ClientMod  |  " + mc.getSession().getUsername());
            case "Coordinates": {
                if (mc.player == null) return line(ms, x, y, "XYZ");
                String[] d = {"South", "West", "North", "East"};
                String dir = d[MathHelper.floor(mc.player.rotationYaw / 90.0 + 0.5) & 3];
                int[] a = line(ms, x, y, String.format("XYZ: %.1f  %.1f  %.1f", mc.player.getPosX(), mc.player.getPosY(), mc.player.getPosZ()));
                int[] b = line(ms, x, y + 12, "Facing: " + dir);
                return new int[]{Math.max(a[0], b[0]), 24};
            }
            case "FPS":
                return line(ms, x, y, "FPS: " + ClientMod.fps);
            case "CPS":
                return line(ms, x, y, "CPS: " + ClientMod.cps(ClientMod.LC) + " | " + ClientMod.cps(ClientMod.RC));
            case "Ping": {
                int ping = 0;
                if (mc.getConnection() != null && mc.player != null) {
                    net.minecraft.client.network.play.NetworkPlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUniqueID());
                    if (info != null) ping = info.getResponseTime();
                }
                return line(ms, x, y, "Ping: " + ping + " ms");
            }
            case "Speed":
                return line(ms, x, y, String.format("Speed: %.2f b/s", ClientMod.speed));
            case "Keystrokes": {
                int k = 20;
                key(ms, x + k, y, k - 2, "W", mc.gameSettings.keyBindForward.isKeyDown());
                key(ms, x, y + k, k - 2, "A", mc.gameSettings.keyBindLeft.isKeyDown());
                key(ms, x + k, y + k, k - 2, "S", mc.gameSettings.keyBindBack.isKeyDown());
                key(ms, x + 2 * k, y + k, k - 2, "D", mc.gameSettings.keyBindRight.isKeyDown());
                key2(ms, x, y + 2 * k, 29, "L " + ClientMod.cps(ClientMod.LC), mc.gameSettings.keyBindAttack.isKeyDown());
                key2(ms, x + 31, y + 2 * k, 29, "R " + ClientMod.cps(ClientMod.RC), mc.gameSettings.keyBindUseItem.isKeyDown());
                return new int[]{3 * k - 2, 3 * k - 2};
            }
            case "Armor": {
                box(ms, x, y, 74, 18);
                if (mc.player != null) {
                    EquipmentSlotType[] S = {EquipmentSlotType.HEAD, EquipmentSlotType.CHEST, EquipmentSlotType.LEGS, EquipmentSlotType.FEET};
                    for (int i = 0; i < 4; i++) {
                        ItemStack st = mc.player.getItemStackFromSlot(S[i]);
                        if (st.isEmpty()) continue;
                        int ix = (int) ((x + 3 + i * 17) * Cfg.hudScale), iy = (int) ((y + 1) * Cfg.hudScale);
                        mc.getItemRenderer().renderItemAndEffectIntoGUI(st, ix, iy);
                        mc.getItemRenderer().renderItemOverlayIntoGUI(f(), st, ix, iy, null);
                    }
                }
                return new int[]{74, 18};
            }
            case "Effects": {
                List<String> ls = new ArrayList<>();
                if (mc.player != null)
                    for (EffectInstance e : mc.player.getActivePotionEffects())
                        ls.add(I18n.format(e.getEffectName()) + " " + (e.getAmplifier() + 1) + "  " + time(e.getDuration() / 20));
                if (ls.isEmpty() && editor) ls.add("Effects");
                int w = 40;
                for (String s : ls) w = Math.max(w, f().getStringWidth(s) + 9);
                for (int i = 0; i < ls.size(); i++) {
                    box(ms, x, y + i * 12, w, 12);
                    f().drawStringWithShadow(ms, ls.get(i), x + 5, y + i * 12 + 2, 0xFFFFFFFF);
                }
                return new int[]{w, Math.max(12, ls.size() * 12)};
            }
            case "TargetHUD": {
                LivingEntity t = ClientMod.target;
                boolean show = t != null && t.isAlive() && ClientMod.targetTicks > 0;
                if (!show && !editor) return new int[]{120, 34};
                String name = show ? t.getName().getString() : "Target";
                float hp = show ? t.getHealth() : 20f;
                float max = show ? Math.max(1f, t.getMaxHealth()) : 20f;
                box(ms, x, y, 120, 34);
                f().drawStringWithShadow(ms, name, x + 6, y + 4, 0xFFFFFFFF);
                int bw = 108;
                AbstractGui.fill(ms, x + 6, y + 17, x + 6 + bw, y + 23, 0x66000000);
                float r = MathHelper.clamp(hp / max, 0f, 1f);
                int col = 0xFF000000 | ((int) (255 * (1 - r)) << 16) | ((int) (255 * r) << 8) | 0x30;
                AbstractGui.fill(ms, x + 6, y + 17, x + 6 + (int) (bw * r), y + 23, col);
                f().drawStringWithShadow(ms, String.format("%.1f / %.1f", hp, max), x + 6, y + 25, 0xFFDDDDDD);
                return new int[]{120, 34};
            }
            case "ArrayList": {
                List<String> act = new ArrayList<>();
                for (Cfg.Mod m : Cfg.MODS.values())
                    if (m.on && !m.cat.equals("HUD") && !m.name.equals("Notifications")) act.add(m.name);
                if (act.isEmpty() && editor) act.add("ArrayList");
                act.sort((a, b) -> f().getStringWidth(b) - f().getStringWidth(a));
                int mw = 30;
                for (String s : act) mw = Math.max(mw, f().getStringWidth(s) + 9);
                for (int i = 0; i < act.size(); i++) {
                    String s = act.get(i);
                    int w = f().getStringWidth(s) + 9;
                    int lx = x + mw - w, ly = y + i * 12;
                    AbstractGui.fill(ms, lx, ly, lx + w, ly + 12, Cfg.bgAlpha << 24);
                    AbstractGui.fill(ms, lx + w - 2, ly, lx + w, ly + 12, 0xFF000000 | Cfg.accent(i * 0.07f));
                    f().drawStringWithShadow(ms, s, lx + 4, ly + 2, 0xFF000000 | Cfg.accent(i * 0.07f));
                }
                return new int[]{mw, Math.max(12, act.size() * 12)};
            }
        }
        return new int[]{10, 10};
    }


    static void key(MatrixStack ms, int x, int y, int s, String t, boolean down) {
        AbstractGui.fill(ms, x, y, x + s, y + s, down ? (0xCC000000 | Cfg.accent(0)) : (Cfg.bgAlpha << 24));
        f().drawStringWithShadow(ms, t, x + (s - f().getStringWidth(t)) / 2f, y + (s - 8) / 2f, 0xFFFFFFFF);
    }

    static void key2(MatrixStack ms, int x, int y, int w, String t, boolean down) {
        AbstractGui.fill(ms, x, y, x + w, y + 18, down ? (0xCC000000 | Cfg.accent(0)) : (Cfg.bgAlpha << 24));
        f().drawStringWithShadow(ms, t, x + (w - f().getStringWidth(t)) / 2f, y + 5, 0xFFFFFFFF);
    }

    // ---------- drag & drop HUD editor ----------
    static class Editor extends Screen {
        String drag = null;
        int ox, oy;

        Editor() { super(new StringTextComponent("HUD Editor")); }

        @Override
        public void render(MatrixStack ms, int mx, int my, float pt) {
            renderBackground(ms);
            drawCenteredString(ms, font, "HUD Editor - drag the elements. Right Shift / Esc = save & close", width / 2, 6, 0xFFFFFF);
            float s = Cfg.hudScale;
            ms.push();
            ms.scale(s, s, 1f);
            for (String el : EL) {
                if (!Cfg.on(el)) continue;
                int[] p = Cfg.POS.get(el);
                int[] sz = SIZE.getOrDefault(el, new int[]{80, 12});
                int x = rx(p[0], sz[0]);
                int[] ns = draw(el, ms, x, p[1], true);
                SIZE.put(el, ns);
                int c = el.equals(drag) ? 0xFFFFFFFF : 0x88FFFFFF;
                AbstractGui.fill(ms, x - 1, p[1] - 1, x + ns[0] + 1, p[1], c);
                AbstractGui.fill(ms, x - 1, p[1] + ns[1], x + ns[0] + 1, p[1] + ns[1] + 1, c);
                AbstractGui.fill(ms, x - 1, p[1], x, p[1] + ns[1], c);
                AbstractGui.fill(ms, x + ns[0], p[1], x + ns[0] + 1, p[1] + ns[1], c);
            }
            ms.pop();
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            double x = mx / Cfg.hudScale, y = my / Cfg.hudScale;
            for (int i = EL.length - 1; i >= 0; i--) {
                String el = EL[i];
                if (!Cfg.on(el)) continue;
                int[] p = Cfg.POS.get(el);
                int[] sz = SIZE.getOrDefault(el, new int[]{80, 12});
                int px = rx(p[0], sz[0]);
                if (x >= px && x <= px + sz[0] && y >= p[1] && y <= p[1] + sz[1]) {
                    drag = el;
                    ox = (int) (x - px);
                    oy = (int) (y - p[1]);
                    return true;
                }
            }
            return super.mouseClicked(mx, my, btn);
        }

        @Override
        public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
            if (drag != null) {
                Cfg.POS.put(drag, new int[]{(int) (mx / Cfg.hudScale - ox), (int) (my / Cfg.hudScale - oy)});
                return true;
            }
            return super.mouseDragged(mx, my, btn, dx, dy);
        }

        @Override
        public boolean mouseReleased(double mx, double my, int btn) {
            if (drag != null) { drag = null; Cfg.save(); return true; }
            return super.mouseReleased(mx, my, btn);
        }

        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) { Cfg.save(); closeScreen(); return true; }
            return super.keyPressed(k, s, m);
        }

        @Override
        public boolean isPauseScreen() { return false; }
    }
}
