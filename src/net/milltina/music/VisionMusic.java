package net.milltina.music;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

public final class VisionMusic {
    private static final double DISTANCE = 2.3D;

    private static final double UNITS = MusicView.DEFAULT_W / 2.8D;
    private static final double BAR = 0.08D;

    private static final double GRIP = 0.15D;
    private static final int LEFT = 1;
    private static final int RIGHT = 2;
    private static final int BOTTOM = 4;
    private static final String SAVE_FILE = "card3d.txt";

    private static final int TEXTURE_STEP = 256;
    private static final double PIXEL_BUDGET = 6000000.0D;
    private static final int MAX_PIXELS = 4096;
    private static final double MAX_TILT = Math.toRadians(60.0D);
    private static final double MIN_DISTANCE = 1.0D;
    private static final double MAX_DISTANCE = 8.0D;
    private static final double WHEEL_STEP = 1.15D;
    private static final float OPEN_SECONDS = 0.25F;
    private static final float CLOSE_SECONDS = 0.16F;
    private static final int GL_DEPTH_COMPONENT24 = 0x81A6;

    private static boolean active;
    private static boolean closing;
    private static long changedAt;
    private static World world;

    private static double topX;
    private static double topY;
    private static double topZ;
    private static double rightX;
    private static double rightZ;
    private static double upX;
    private static double upY;
    private static double upZ;
    private static double normalX;
    private static double normalY;
    private static double normalZ;
    private static double viewDistance = DISTANCE;

    private static int viewW = MusicView.DEFAULT_W;
    private static int viewH = MusicView.DEFAULT_H;
    private static boolean sizeLoaded;
    private static int grip;
    private static int stretching;

    private static float mouseX = -1000.0F;
    private static float mouseY = -1000.0F;
    private static boolean onCard;
    private static boolean onBar;
    private static boolean barHover;
    private static boolean dragging;
    private static double grabAcross;
    private static double grabDown;
    private static double grabDistance;
    private static boolean pressedOnCard;

    private static int framebuffer = -1;
    private static int depthBuffer = -1;
    private static int texture = -1;
    private static int framebufferWidth;
    private static int framebufferHeight;

    private static int paintWidth = 1;
    private static int paintHeight = 1;

    private VisionMusic() {
    }

    private static double cardW() {
        return viewW / UNITS;
    }

    private static double cardH() {
        return viewH / UNITS;
    }

    static boolean isActive() {
        return active;
    }

    public static boolean shownInWorld() {
        return active;
    }

    public static boolean usable() {
        return active && !closing;
    }

    static boolean open(Minecraft mc, String search) {
        if (mc.thePlayer == null || mc.theWorld == null || MusicMod.shadersOn() || !OpenGlHelper.framebufferSupported) {
            return false;
        }
        mc.displayGuiScreen(null);
        if (!sizeLoaded) {
            sizeLoaded = true;
            loadSize();
        }
        MusicView view = GuiMusic.view();
        view.size(viewW, viewH);
        view.opened();
        if (search != null) {
            view.searchFor(search);
        }
        world = mc.theWorld;
        if (!active || closing) {
            place(mc);
            changedAt = System.nanoTime();
        }
        active = true;
        closing = false;
        dragging = false;
        stretching = 0;
        return true;
    }

    static void close() {
        if (!active || closing) {
            return;
        }
        GuiMusic.view().release(0);
        closing = true;
        dragging = false;
        if (stretching != 0) {
            stretching = 0;
            saveSize();
        }
        changedAt = System.nanoTime();
        Keyboard.enableRepeatEvents(false);
    }

    private static float shown() {
        float seconds = (System.nanoTime() - changedAt) / 1.0E9F;
        return closing ? Math.max(0.0F, 1.0F - seconds / CLOSE_SECONDS) : Math.min(1.0F, seconds / OPEN_SECONDS);
    }

    private static void place(Minecraft mc) {
        Vec3 look = mc.thePlayer.getLook(1.0F);
        face(look);
        double middleX = RenderManager.renderPosX + look.xCoord * DISTANCE;
        double middleY = RenderManager.renderPosY + look.yCoord * DISTANCE;
        double middleZ = RenderManager.renderPosZ + look.zCoord * DISTANCE;
        topX = middleX + upX * cardH() / 2.0D;
        topY = middleY + upY * cardH() / 2.0D;
        topZ = middleZ + upZ * cardH() / 2.0D;
        viewDistance = DISTANCE;
    }

    private static void face(Vec3 look) {
        double yaw = Math.atan2(-look.xCoord, look.zCoord);
        double tilt = Math.max(-MAX_TILT, Math.min(MAX_TILT, Math.asin(Math.max(-1.0D, Math.min(1.0D, -look.yCoord)))));
        rightX = -Math.cos(yaw);
        rightZ = -Math.sin(yaw);
        upX = -Math.sin(yaw) * Math.sin(tilt);
        upY = Math.cos(tilt);
        upZ = Math.cos(yaw) * Math.sin(tilt);
        normalX = -Math.sin(yaw) * Math.cos(tilt);
        normalY = -Math.sin(tilt);
        normalZ = Math.cos(yaw) * Math.cos(tilt);
    }

    private static void follow(Vec3 look) {
        face(look);
        double x = RenderManager.renderPosX + look.xCoord * grabDistance;
        double y = RenderManager.renderPosY + look.yCoord * grabDistance;
        double z = RenderManager.renderPosZ + look.zCoord * grabDistance;
        topX = x - rightX * grabAcross + upX * grabDown;
        topY = y + upY * grabDown;
        topZ = z - rightZ * grabAcross + upZ * grabDown;
    }

    private static double[] hit(Vec3 look) {
        double denominator = look.xCoord * normalX + look.yCoord * normalY + look.zCoord * normalZ;
        if (Math.abs(denominator) < 1.0E-4D) {
            return null;
        }
        double ox = RenderManager.renderPosX;
        double oy = RenderManager.renderPosY;
        double oz = RenderManager.renderPosZ;
        double t = ((topX - ox) * normalX + (topY - oy) * normalY + (topZ - oz) * normalZ) / denominator;
        if (t <= 0.0D) {
            return null;
        }
        double dx = ox + look.xCoord * t - topX;
        double dy = oy + look.yCoord * t - topY;
        double dz = oz + look.zCoord * t - topZ;
        return new double[] {dx * rightX + dz * rightZ, -(dx * upX + dy * upY + dz * upZ), t};
    }

    private static void aim(Vec3 look) {
        onCard = false;
        onBar = false;
        grip = 0;
        reachDistance = Double.MAX_VALUE;
        double[] spot = hit(look);
        if (spot == null || stretching != 0) {
            mouseX = -1000.0F;
            mouseY = -1000.0F;
            return;
        }
        double half = cardW() / 2.0D;
        double height = cardH();
        mouseX = (float) ((spot[0] + half) * UNITS);
        mouseY = (float) (spot[1] * UNITS);
        boolean across = Math.abs(spot[0]) <= half;
        boolean card = across && spot[1] >= 0.0D && spot[1] < height;
        boolean bar = across && spot[1] >= -BAR - 0.04D && spot[1] < 0.0D;
        int edges = 0;

        if (!card && !bar && !dragging && !pressedOnCard && spot[1] >= -BAR && spot[1] < height + GRIP) {
            if (spot[0] < -half && spot[0] >= -half - GRIP) {
                edges |= LEFT;
            } else if (spot[0] > half && spot[0] <= half + GRIP) {
                edges |= RIGHT;
            }
            if (spot[1] >= height && Math.abs(spot[0]) <= half + GRIP) {
                edges |= BOTTOM;
            }
        }
        if (card || bar || edges != 0) {
            reachDistance = spot[2];

            if (MusicMod.pointerElsewhere != null && MusicMod.pointerElsewhere.get(spot[2])) {
                mouseX = -1000.0F;
                mouseY = -1000.0F;
                return;
            }
        }
        onCard = card;
        onBar = bar;
        grip = edges;
        if (card || bar) {
            grabDistance = spot[2];
        }
    }

    private static double reachDistance = Double.MAX_VALUE;

    public static double reach() {
        return closing ? Double.MAX_VALUE : reachDistance;
    }

    public static boolean holding() {
        return !closing && (dragging || pressedOnCard || stretching != 0);
    }

    public static boolean typing() {
        return active && !closing && GuiMusic.view().typing();
    }

    public static void blur() {
        if (active && !closing && !pressedOnCard) {
            GuiMusic.view().press(-1.0F, -1.0F, 0);
        }
    }

    public static boolean crosses(double ax, double ay, double az, double dx, double dy, double dz, double margin) {
        double half = cardW() / 2.0D + margin;
        return MusicMod.segmentHits(ax, ay, az, dx, dy, dz, topX, topY, topZ, rightX, rightZ, upX, upY, upZ,
                normalX, normalY, normalZ, -half, half, -BAR - margin, cardH() + margin);
    }

    public static double depth() {
        double x = topX - upX * cardH() / 2.0D - RenderManager.renderPosX;
        double y = topY - upY * cardH() / 2.0D - RenderManager.renderPosY;
        double z = topZ - upZ * cardH() / 2.0D - RenderManager.renderPosZ;
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static void stretch(Vec3 look) {
        double[] spot = hit(look);
        if (spot == null) {
            return;
        }
        double left = -cardW() / 2.0D;
        double right = cardW() / 2.0D;
        int w = viewW;
        if ((stretching & LEFT) != 0) {
            w = clampW((int) Math.round((right - spot[0]) * UNITS));
            left = right - w / UNITS;
        } else if ((stretching & RIGHT) != 0) {
            w = clampW((int) Math.round((spot[0] - left) * UNITS));
            right = left + w / UNITS;
        }
        if ((stretching & BOTTOM) != 0) {
            viewH = Math.max(MusicView.MIN_H, Math.min(MusicView.MAX_H, (int) Math.round(spot[1] * UNITS)));
        }
        double shift = (left + right) / 2.0D;
        topX += rightX * shift;
        topZ += rightZ * shift;
        viewW = w;
    }

    private static int clampW(int w) {
        return Math.max(MusicView.MIN_W, Math.min(MusicView.MAX_W, w));
    }

    private static void loadSize() {
        File file = saveFile();
        if (!file.isFile()) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            String[] parts = (lines.isEmpty() ? "" : lines.get(0)).trim().split("\\s+");
            if (parts.length == 2) {
                viewW = clampW(Integer.parseInt(parts[0]));
                viewH = Math.max(MusicView.MIN_H, Math.min(MusicView.MAX_H, Integer.parseInt(parts[1])));
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("[Music] Reading " + SAVE_FILE + " failed: " + e);
        }
    }

    private static void saveSize() {
        File file = saveFile();
        try {
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), Collections.singletonList(viewW + " " + viewH), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Music] Saving " + SAVE_FILE + " failed: " + e);
        }
    }

    private static File saveFile() {
        return new File(new File(Minecraft.getMinecraft().mcDataDir, "music"), SAVE_FILE);
    }

    public static boolean takeMouse(Minecraft mc) {
        if (!active || closing || mc.currentScreen != null || !mc.inGameHasFocus) {
            return false;
        }
        MusicView view = GuiMusic.view();
        int wheel = Mouse.getEventDWheel();
        boolean taken = false;
        if (wheel != 0) {
            if (dragging) {
                grabDistance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE,
                        grabDistance * (wheel > 0 ? WHEEL_STEP : 1.0D / WHEEL_STEP)));
                taken = true;
            } else if (stretching != 0) {
                taken = true;
            } else if (onCard) {
                view.wheel(mouseX, mouseY, wheel);
                taken = true;
            }
        }
        int button = Mouse.getEventButton();
        if (button < 0) {
            return taken;
        }
        if (button - 100 == MusicSettings.playerKey) {
            return taken;
        }
        boolean down = Mouse.getEventButtonState();
        if (down) {
            if (button == 0 && onBar) {
                dragging = true;
                double[] spot = hit(mc.thePlayer.getLook(1.0F));
                grabAcross = spot == null ? 0.0D : spot[0];
                grabDown = spot == null ? -BAR / 2.0D : spot[1];
                return true;
            }
            if (button == 0 && grip != 0) {
                stretching = grip;
                return true;
            }
            if (onCard) {
                pressedOnCard = true;
                view.press(mouseX, mouseY, button);
                return true;
            }

            view.press(-1.0F, -1.0F, button);
            return taken;
        }
        if (button == 0 && dragging) {
            dragging = false;
            measureDistance();
            return true;
        }
        if (button == 0 && stretching != 0) {
            stretching = 0;
            measureDistance();
            saveSize();
            return true;
        }
        if (pressedOnCard) {
            pressedOnCard = false;
            view.release(button);
            return true;
        }
        return taken;
    }

    public static boolean takeKey(Minecraft mc) {
        if (!active || closing || mc.currentScreen != null) {
            return false;
        }
        MusicView view = GuiMusic.view();
        int key = Keyboard.getEventKey();
        boolean down = Keyboard.getEventKeyState();
        if (down && !Keyboard.isRepeatEvent() && !view.typing()
                && (key == Keyboard.KEY_ESCAPE || key != Keyboard.KEY_NONE && key == MusicSettings.playerKey)) {
            if (key != Keyboard.KEY_ESCAPE || !view.back()) {
                close();
            }
            return true;
        }
        if (!view.typing()) {
            return false;
        }
        if (down) {
            view.key(Keyboard.getEventCharacter(), key);
        }
        return true;
    }

    private static void measureDistance() {
        double x = topX - upX * cardH() / 2.0D - RenderManager.renderPosX;
        double y = topY - upY * cardH() / 2.0D - RenderManager.renderPosY;
        double z = topZ - upZ * cardH() / 2.0D - RenderManager.renderPosZ;
        viewDistance = Math.max(MIN_DISTANCE, Math.sqrt(x * x + y * y + z * z));
    }

    public static void renderInWorld(EntityRenderer renderer, float partialTicks) {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld != world || MusicMod.shadersOn()) {
            active = false;
            return;
        }
        float t = shown();
        if (closing && t <= 0.0F) {
            active = false;
            closing = false;
            return;
        }
        Music.frame(mc);
        Vec3 look = mc.thePlayer.getLook(partialTicks);
        if (dragging && mc.currentScreen == null) {
            follow(look);
        } else if (stretching != 0 && mc.currentScreen == null) {
            stretch(look);
        }
        if (mc.currentScreen == null && !closing) {
            aim(look);
        } else {
            onCard = false;
            onBar = false;
            grip = 0;
            reachDistance = Double.MAX_VALUE;
        }
        MusicView view = GuiMusic.view();

        view.size(viewW, viewH);
        Keyboard.enableRepeatEvents(view.typing());
        float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);

        paint(mc, view, onCard || pressedOnCard ? mouseX : -1000.0F, onCard || pressedOnCard ? mouseY : -1000.0F, eased);
        renderer.disableLightmap((double) partialTicks);
        draw(eased);
    }

    private static float density(Minecraft mc) {
        double fov = Math.toRadians(Math.max(30.0D, Math.min(110.0D, mc.gameSettings.fovSetting)));
        double perBlock = mc.displayHeight / 2.0D / (viewDistance * Math.tan(fov / 2.0D));
        double perUnit = Math.round(Math.max(2.0D, Math.min(4.0D, perBlock / UNITS * 1.3D)) * 2.0D) / 2.0D;
        double budget = Math.min(Math.sqrt(PIXEL_BUDGET / ((double) viewW * viewH)),
                (double) MAX_PIXELS / Math.max(viewW, viewH));
        return (float) Math.min(perUnit, Math.floor(budget * 2.0D) / 2.0D);
    }

    private static void paint(Minecraft mc, MusicView view, float mx, float my, float alpha) {
        float density = density(mc);
        paintWidth = Math.round(viewW * density);
        paintHeight = Math.round(viewH * density);
        int textureWidth = (paintWidth + TEXTURE_STEP - 1) / TEXTURE_STEP * TEXTURE_STEP;
        int textureHeight = (paintHeight + TEXTURE_STEP - 1) / TEXTURE_STEP * TEXTURE_STEP;

        if (framebuffer < 0 || paintWidth > framebufferWidth || paintHeight > framebufferHeight
                || stretching == 0 && (textureWidth != framebufferWidth || textureHeight != framebufferHeight)) {
            createFramebuffer(textureWidth, textureHeight);
        }
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, viewW, viewH, 0.0D, 1000.0D, 3000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2000.0F);

        OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, framebuffer);
        GL11.glViewport(0, 0, paintWidth, paintHeight);
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        boolean maxAlpha = GLContext.getCapabilities().OpenGL20;
        if (maxAlpha) {
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_MAX);
        }

        view.screenBackdrop = false;

        MusicFont.pixels(paintWidth / (float) viewW, true);
        view.draw(mx, my, 1.0F);
        MusicFont.pixels(0.0F, false);
        if (maxAlpha) {
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        }
        if (OpenGlHelper.isFramebufferEnabled()) {
            mc.getFramebuffer().bindFramebuffer(true);
        } else {
            OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, 0);
            GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        }
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    private static void draw(float alpha) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_LIGHTING);
        MusicMod.panelDepth();
        GL11.glEnable(GL11.GL_BLEND);

        double grow = 0.94D + 0.06D * alpha;
        double half = cardW() / 2.0D * grow;
        double top = (1.0D - grow) * cardH() / 2.0D + (1.0D - alpha) * 0.06D;
        double bottom = top + cardH() * grow;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        barHover = onBar || dragging;
        float barAlpha = (barHover ? 0.85F : 0.45F) * alpha;
        GL11.glColor4f(barHover ? 0.85F : 0.55F, barHover ? 0.87F : 0.58F, barHover ? 0.9F : 0.62F, barAlpha);
        double barWidth = barHover ? 0.5D : 0.35D;
        quad(-barWidth, top - BAR, barWidth, top - BAR + 0.025D, false);

        int edges = stretching != 0 ? stretching : grip;
        if (edges != 0) {
            double line = 0.03D;
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.75F * alpha);
            if ((edges & LEFT) != 0) {
                quad(-half - line, top, -half, bottom, false);
            }
            if ((edges & RIGHT) != 0) {
                quad(half, top, half + line, bottom, false);
            }
            if ((edges & BOTTOM) != 0) {
                quad(-half, bottom, half, bottom + line, false);
            }
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);

        GL11.glColor4f(alpha, alpha, alpha, alpha);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        quad(-half, top, half, bottom, true);
        GL11.glPopAttrib();
    }

    private static void quad(double left, double top, double right, double bottom, boolean textured) {
        double u = textured ? paintWidth / (double) framebufferWidth : 1.0D;
        double v = textured ? paintHeight / (double) framebufferHeight : 1.0D;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        corner(tessellator, left, top, 0.0D, v);
        corner(tessellator, left, bottom, 0.0D, 0.0D);
        corner(tessellator, right, bottom, u, 0.0D);
        corner(tessellator, right, top, u, v);
        tessellator.draw();
    }

    private static void corner(Tessellator tessellator, double across, double down, double u, double v) {
        double x = topX - RenderManager.renderPosX + rightX * across - upX * down;
        double y = topY - RenderManager.renderPosY - upY * down;
        double z = topZ - RenderManager.renderPosZ + rightZ * across - upZ * down;
        tessellator.addVertexWithUV(x, y, z, u, v);
    }

    private static void createFramebuffer(int width, int height) {
        if (framebuffer >= 0) {
            OpenGlHelper.func_153174_h(framebuffer);
            OpenGlHelper.func_153184_g(depthBuffer);
            TextureUtil.deleteTexture(texture);
        }
        framebufferWidth = width;
        framebufferHeight = height;
        texture = TextureUtil.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        depthBuffer = OpenGlHelper.func_153185_f();
        OpenGlHelper.func_153176_h(OpenGlHelper.field_153199_f, depthBuffer);
        OpenGlHelper.func_153186_a(OpenGlHelper.field_153199_f, GL_DEPTH_COMPONENT24, width, height);
        framebuffer = OpenGlHelper.func_153165_e();
        OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, framebuffer);
        OpenGlHelper.func_153188_a(OpenGlHelper.field_153198_e, OpenGlHelper.field_153200_g, GL11.GL_TEXTURE_2D, texture, 0);
        OpenGlHelper.func_153190_b(OpenGlHelper.field_153198_e, OpenGlHelper.field_153201_h,
                OpenGlHelper.field_153199_f, depthBuffer);
    }
}
