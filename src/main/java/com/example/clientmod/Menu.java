package com.example.clientmod;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import org.lwjgl.glfw.GLFW;

/** Mod Menu (Right Shift): category tabs + settings tab. */
public class Menu extends Screen {
    static int tab = 0;
    static final float[] SCALES = {0.8f, 1.0f, 1.2f, 1.5f};
    static final int[] BGS = {0, 80, 120, 180};
    static final float[] FOGS = {0.01f, 0.02f, 0.03f, 0.05f, 0.08f};

    Menu() { super(new StringTextComponent("ClientMod")); }

    static ITextComponent t(String s) { return new StringTextComponent(s); }

    static void reopen() { Cfg.save(); Minecraft.getInstance().displayGuiScreen(new Menu()); }

    @Override
    protected void init() {
        int n = Cfg.CATS.length, tw = 72, x0 = width / 2 - n * tw / 2;
        for (int i = 0; i < n; i++) {
            final int ti = i;
            addButton(new Button(x0 + i * tw, 10, tw - 2, 20, t((i == tab ? "> " : "") + Cfg.CATS[i]), b -> {
                tab = ti;
                reopen();
            }));
        }
        int bx = width / 2 - 165;
        if (tab < n - 1) {
            int i = 0;
            for (Cfg.Mod m : Cfg.MODS.values()) {
                if (!m.cat.equals(Cfg.CATS[tab])) continue;
                final Cfg.Mod mm = m;
                addButton(new Button(bx + (i % 3) * 111, 40 + (i / 3) * 24, 108, 20,
                        t(m.name + ": " + (m.on ? "ON" : "OFF")), b -> {
                    ClientMod.toggle(mm);
                    reopen();
                }));
                i++;
            }
        } else {
            addButton(new Button(bx, 40, 162, 20, t("Theme: " + Cfg.TN[Cfg.theme]), b -> {
                Cfg.theme = (Cfg.theme + 1) % Cfg.TN.length; reopen();
            }));
            addButton(new Button(bx + 168, 40, 162, 20, t("HUD scale: " + Cfg.hudScale), b -> {
                int i = 0;
                for (int k = 0; k < SCALES.length; k++) if (SCALES[k] == Cfg.hudScale) i = k;
                Cfg.hudScale = SCALES[(i + 1) % SCALES.length]; reopen();
            }));
            addButton(new Button(bx, 64, 162, 20, t("HUD background: " + Cfg.bgAlpha), b -> {
                int i = 0;
                for (int k = 0; k < BGS.length; k++) if (BGS[k] == Cfg.bgAlpha) i = k;
                Cfg.bgAlpha = BGS[(i + 1) % BGS.length]; reopen();
            }));
            addButton(new Button(bx + 168, 64, 162, 20, t("Open HUD Editor"), b -> {
                Cfg.save(); minecraft.displayGuiScreen(new Hud.Editor());
            }));
            addButton(new Button(bx, 88, 162, 20, t("Sky colour: " + Cfg.presetName(Cfg.skyIdx)), b -> {
                Cfg.setSky((Cfg.skyIdx + 1) % Cfg.PN.length); reopen();
            }));
            addButton(new Button(bx + 168, 88, 162, 20, t("Particles colour: " + Cfg.presetName(Cfg.partIdx)), b -> {
                Cfg.setPart((Cfg.partIdx + 1) % Cfg.PN.length); reopen();
            }));
            addButton(new Button(bx, 112, 162, 20, t("Fog colour: " + Cfg.presetName(Cfg.fogIdx)), b -> {
                Cfg.setFog((Cfg.fogIdx + 1) % Cfg.PN.length); reopen();
            }));
            addButton(new Button(bx + 168, 112, 162, 20, t("Fog density: " + Cfg.fogDensity), b -> {
                int i = 0;
                for (int k = 0; k < FOGS.length; k++) if (FOGS[k] == Cfg.fogDensity) i = k;
                Cfg.fogDensity = FOGS[(i + 1) % FOGS.length]; reopen();
            }));
        }
    }

    @Override
    public boolean keyPressed(int k, int s, int mod) {
        if (k == GLFW.GLFW_KEY_RIGHT_SHIFT) { Cfg.save(); closeScreen(); return true; }
        return super.keyPressed(k, s, mod);
    }

    @Override
    public void render(MatrixStack ms, int mx, int my, float pt) {
        renderBackground(ms);
        super.render(ms, mx, my, pt);
        if (tab == Cfg.CATS.length - 1) {
            int y = 142;
            String[] help = {
                    "Keybinds:  .bind <module> <key>   e.g.  .bind Fly G   (.bind Fly none = remove)",
                    "Friends:   .friend add|del|list <nick>      Enemies:  .enemy add|del|list <nick>",
                    "Colours:   .sky RRGGBB   .particles RRGGBB   .fog RRGGBB",
                    "AutoBuy:   .autobuy item <name>   .autobuy price <max|off>      Zoom: hold C",
                    "Save:      .save   (everything is also saved automatically)"};
            for (String h : help) { drawCenteredString(ms, font, h, width / 2, y, 0xDDDDDD); y += 12; }
            StringBuilder sb = new StringBuilder("Bound keys: ");
            boolean any = false;
            for (Cfg.Mod m : Cfg.MODS.values())
                if (m.key >= 0) { sb.append(m.name).append("=").append(GLFW.glfwGetKeyName(m.key, 0)).append("  "); any = true; }
            drawCenteredString(ms, font, any ? sb.toString() : "Bound keys: none", width / 2, y + 6, 0xFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
