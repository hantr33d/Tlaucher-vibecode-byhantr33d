package com.example.clientmod;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.ConnectingScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.world.DimensionRenderInfo;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.network.play.client.CPlayerPacket;
import net.minecraft.particles.RedstoneParticleData;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.client.ISkyRenderHandler;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.*;

@Mod("clientmod")
public class ClientMod {
    static final Minecraft mc = Minecraft.getInstance();
    static final Random R = new Random();
    static final Map<Block, float[]> ORES = new HashMap<>();
    static final double RANGE = 4.2;

    static List<BlockPos> ores = new ArrayList<>();
    static boolean wasFlying = false, mbActive = false;
    static double oldGamma = -1, speed = 0;
    static int pearlCd = 0, afk = 0, rcTick = 0, buyDelay = 0, haloTick = 0;
    static float zoom = 1f;
    static LivingEntity target;
    static int targetTicks = 0;
    static ServerData lastServer;
    static final Deque<Long> LC = new ArrayDeque<>(), RC = new ArrayDeque<>();
    static final List<Object[]> NOTES = new ArrayList<>();
    static int frames = 0, fps = 0;
    static long fpsT = System.currentTimeMillis();
    static final SkyHandler SKY = new SkyHandler();
    static final float[][] STARS = new float[260][];

    static {
        ORES.put(Blocks.DIAMOND_ORE, new float[]{0.2f, 1f, 1f});
        ORES.put(Blocks.GOLD_ORE, new float[]{1f, 0.9f, 0.1f});
        ORES.put(Blocks.IRON_ORE, new float[]{0.85f, 0.7f, 0.55f});
        ORES.put(Blocks.EMERALD_ORE, new float[]{0.1f, 1f, 0.2f});
        ORES.put(Blocks.REDSTONE_ORE, new float[]{1f, 0.1f, 0.1f});
        ORES.put(Blocks.LAPIS_ORE, new float[]{0.2f, 0.3f, 1f});
        ORES.put(Blocks.COAL_ORE, new float[]{0.4f, 0.4f, 0.4f});
        ORES.put(Blocks.ANCIENT_DEBRIS, new float[]{0.6f, 0.3f, 0.1f});
        ORES.put(Blocks.NETHER_GOLD_ORE, new float[]{1f, 0.8f, 0.2f});
        ORES.put(Blocks.NETHER_QUARTZ_ORE, new float[]{1f, 1f, 1f});
        Random sr = new Random(1337);
        for (int i = 0; i < STARS.length; i++) {
            double y = 0.08 + sr.nextDouble() * 0.92, a = sr.nextDouble() * Math.PI * 2, r = Math.sqrt(1 - y * y);
            STARS[i] = new float[]{(float) (Math.cos(a) * r), (float) y, (float) (Math.sin(a) * r)};
        }
    }

    public ClientMod() {
        Cfg.load();
        MinecraftForge.EVENT_BUS.register(this);
    }

    // ---------- helpers ----------
    static boolean on(String n) { return Cfg.on(n); }

    static void msg(String s) {
        if (mc.player != null) mc.player.sendStatusMessage(new StringTextComponent(s), false);
    }

    static void notifyUser(String s) {
        if (on("Notifications")) NOTES.add(new Object[]{s, System.currentTimeMillis()});
    }

    static void toggle(Cfg.Mod m) {
        m.on = !m.on;
        notifyUser(m.name + (m.on ? " enabled" : " disabled"));
        Cfg.save();
    }

    static int cps(Deque<Long> d) {
        long now = System.currentTimeMillis();
        while (!d.isEmpty() && now - d.peekFirst() > 1000) d.pollFirst();
        return d.size();
    }

    static boolean isFriend(String n) { return on("FriendSystem") && Cfg.friends.contains(n); }

    static PlayerEntity nearest(ClientPlayerEntity p, double range) {
        PlayerEntity t = null;
        double best = 1e9;
        for (AbstractClientPlayerEntity o : mc.world.getPlayers()) {
            if (o == p || !o.isAlive()) continue;
            String n = o.getName().getString().toLowerCase();
            if (isFriend(n)) continue;
            if (!Cfg.enemies.isEmpty() && !Cfg.enemies.contains(n)) continue;
            double d = p.getDistance(o);
            if (d <= range && d < best) { best = d; t = o; }
        }
        return t;
    }

    static void v(BufferBuilder b, Matrix4f m, float x, float y, float z, float[] c) {
        b.pos(m, x, y, z).color(c[0], c[1], c[2], 1f).endVertex();
    }

    // ---------- main tick ----------
    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        // AutoReconnect works while no world is loaded
        if (on("AutoReconnect")) {
            if (mc.getCurrentServerData() != null) lastServer = mc.getCurrentServerData();
            if (mc.currentScreen instanceof DisconnectedScreen && lastServer != null) {
                if (++rcTick >= 80) {
                    rcTick = 0;
                    mc.displayGuiScreen(new ConnectingScreen(new MainMenuScreen(), mc, lastServer));
                }
            } else rcTick = 0;
        }

        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;

        if (targetTicks > 0) targetTicks--;
        if (mc.pointedEntity instanceof PlayerEntity) { target = (LivingEntity) mc.pointedEntity; targetTicks = 60; }

        double sx = p.getPosX() - p.lastTickPosX, sz = p.getPosZ() - p.lastTickPosZ;
        speed = Math.sqrt(sx * sx + sz * sz) * 20.0;

        // zoom (hold C)
        boolean zooming = on("Zoom") && mc.currentScreen == null
                && GLFW.glfwGetKey(mc.getMainWindow().getHandle(), GLFW.GLFW_KEY_C) == GLFW.GLFW_PRESS;
        zoom += ((zooming ? 4f : 1f) - zoom) * 0.35f;

        // custom sky handler
        DimensionRenderInfo dri = mc.world.getDimensionRenderInfo();
        if (on("Sky")) {
            if (dri.getSkyRenderHandler() != SKY) dri.setSkyRenderHandler(SKY);
        } else if (dri.getSkyRenderHandler() == SKY) {
            dri.setSkyRenderHandler(null);
        }

        // motion blur (vanilla "phosphor" ghosting shader)
        if (on("MotionBlur")) {
            if (mc.gameRenderer.getShaderGroup() == null) {
                mc.gameRenderer.loadShader(new ResourceLocation("shaders/post/phosphor.json"));
                mbActive = true;
            }
        } else if (mbActive) {
            mc.gameRenderer.stopUseShader();
            mbActive = false;
        }

        // world particles
        if (on("Particles")) {
            for (int i = 0; i < 4; i++) {
                float[] c = Cfg.partCol(R);
                mc.world.addParticle(new RedstoneParticleData(c[0], c[1], c[2], 1.6f),
                        p.getPosX() + (R.nextDouble() - 0.5) * 26, p.getPosY() + R.nextDouble() * 10 - 2,
                        p.getPosZ() + (R.nextDouble() - 0.5) * 26, 0, -0.02, 0);
            }
        }

        // cosmetics: rotating halo above your head (client-side, seen in 3rd person)
        if (on("Cosmetics")) {
            haloTick++;
            for (int i = 0; i < 3; i++) {
                double a = haloTick * 0.25 + i * Math.PI * 2 / 3;
                float[] c = Cfg.accentF(i * 0.15f);
                mc.world.addParticle(new RedstoneParticleData(c[0], c[1], c[2], 0.9f),
                        p.getPosX() + Math.cos(a) * 0.38, p.getPosY() + p.getHeight() + 0.3, p.getPosZ() + Math.sin(a) * 0.38, 0, 0, 0);
            }
        }

        // FullBright
        if (on("FullBright")) {
            if (oldGamma < 0) oldGamma = mc.gameSettings.gamma;
            mc.gameSettings.gamma = 15.0;
        } else if (oldGamma >= 0) {
            mc.gameSettings.gamma = oldGamma;
            oldGamma = -1;
        }

        // AutoRespawn
        if (on("AutoRespawn") && !p.isAlive()) p.respawnPlayer();
        if (!p.isAlive()) return;

        // AntiAFK
        if (on("AntiAFK") && ++afk >= 400) {
            afk = 0;
            p.rotationYaw += (R.nextBoolean() ? 6 : -6);
            if (p.isOnGround()) p.jump();
            p.swingArm(Hand.MAIN_HAND);
        }

        // Fly
        if (on("Fly")) {
            p.abilities.allowFlying = true;
            p.abilities.isFlying = true;
            wasFlying = true;
        } else if (wasFlying) {
            wasFlying = false;
            if (!p.isCreative() && !p.isSpectator()) {
                p.abilities.allowFlying = false;
                p.abilities.isFlying = false;
            }
        }

        // Spider
        if (on("Spider") && p.collidedHorizontally) {
            Vector3d mo = p.getMotion();
            p.setMotion(mo.x, 0.2, mo.z);
        }

        if (on("Sprint") && p.moveForward > 0 && !p.collidedHorizontally) p.setSprinting(true);

        if (on("NoFall") && p.fallDistance > 2f) {
            mc.getConnection().sendPacket(new CPlayerPacket.PositionPacket(p.getPosX(), p.getPosY(), p.getPosZ(), true));
        }

        // AutoTotem
        if (on("AutoTotem") && p.getHeldItemOffhand().getItem() != Items.TOTEM_OF_UNDYING) {
            for (int s = 9; s <= 44; s++) {
                if (p.container.getSlot(s).getStack().getItem() == Items.TOTEM_OF_UNDYING) {
                    mc.playerController.windowClick(0, s, 0, ClickType.PICKUP, p);
                    mc.playerController.windowClick(0, 45, 0, ClickType.PICKUP, p);
                    mc.playerController.windowClick(0, s, 0, ClickType.PICKUP, p);
                    break;
                }
            }
        }

        // AutoPearl (low health -> pearl thrown where you look)
        if (pearlCd > 0) pearlCd--;
        if (on("AutoPearl") && pearlCd == 0 && p.getHealth() <= 6f
                && !p.getCooldownTracker().hasCooldown(Items.ENDER_PEARL)) {
            int slot = -1;
            for (int i = 0; i < 9; i++)
                if (p.inventory.getStackInSlot(i).getItem() == Items.ENDER_PEARL) slot = i;
            if (slot >= 0) {
                int old = p.inventory.currentItem;
                p.inventory.currentItem = slot;
                mc.playerController.syncCurrentPlayItem();
                mc.playerController.processRightClick(p, mc.world, Hand.MAIN_HAND);
                p.inventory.currentItem = old;
                mc.playerController.syncCurrentPlayItem();
                pearlCd = 40;
            }
        }

        if (on("BowAim")) bowAim(p);
        if (on("AutoBuy")) autoBuy(p);

        // AttackAura
        if (on("AttackAura") && p.getCooledAttackStrength(0f) >= 1f) {
            PlayerEntity t = nearest(p, RANGE);
            if (t != null) {
                target = t;
                targetTicks = 60;
                Vector3d diff = t.getPositionVec().add(0, t.getEyeHeight() * 0.9, 0).subtract(p.getEyePosition(1f));
                float yaw = (float) (Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
                float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, Math.sqrt(diff.x * diff.x + diff.z * diff.z)));
                p.rotationYaw = yaw;
                p.rotationPitch = pitch;
                mc.getConnection().sendPacket(new CPlayerPacket.RotationPacket(yaw, pitch, p.isOnGround()));
                if (on("Criticals") && p.isOnGround()) {
                    double x = p.getPosX(), y = p.getPosY(), z = p.getPosZ();
                    mc.getConnection().sendPacket(new CPlayerPacket.PositionPacket(x, y + 0.0625, z, false));
                    mc.getConnection().sendPacket(new CPlayerPacket.PositionPacket(x, y, z, false));
                }
                mc.playerController.attackEntity(p, t);
                p.swingArm(Hand.MAIN_HAND);
            }
        }

        // XRay ore cache
        if (on("XRay") && mc.world.getGameTime() % 20 == 0) {
            List<BlockPos> l = new ArrayList<>();
            BlockPos o = p.getPosition();
            BlockPos.Mutable m = new BlockPos.Mutable();
            int r = 16;
            for (int x = -r; x <= r; x++)
                for (int y = -r; y <= r; y++)
                    for (int z = -r; z <= r; z++) {
                        m.setPos(o.getX() + x, o.getY() + y, o.getZ() + z);
                        if (ORES.containsKey(mc.world.getBlockState(m).getBlock())) l.add(m.toImmutable());
                    }
            ores = l;
        }
    }

    // ---------- bow aimbot ----------
    static void bowAim(ClientPlayerEntity p) {
        if (!p.isHandActive() || !(p.getActiveItemStack().getItem() instanceof BowItem)) return;
        int elapsed = p.getActiveItemStack().getUseDuration() - p.getItemInUseCount();
        float f = BowItem.getArrowVelocity(elapsed);
        if (f < 0.15f) return;
        double spd = f * 3.0;
        PlayerEntity t = nearest(p, 70);
        if (t == null) return;
        target = t;
        targetTicks = 60;

        Vector3d eye = p.getEyePosition(1f);
        Vector3d tp = t.getPositionVec().add(0, t.getHeight() * 0.6, 0);
        double dvx = t.getPosX() - t.lastTickPosX, dvz = t.getPosZ() - t.lastTickPosZ;
        double ticks = eye.distanceTo(tp) / Math.max(spd * 0.9, 0.5);
        double dx = tp.x + dvx * ticks - eye.x, dz = tp.z + dvz * ticks - eye.z, dy = tp.y - eye.y;
        double hd = Math.sqrt(dx * dx + dz * dz);

        double bestErr = 1e9, bestPitch = 0;
        for (double a = -60; a <= 60; a += 0.25) {
            double r = Math.toRadians(a);
            double vx = spd * Math.cos(r), vy = spd * Math.sin(r), x = 0, y = 0;
            for (int i = 0; i < 200; i++) {
                x += vx; y += vy; vx *= 0.99; vy = vy * 0.99 - 0.05;
                if (x >= hd) {
                    double err = Math.abs(y - dy);
                    if (err < bestErr) { bestErr = err; bestPitch = a; }
                    break;
                }
            }
        }
        p.rotationYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        p.rotationPitch = (float) -bestPitch;
    }

    // ---------- AutoBuy: server shop / auction GUIs ----------
    static long price(ItemStack st, ClientPlayerEntity p) {
        for (ITextComponent line : st.getTooltip(p, ITooltipFlag.TooltipFlags.NORMAL)) {
            String s = line.getString().toLowerCase();
            if (s.contains("цена") || s.contains("price") || s.contains("$") || s.contains("стоимость")) {
                String d = s.replaceAll("[^0-9]", "");
                if (!d.isEmpty() && d.length() < 18) return Long.parseLong(d);
            }
        }
        return Long.MAX_VALUE;
    }

    static void autoBuy(ClientPlayerEntity p) {
        Container c = p.openContainer;
        if (!(mc.currentScreen instanceof ContainerScreen) || c == null || c == p.container) return;
        if (buyDelay-- > 0) return;
        String want = Cfg.buyItem.toLowerCase();
        if (!want.isEmpty()) {
            for (Slot s : c.inventorySlots) {
                if (s.inventory == p.inventory) continue;
                ItemStack st = s.getStack();
                if (st.isEmpty()) continue;
                String reg = st.getItem().getRegistryName().getPath();
                String nm = st.getDisplayName().getString().toLowerCase();
                if (!(reg.contains(want) || nm.contains(want))) continue;
                if (Cfg.buyMax >= 0 && price(st, p) > Cfg.buyMax) continue;
                mc.playerController.windowClick(c.windowId, s.slotNumber, 0, ClickType.PICKUP, p);
                buyDelay = 4;
                return;
            }
        }
        Item[] ok = {Items.LIME_STAINED_GLASS_PANE, Items.GREEN_STAINED_GLASS_PANE, Items.LIME_CONCRETE, Items.LIME_WOOL, Items.GREEN_WOOL};
        for (Slot s : c.inventorySlots) {
            if (s.inventory == p.inventory) continue;
            Item it = s.getStack().getItem();
            for (Item o : ok)
                if (it == o) {
                    mc.playerController.windowClick(c.windowId, s.slotNumber, 0, ClickType.PICKUP, p);
                    buyDelay = 4;
                    return;
                }
        }
    }

    // ---------- custom sky ----------
    static class SkyHandler implements ISkyRenderHandler {
        @Override
        public void render(int ticks, float pt, MatrixStack ms, ClientWorld world, Minecraft mc) {
            float[] h = Cfg.skyCol();
            float[] t = {h[0] * 0.45f, h[1] * 0.45f, h[2] * 0.45f};
            float[] white = {1f, 1f, 1f};
            float s = 100f;

            RenderSystem.disableTexture();
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);

            Matrix4f m = ms.getLast().getMatrix();
            Tessellator ts = Tessellator.getInstance();
            BufferBuilder b = ts.getBuffer();
            b.begin(7, DefaultVertexFormats.POSITION_COLOR);

            v(b, m, -s, s, -s, t); v(b, m, -s, s, s, t); v(b, m, s, s, s, t); v(b, m, s, s, -s, t);
            v(b, m, -s, -s, -s, h); v(b, m, s, -s, -s, h); v(b, m, s, -s, s, h); v(b, m, -s, -s, s, h);
            float[][] c = {{-s, -s}, {s, -s}, {s, s}, {-s, s}};
            for (int i = 0; i < 4; i++) {
                float[] a = c[i], d = c[(i + 1) % 4];
                v(b, m, a[0], -s, a[1], h); v(b, m, d[0], -s, d[1], h); v(b, m, d[0], 0, d[1], h); v(b, m, a[0], 0, a[1], h);
                v(b, m, a[0], 0, a[1], h); v(b, m, d[0], 0, d[1], h); v(b, m, d[0], s, d[1], t); v(b, m, a[0], s, a[1], t);
            }
            float rad = 90f, size = 0.55f;
            for (float[] st : STARS) {
                float ux = st[2], uz = -st[0];
                float ul = (float) Math.sqrt(ux * ux + uz * uz);
                ux /= ul; uz /= ul;
                float wx = st[1] * uz, wy = st[2] * ux - st[0] * uz, wz = -st[1] * ux;
                float cx = st[0] * rad, cy = st[1] * rad, cz = st[2] * rad;
                v(b, m, cx - ux * size - wx * size, cy - wy * size, cz - uz * size - wz * size, white);
                v(b, m, cx + ux * size - wx * size, cy - wy * size, cz + uz * size - wz * size, white);
                v(b, m, cx + ux * size + wx * size, cy + wy * size, cz + uz * size + wz * size, white);
                v(b, m, cx - ux * size + wx * size, cy + wy * size, cz - uz * size + wz * size, white);
            }
            ts.draw();

            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.enableTexture();
        }
    }

    // ---------- fog / fov ----------
    @SubscribeEvent
    public void fogColor(EntityViewRenderEvent.FogColors e) {
        float[] c = null;
        if (on("Fog")) c = Cfg.fogCol();
        else if (on("Sky")) c = Cfg.skyCol();
        if (c != null) { e.setRed(c[0]); e.setGreen(c[1]); e.setBlue(c[2]); }
    }

    @SubscribeEvent
    public void fogDensity(EntityViewRenderEvent.FogDensity e) {
        if (on("Fog")) { e.setDensity(Cfg.fogDensity); e.setCanceled(true); }
    }

    @SubscribeEvent
    public void fov(EntityViewRenderEvent.FOVModifier e) {
        if (zoom > 1.01f) e.setFOV(e.getFOV() / zoom);
    }

    // ---------- block outline ----------
    @SubscribeEvent
    public void outline(DrawHighlightEvent.HighlightBlock e) {
        if (!on("BlockOutline") || mc.world == null) return;
        BlockPos pos = e.getTarget().getPos();
        BlockState st = mc.world.getBlockState(pos);
        if (st.getShape(mc.world, pos).isEmpty()) return;
        AxisAlignedBB bb = st.getShape(mc.world, pos).getBoundingBox().offset(pos).grow(0.002);
        Vector3d cam = e.getInfo().getProjectedView();
        IVertexBuilder vb = e.getBuffers().getBuffer(RenderType.getLines());
        float[] c = Cfg.accentF(0f);
        WorldRenderer.drawBoundingBox(e.getMatrix(), vb, bb.offset(-cam.x, -cam.y, -cam.z), c[0], c[1], c[2], 1f);
        e.setCanceled(true);
    }

    // ---------- animations (smaller item + tilt on swing) ----------
    @SubscribeEvent
    public void hand(RenderHandEvent e) {
        if (!on("Animations")) return;
        MatrixStack ms = e.getMatrixStack();
        ms.scale(0.85f, 0.85f, 0.85f);
        ms.translate(0.06, -0.04, 0);
        ms.rotate(Vector3f.XP.rotationDegrees(-e.getSwingProgress() * 18f));
    }

    // ---------- world rendering: xray, esp, tracers ----------
    @SubscribeEvent
    public void render(RenderWorldLastEvent e) {
        if (mc.player == null || mc.world == null) return;
        boolean xray = on("XRay"), esp = on("ESP"), tr = on("Tracers");
        if (!xray && !esp && !tr) return;

        float pt = e.getPartialTicks();
        MatrixStack ms = e.getMatrixStack();
        Vector3d cam = mc.gameRenderer.getActiveRenderInfo().getProjectedView();
        ms.push();
        ms.translate(-cam.x, -cam.y, -cam.z);
        RenderSystem.disableDepthTest();
        RenderSystem.disableTexture();
        RenderSystem.lineWidth(2f);

        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(1, DefaultVertexFormats.POSITION_COLOR);
        Matrix4f m = ms.getLast().getMatrix();

        if (xray) {
            for (BlockPos bp : ores) {
                float[] col = ORES.get(mc.world.getBlockState(bp).getBlock());
                if (col == null) continue;
                WorldRenderer.drawBoundingBox(ms, b, new AxisAlignedBB(bp), col[0], col[1], col[2], 1f);
            }
        }
        if (esp || tr) {
            Vector3d start = cam.add(mc.player.getLook(pt));
            for (AbstractClientPlayerEntity o : mc.world.getPlayers()) {
                if (o == mc.player) continue;
                String n = o.getName().getString().toLowerCase();
                float[] col = isFriend(n) ? new float[]{0f, 1f, 0f}
                        : Cfg.enemies.contains(n) ? new float[]{1f, 0f, 0f} : new float[]{1f, 1f, 1f};
                double ix = o.lastTickPosX + (o.getPosX() - o.lastTickPosX) * pt;
                double iy = o.lastTickPosY + (o.getPosY() - o.lastTickPosY) * pt;
                double iz = o.lastTickPosZ + (o.getPosZ() - o.lastTickPosZ) * pt;
                if (esp) {
                    AxisAlignedBB bb = o.getBoundingBox().offset(ix - o.getPosX(), iy - o.getPosY(), iz - o.getPosZ());
                    WorldRenderer.drawBoundingBox(ms, b, bb, col[0], col[1], col[2], 1f);
                }
                if (tr) {
                    v(b, m, (float) start.x, (float) start.y, (float) start.z, col);
                    v(b, m, (float) ix, (float) (iy + o.getHeight() / 2), (float) iz, col);
                }
            }
        }
        t.draw();

        RenderSystem.enableTexture();
        RenderSystem.enableDepthTest();
        ms.pop();
    }

    // ---------- HUD ----------
    @SubscribeEvent
    public void hud(RenderGameOverlayEvent.Post e) {
        if (e.getType() != RenderGameOverlayEvent.ElementType.TEXT) return;
        frames++;
        long now = System.currentTimeMillis();
        if (now - fpsT >= 1000) { fps = frames; frames = 0; fpsT = now; }
        if (mc.gameSettings.hideGUI || mc.currentScreen instanceof Hud.Editor) return;
        Hud.renderAll(e.getMatrixStack());
    }

    // ---------- input ----------
    @SubscribeEvent
    public void mouse(InputEvent.MouseInputEvent e) {
        if (e.getAction() != GLFW.GLFW_PRESS) return;
        if (e.getButton() == 0) LC.addLast(System.currentTimeMillis());
        else if (e.getButton() == 1) RC.addLast(System.currentTimeMillis());
    }

    @SubscribeEvent
    public void key(InputEvent.KeyInputEvent e) {
        if (e.getAction() != GLFW.GLFW_PRESS || mc.currentScreen != null || mc.player == null) return;
        if (e.getKey() == GLFW.GLFW_KEY_RIGHT_SHIFT) { mc.displayGuiScreen(new Menu()); return; }
        for (Cfg.Mod m : Cfg.MODS.values())
            if (m.key == e.getKey()) toggle(m);
    }

    static int parseKey(String s) {
        s = s.toUpperCase();
        if (s.equals("NONE")) return -1;
        if (s.length() == 1) return s.charAt(0);
        if (s.matches("F\\d{1,2}")) return GLFW.GLFW_KEY_F1 + Integer.parseInt(s.substring(1)) - 1;
        switch (s) {
            case "INSERT": return GLFW.GLFW_KEY_INSERT;
            case "HOME": return GLFW.GLFW_KEY_HOME;
            case "END": return GLFW.GLFW_KEY_END;
            case "DELETE": return GLFW.GLFW_KEY_DELETE;
            case "LSHIFT": return GLFW.GLFW_KEY_LEFT_SHIFT;
            case "RCTRL": return GLFW.GLFW_KEY_RIGHT_CONTROL;
            case "ALT": return GLFW.GLFW_KEY_LEFT_ALT;
            default: return -2;
        }
    }

    // ---------- chat commands ----------
    @SubscribeEvent
    public void chat(ClientChatEvent e) {
        String m = e.getMessage();
        if (!m.startsWith(".")) return;
        e.setCanceled(true);
        mc.ingameGUI.getChatGUI().addToSentMessages(m);
        String[] a = m.substring(1).trim().split("\\s+");
        String cmd = a[0].toLowerCase();

        if (cmd.equals("save")) { Cfg.save(); msg("Saved."); return; }

        if (cmd.equals("bind") && a.length > 2) {
            Cfg.Mod md = Cfg.find(a[1]);
            int k = parseKey(a[2]);
            if (md == null) { msg("No such module: " + a[1]); return; }
            if (k == -2) { msg("Unknown key. Use A-Z, 0-9, F1-F12, INSERT, HOME, END, DELETE, LSHIFT, RCTRL, ALT or none"); return; }
            md.key = k;
            Cfg.save();
            msg(md.name + (k < 0 ? " unbound" : " bound to " + a[2].toUpperCase()));
            return;
        }

        if ((cmd.equals("sky") || cmd.equals("particles") || cmd.equals("fog")) && a.length > 1) {
            try {
                int rgb = Integer.parseInt(a[1].replace("#", ""), 16) & 0xFFFFFF;
                if (cmd.equals("sky")) { Cfg.skyIdx = -1; Cfg.skyRGB = rgb; }
                else if (cmd.equals("fog")) { Cfg.fogIdx = -1; Cfg.fogRGB = rgb; }
                else { Cfg.partIdx = -1; Cfg.partRGB = rgb; }
                Cfg.save();
                msg(cmd + " colour set to #" + a[1].replace("#", ""));
            } catch (NumberFormatException ex) {
                msg("Use: ." + cmd + " RRGGBB   (example: ." + cmd + " ff00aa)");
            }
            return;
        }

        if (cmd.equals("autobuy") && a.length > 1) {
            if (a[1].equalsIgnoreCase("item") && a.length > 2) {
                Cfg.buyItem = m.substring(m.indexOf(a[2])).toLowerCase();
                msg("AutoBuy item: " + Cfg.buyItem);
            } else if (a[1].equalsIgnoreCase("price") && a.length > 2) {
                try { Cfg.buyMax = a[2].equalsIgnoreCase("off") ? -1 : Long.parseLong(a[2]); msg("AutoBuy max price: " + (Cfg.buyMax < 0 ? "off" : Cfg.buyMax)); }
                catch (NumberFormatException ex) { msg("Use: .autobuy price 5000   or   .autobuy price off"); }
            }
            Cfg.save();
            return;
        }

        Set<String> list = (cmd.equals("friend") || cmd.equals("team")) ? Cfg.friends : cmd.equals("enemy") ? Cfg.enemies : null;
        if (list == null || a.length < 2) {
            msg(".friend|.enemy add|del|list <nick>   .bind <module> <key>   .sky/.particles/.fog RRGGBB   .autobuy item|price   .save");
            return;
        }
        switch (a[1].toLowerCase()) {
            case "add":
                if (a.length > 2) { list.add(a[2].toLowerCase()); msg(cmd + ": added " + a[2]); }
                break;
            case "del":
                if (a.length > 2) { list.remove(a[2].toLowerCase()); msg(cmd + ": removed " + a[2]); }
                break;
            case "list":
                msg(cmd + ": " + list);
                break;
            default:
                msg(".friend|.enemy add|del|list <nick>");
        }
        Cfg.save();
    }
}
