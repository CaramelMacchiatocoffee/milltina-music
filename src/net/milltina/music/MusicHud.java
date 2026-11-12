package net.milltina.music;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public final class MusicHud {
    private static final float CARD_W = 150.0F;
    private static final float CARD_H = 32.0F;

    private static float posX = -1.0F;
    private static float posY = -1.0F;
    private static boolean loaded;
    private static boolean dragging;
    private static float grabX;
    private static float grabY;

    private static float boxX;
    private static float boxY;
    private static float boxW;
    private static float boxH;
    private static boolean boxShown;

    private static float shown;
    private static long lastFrame;
    private static int lyricIndex = -1;
    private static String lyricOld = "";
    private static String lyricNow = "";
    private static String transOld = "";
    private static String transNow = "";
    private static float lyricMove = 1.0F;
    private static int accent = 0xE5484D;

    private static float dark = -1.0F;
    private static int textColor = 0x16181C;
    private static int dimColor = 0x6E7580;

    private MusicHud() {
    }

    public static void render(Minecraft mc, ScaledResolution resolution) {
        Music.frame(mc);
        load(mc);
        Music music = Music.peek();
        Song song = music == null ? null : music.player.current();
        long now = System.nanoTime();
        float dt = lastFrame == 0L ? 0.016F : Math.min(0.1F, (now - lastFrame) / 1.0E9F);
        lastFrame = now;
        boolean want = MusicSettings.enabled && song != null && !(mc.currentScreen instanceof GuiMusic) && !VisionMusic.isActive()
                && !mc.gameSettings.showDebugInfo;
        shown += ((want ? 1.0F : 0.0F) - shown) * (1.0F - (float) Math.exp(-10.0F * dt));
        boxShown = shown > 0.05F;
        if (shown < 0.01F || song == null) {
            return;
        }
        MusicFont font = MusicFont.get();
        MusicPlayer player = music.player;
        float scale = (float) MusicSettings.slider("HUD Scale", 1.0D);
        boolean lyricsOn = MusicSettings.check("HUD Lyrics", true);
        boolean translation = MusicSettings.check("Translation", true);

        Lyrics lyrics = player.lyrics();
        int index = lyrics.indexAt(player.position() + 150L);
        if (index != lyricIndex) {
            lyricIndex = index;
            lyricOld = lyricNow;
            transOld = transNow;
            Lyrics.Line line = index >= 0 ? lyrics.lines.get(index) : null;
            lyricNow = line == null ? "" : line.text;
            transNow = line == null || line.translation == null ? "" : line.translation;
            lyricMove = 0.0F;
        }
        lyricMove = Math.min(1.0F, lyricMove + dt / 0.28F);
        boolean hasLyric = lyricsOn && !lyrics.lines.isEmpty();
        float lyricH = hasLyric ? (translation ? 22.0F : 14.0F) : 0.0F;

        float w = CARD_W;
        float h = CARD_H + lyricH;
        int sw = resolution.getScaledWidth();
        int sh = resolution.getScaledHeight();
        float x = posX < 0 ? sw - w * scale - 4 : posX * sw;
        float y = posY < 0 ? 4 : posY * sh;
        x = Math.max(0, Math.min(sw - w * scale, x));
        y = Math.max(0, Math.min(sh - h * scale, y));
        boxX = x;
        boxY = y;
        boxW = w * scale;
        boxH = h * scale;

        Music.Cover cover = music.cover(song.cover, 100);
        int target = cover == null ? accent : cover.accent;
        accent = mix(accent, target, 1.0F - (float) Math.exp(-4.0F * dt));
        float a = 1.0F - (1.0F - shown) * (1.0F - shown);

        GL11.glPushMatrix();
        float f = resolution.getScaleFactor();
        boolean still = a >= 0.999F;
        if (still) {
            GL11.glTranslatef(Math.round(x * f) / f, Math.round(y * f) / f, 0.0F);
        } else {
            GL11.glTranslatef(x + (1.0F - a) * 6.0F, y, 0.0F);
        }
        GL11.glScalef(scale, scale, 1.0F);
        MusicFont.pixels(scale * f, still);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float darkTarget = MusicSettings.check("Dark Mode", false) ? 1.0F : 0.0F;
        dark = dark < 0.0F ? darkTarget : dark + (darkTarget - dark) * (1.0F - (float) Math.exp(-9.0F * dt));
        int bg = mix(MusicView.LIGHT[0], MusicView.DARK[0], dark);
        int ink = mix(MusicView.LIGHT[6], MusicView.DARK[6], dark);
        int tile = mix(MusicView.LIGHT[7], MusicView.DARK[7], dark);
        textColor = mix(MusicView.LIGHT[2], MusicView.DARK[2], dark);
        dimColor = mix(MusicView.LIGHT[3], MusicView.DARK[3], dark);

        rect(0, 0, w, h, bg, (int) (235 * a));
        rect(5, 4, 29, 28, tile, (int) (255 * a));
        if (cover != null) {
            image(cover.texture, 5, 4, 24, a);
        }
        int ta = (int) (255 * a);
        font.draw(font.trim(song.name, w - 40, 0.85F), 34, 5, ta << 24 | textColor, 0.85F);
        String sub = player.paused() ? "Paused" : player.status().isEmpty() || player.status().equals("Loading")
                ? song.artists : player.status();
        font.draw(font.trim(sub, w - 40, 0.7F), 34, 16, ta << 24 | dimColor, 0.7F);
        float progress = song.durationMs <= 0 ? 0 : player.position() / (float) song.durationMs;
        rect(34, 26, w - 6, 27, ink, (int) (28 * a));
        rect(34, 26, 34 + (w - 40) * Math.min(1.0F, progress), 27, accent, ta);

        if (hasLyric) {
            float t = 1.0F - (1.0F - lyricMove) * (1.0F - lyricMove) * (1.0F - lyricMove);
            float base = CARD_H + 1;
            rect(0, CARD_H, w, CARD_H + 0.5F, ink, (int) (16 * a));
            if (t < 1.0F && !lyricOld.isEmpty()) {
                lyricLine(font, lyricOld, transOld, w, base - t * 6.0F, a * (1.0F - t), translation);
            }
            lyricLine(font, lyricNow, transNow, w, base + (1.0F - t) * 6.0F, a * t, translation);
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
        MusicFont.pixels(0.0F, false);
    }

    private static void lyricLine(MusicFont font, String text, String trans, float w, float y, float a, boolean translation) {
        int alpha = (int) (255 * a);
        if (alpha < 4 || text.isEmpty()) {
            return;
        }
        String line = font.trim(text, w - 12, 0.82F);
        font.draw(line, (w - font.width(line, 0.82F)) / 2, y + 2, alpha << 24 | textColor, 0.82F);
        if (translation && !trans.isEmpty()) {
            String t = font.trim(trans, w - 12, 0.68F);
            font.draw(t, (w - font.width(t, 0.68F)) / 2, y + 12, (int) (alpha * 0.8F) << 24 | dimColor, 0.68F);
        }
    }

    public static boolean startDrag(int mouseX, int mouseY) {
        if (!boxShown || mouseX < boxX || mouseX >= boxX + boxW || mouseY < boxY || mouseY >= boxY + boxH) {
            return false;
        }
        dragging = true;
        grabX = mouseX - boxX;
        grabY = mouseY - boxY;
        return true;
    }

    public static void chatDrag(Minecraft mc, int mouseX, int mouseY) {
        if (!dragging) {
            return;
        }
        if (!Mouse.isButtonDown(0)) {
            endDrag();
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        posX = Math.max(0.0F, (mouseX - grabX) / resolution.getScaledWidth());
        posY = Math.max(0.0F, (mouseY - grabY) / resolution.getScaledHeight());
    }

    public static void endDrag() {
        if (dragging) {
            dragging = false;
            save();
        }
    }

    static void resetPosition() {
        posX = -1.0F;
        posY = -1.0F;
        save();
    }

    private static File file() {
        return new File(new File(Minecraft.getMinecraft().mcDataDir, "music"), "hud.properties");
    }

    private static void load(Minecraft mc) {
        if (loaded) {
            return;
        }
        loaded = true;
        File file = file();
        if (!file.isFile()) {
            return;
        }
        Properties properties = new Properties();
        try {
            InputStream in = new FileInputStream(file);
            try {
                properties.load(in);
            } finally {
                in.close();
            }
            posX = Float.parseFloat(properties.getProperty("x", "-1"));
            posY = Float.parseFloat(properties.getProperty("y", "-1"));
        } catch (Exception ignored) {
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("x", String.valueOf(posX));
        properties.setProperty("y", String.valueOf(posY));
        try {
            File file = file();
            file.getParentFile().mkdirs();
            OutputStream out = new FileOutputStream(file);
            try {
                properties.store(out, "Music HUD place, share of the screen; -1 is the default");
            } finally {
                out.close();
            }
        } catch (IOException ignored) {
        }
    }

    private static int mix(int a, int b, float t) {
        int r = (int) ((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t + 0.5F);
        int g = (int) ((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t + 0.5F);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t + 0.5F);
        return r << 16 | g << 8 | bl;
    }

    private static void rect(float x1, float y1, float x2, float y2, int rgb, int a) {
        if (a <= 0) {
            return;
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f((rgb >> 16 & 255) / 255.0F, (rgb >> 8 & 255) / 255.0F, (rgb & 255) / 255.0F, Math.min(255, a) / 255.0F);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertex(x1, y2, 0.0D);
        t.addVertex(x2, y2, 0.0D);
        t.addVertex(x2, y1, 0.0D);
        t.addVertex(x1, y1, 0.0D);
        t.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static void image(int texture, float x, float y, float size, float a) {
        if (texture <= 0) {
            return;
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, a);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x, y + size, 0.0D, 0.0D, 1.0D);
        t.addVertexWithUV(x + size, y + size, 0.0D, 1.0D, 1.0D);
        t.addVertexWithUV(x + size, y, 0.0D, 1.0D, 0.0D);
        t.addVertexWithUV(x, y, 0.0D, 0.0D, 0.0D);
        t.draw();
    }
}
