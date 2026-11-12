package net.milltina.music;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public final class MusicMod {
    public interface Backdrop {
        void draw(Minecraft mc, int width, int height);
    }

    public interface PointerCheck {
        boolean get(double distance);
    }

    public static Backdrop backdrop;
    public static PointerCheck pointerElsewhere;

    private static final double LABEL_MARGIN = 0.5D;
    private static Method shadersMethod;
    private static boolean shadersLooked;

    private MusicMod() {
    }

    public static void open(Minecraft mc, String search) {
        MusicSettings.all();
        MusicCommand.open(mc, search);
    }

    public static boolean onChat(Minecraft mc, String message) {
        MusicSettings.all();
        return MusicCommand.handle(mc, message);
    }

    public static void onKey(Minecraft mc, int key) {
        MusicSettings.all();
        MusicCommand.onKey(mc, key);
    }

    public static boolean takeMouse(Minecraft mc) {
        if (!VisionMusic.usable()) {
            return false;
        }
        int button = Mouse.getEventButton();
        boolean down = Mouse.getEventButtonState();
        if (button >= 0 && !down) {
            KeyBinding.setKeyBindState(button - 100, false);
        }
        boolean aimed = aimed();
        if (aimed && button == 0 && down && mc.thePlayer != null && mc.currentScreen == null) {
            mc.thePlayer.swingItem();
        }
        boolean taken = VisionMusic.takeMouse(mc);
        if (aimed) {
            return taken;
        }
        return taken || button >= 0 && mc.currentScreen == null && mc.inGameHasFocus;
    }

    public static boolean takeKey(Minecraft mc) {
        if (!VisionMusic.usable() || mc.currentScreen != null) {
            return false;
        }
        if (Keyboard.getEventKey() == Keyboard.KEY_ESCAPE && !VisionMusic.typing()) {
            if (!Keyboard.getEventKeyState() || !aimed()) {
                return false;
            }
            if (Keyboard.isRepeatEvent()) {
                return true;
            }
        }
        return VisionMusic.takeKey(mc);
    }

    private static boolean aimed() {
        return VisionMusic.holding() || VisionMusic.reach() < Double.MAX_VALUE;
    }

    public static void renderWorld(EntityRenderer renderer, float partialTicks) {
        if (!VisionMusic.shownInWorld()) {
            return;
        }
        EntityLivingBase eye = Minecraft.getMinecraft().renderViewEntity;
        if (eye != null) {
            RenderManager.renderPosX = eye.lastTickPosX + (eye.posX - eye.lastTickPosX) * partialTicks;
            RenderManager.renderPosY = eye.lastTickPosY + (eye.posY - eye.lastTickPosY) * partialTicks;
            RenderManager.renderPosZ = eye.lastTickPosZ + (eye.posZ - eye.lastTickPosZ) * partialTicks;
        }
        VisionMusic.renderInWorld(renderer, partialTicks);
    }

    public static void renderHud(Minecraft mc, ScaledResolution resolution) {
        MusicSettings.all();
        MusicHud.render(mc, resolution);
    }

    public static boolean chatPress(int mouseX, int mouseY) {
        return MusicHud.startDrag(mouseX, mouseY);
    }

    public static void chatDrag(Minecraft mc, int mouseX, int mouseY) {
        MusicHud.chatDrag(mc, mouseX, mouseY);
    }

    public static void chatRelease() {
        MusicHud.endDrag();
    }

    public static void leftWorld() {
        Music.leftWorld();
    }

    public static void beforeSoundUnload() {
        Music.beforeSoundUnload();
    }

    public static boolean isOpen() {
        return VisionMusic.usable() || Minecraft.getMinecraft().currentScreen instanceof GuiMusic;
    }

    public static double depth() {
        return VisionMusic.depth();
    }

    public static boolean coversLabel(double x, double y, double z) {
        if (!VisionMusic.shownInWorld()) {
            return false;
        }
        double ax = RenderManager.renderPosX + ActiveRenderInfo.objectX;
        double ay = RenderManager.renderPosY + ActiveRenderInfo.objectY;
        double az = RenderManager.renderPosZ + ActiveRenderInfo.objectZ;
        return VisionMusic.crosses(ax, ay, az, RenderManager.renderPosX + x - ax, RenderManager.renderPosY + y - ay,
                RenderManager.renderPosZ + z - az, LABEL_MARGIN);
    }

    static boolean shadersOn() {
        if (!shadersLooked) {
            shadersLooked = true;
            String[] names = {"Config", "net.minecraft.src.Config", "net.optifine.Config"};
            for (String name : names) {
                try {
                    shadersMethod = Class.forName(name).getMethod("isShaders");
                    break;
                } catch (Throwable ignored) {
                }
            }
        }
        if (shadersMethod == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(shadersMethod.invoke(null));
        } catch (Throwable e) {
            return false;
        }
    }

    static void panelDepth() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_ALWAYS);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.004F);
    }

    static boolean segmentHits(double ax, double ay, double az, double dx, double dy, double dz,
            double topX, double topY, double topZ, double rightX, double rightZ, double upX, double upY, double upZ,
            double normalX, double normalY, double normalZ, double left, double right, double top, double bottom) {
        double denominator = dx * normalX + dy * normalY + dz * normalZ;
        if (Math.abs(denominator) < 1.0E-6D) {
            return false;
        }
        double t = ((topX - ax) * normalX + (topY - ay) * normalY + (topZ - az) * normalZ) / denominator;
        if (t <= 0.0D || t >= 1.0D) {
            return false;
        }
        double px = ax + dx * t - topX;
        double py = ay + dy * t - topY;
        double pz = az + dz * t - topZ;
        double across = px * rightX + pz * rightZ;
        double down = -(px * upX + py * upY + pz * upZ);
        return across >= left && across <= right && down >= top && down <= bottom;
    }
}
