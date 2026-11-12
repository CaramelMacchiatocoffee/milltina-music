package net.milltina.music;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public final class MusicFont {
    public static final float SIZE = 8.0F;
    private static final int BAKE = 4;

    private static final int MAX_EXACT = 64;

    private static float ppu;
    private static boolean snap;

    public static void pixels(float pixelsPerUnit, boolean onGrid) {
        ppu = pixelsPerUnit;
        snap = onGrid && pixelsPerUnit > 0;
    }

    public static float ppu() {
        return ppu;
    }

    public static float snap(float x) {
        return snap ? Math.round(x * ppu) / ppu : x;
    }
    private static final int PAGE = 1024;

    private static final int BAKES_PER_FRAME = 48;

    private static MusicFont instance;

    private final Font[] fonts;
    private final FontMetrics[] metrics;
    private final BufferedImage scratch;
    private final Graphics2D scratchGraphics;
    private final int cell;
    private final int ascent;
    private final Map<Character, Glyph> glyphs = new HashMap<Character, Glyph>();

    private final Map<Integer, Glyph> sized = new HashMap<Integer, Glyph>();
    private final Map<Integer, Sized> sizes = new HashMap<Integer, Sized>();
    private final List<Integer> pages = new ArrayList<Integer>();
    private int penX = PAGE;
    private int penY;

    private int rowHeight;
    private int bakedThisFrame;
    private long frameMark;

    public static MusicFont get() {
        if (instance == null) {
            instance = new MusicFont();
        }
        return instance;
    }

    private MusicFont() {
        Font latin = load("/assets/milltinamusic/fonts/Lexend-SemiBold.ttf");
        Font cjk = load("/assets/milltinamusic/fonts/NotoSansSC-ExtraBold.ttf");
        Font system = new Font(Font.DIALOG, Font.BOLD, 1);
        float px = SIZE * BAKE;
        this.fonts = new Font[] {
            latin == null ? system.deriveFont(px) : latin.deriveFont(px),
            cjk == null ? system.deriveFont(px) : cjk.deriveFont(Font.PLAIN, px * 0.92F),
            system.deriveFont(px * 0.95F)};
        this.metrics = new FontMetrics[this.fonts.length];
        this.cell = (int) Math.ceil(px * 1.5F);
        int room = Math.max(this.cell, (int) Math.ceil(MAX_EXACT * 1.5F));
        this.scratch = new BufferedImage(room * 2, room, BufferedImage.TYPE_INT_ARGB);
        this.scratchGraphics = this.scratch.createGraphics();
        this.scratchGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        this.scratchGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        this.scratchGraphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        for (int i = 0; i < this.fonts.length; i++) {
            this.metrics[i] = this.scratchGraphics.getFontMetrics(this.fonts[i]);
        }
        this.ascent = this.metrics[0].getAscent();
    }

    private static Font load(String path) {
        try {
            InputStream in = MusicFont.class.getResourceAsStream(path);
            if (in == null) {
                return null;
            }
            try {
                return Font.createFont(Font.TRUETYPE_FONT, in);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    public float width(String text, float scale) {
        float width = 0.0F;
        for (int i = 0; i < text.length(); i++) {
            Glyph glyph = glyph(text.charAt(i), false);
            width += glyph == null ? SIZE * 0.5F : glyph.advance;
        }
        return width * scale;
    }

    float height(float scale) {
        return this.metrics[0].getHeight() / (float) BAKE * scale;
    }

    public void draw(String text, float x, float y, int color, float scale) {
        if (text == null || text.isEmpty() || (color >>> 24) < 3) {
            return;
        }
        long now = System.nanoTime();
        if (now - this.frameMark > 8000000L) {
            this.frameMark = now;
            this.bakedThisFrame = 0;
        }

        int px = ppu > 0 ? Math.round(SIZE * scale * ppu) : 0;
        Sized size = px >= 4 && px <= MAX_EXACT ? sized(px) : null;

        Glyph[] line = new Glyph[text.length()];
        Glyph[] exact = new Glyph[text.length()];
        for (int i = 0; i < text.length(); i++) {
            line[i] = glyph(text.charAt(i), true);
            if (size != null && line[i] != null) {
                exact[i] = exactGlyph(text.charAt(i), size);
            }
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F, (color >>> 24) / 255.0F);
        float unit = scale / BAKE;

        float top = y - (this.cell - this.metrics[0].getHeight()) / 2.0F * unit;
        float exactTop = 0;
        if (size != null) {
            float yPx = snap ? Math.round(y * ppu) : y * ppu;
            exactTop = (yPx - (size.cell - size.height) / 2) / ppu;
        }
        Tessellator tessellator = Tessellator.instance;
        int boundPage = -1;
        float pen = x;
        for (int i = 0; i < line.length; i++) {
            Glyph glyph = line[i];
            if (glyph == null) {
                pen += SIZE * 0.5F * scale;
                continue;
            }
            Glyph shown = exact[i] != null ? exact[i] : glyph;
            if (shown.width > 0) {
                if (shown.page != boundPage) {
                    if (boundPage >= 0) {
                        tessellator.draw();
                    }
                    boundPage = shown.page;
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.pages.get(boundPage).intValue());
                    tessellator.startDrawingQuads();
                }
                float left;
                float right;
                float glyphTop;
                float bottom;
                if (shown == glyph) {
                    left = pen + glyph.offset * unit;
                    right = left + glyph.width * unit;
                    glyphTop = top;
                    bottom = top + this.cell * unit;
                } else {
                    float penPx = snap ? Math.round(pen * ppu) : pen * ppu;
                    left = (penPx + shown.offset) / ppu;
                    right = left + shown.width / ppu;
                    glyphTop = exactTop;
                    bottom = exactTop + size.cell / ppu;
                }
                tessellator.addVertexWithUV(left, bottom, 0.0D, shown.u0, shown.v1);
                tessellator.addVertexWithUV(right, bottom, 0.0D, shown.u1, shown.v1);
                tessellator.addVertexWithUV(right, glyphTop, 0.0D, shown.u1, shown.v0);
                tessellator.addVertexWithUV(left, glyphTop, 0.0D, shown.u0, shown.v0);
            }
            pen += glyph.advance * scale;
        }
        if (boundPage >= 0) {
            tessellator.draw();
        }
    }

    private Sized sized(int px) {
        Sized size = this.sizes.get(Integer.valueOf(px));
        if (size == null) {
            size = new Sized();
            size.px = px;
            size.fonts = new Font[this.fonts.length];
            size.metrics = new FontMetrics[this.fonts.length];
            float master = SIZE * BAKE;
            for (int i = 0; i < this.fonts.length; i++) {
                size.fonts[i] = this.fonts[i].deriveFont(this.fonts[i].getSize2D() * px / master);
                size.metrics[i] = this.scratchGraphics.getFontMetrics(size.fonts[i]);
            }
            size.cell = (int) Math.ceil(px * 1.5F);
            size.height = size.metrics[0].getHeight();
            size.baseline = size.metrics[0].getAscent() + (size.cell - size.height) / 2;
            this.sizes.put(Integer.valueOf(px), size);
        }
        return size;
    }

    private Glyph exactGlyph(char c, Sized size) {
        Integer key = Integer.valueOf(size.px << 16 | c);
        Glyph glyph = this.sized.get(key);
        if (glyph != null || this.bakedThisFrame >= BAKES_PER_FRAME) {
            return glyph;
        }
        if (Character.isWhitespace(c)) {
            return null;
        }
        this.bakedThisFrame++;
        glyph = new Glyph();
        render(c, size.fonts, size.metrics, size.cell, size.baseline, glyph, true);
        this.sized.put(key, glyph);
        return glyph;
    }

    public String trim(String text, float width, float scale) {
        if (width(text, scale) <= width) {
            return text;
        }
        float dots = width("...", scale);
        int end = text.length();
        while (end > 0 && width(text.substring(0, end), scale) + dots > width) {
            end--;
        }
        return text.substring(0, end).trim() + "...";
    }

    private Glyph glyph(char c, boolean bake) {
        Glyph glyph = this.glyphs.get(Character.valueOf(c));
        if (glyph != null || !bake && this.glyphs.containsKey(Character.valueOf(c))) {
            return glyph;
        }
        if (!bake || this.bakedThisFrame >= BAKES_PER_FRAME) {
            return null;
        }
        this.bakedThisFrame++;
        glyph = bake(c);
        this.glyphs.put(Character.valueOf(c), glyph);
        return glyph;
    }

    private Glyph bake(char c) {
        int which = this.fonts.length - 1;
        for (int i = 0; i < this.fonts.length; i++) {
            if (this.fonts[i].canDisplay(c)) {
                which = i;
                break;
            }
        }
        FontMetrics fm = this.metrics[which];
        Glyph glyph = new Glyph();
        glyph.advance = fm.charWidth(c) / (float) BAKE;
        if (Character.isWhitespace(c)) {
            return glyph;
        }

        render(c, this.fonts, this.metrics, this.cell, this.ascent + (this.cell - this.metrics[0].getHeight()) / 2, glyph, false);
        return glyph;
    }

    private void render(char c, Font[] fonts, FontMetrics[] metrics, int cell, int baseline, Glyph glyph, boolean exact) {
        int which = fonts.length - 1;
        for (int i = 0; i < fonts.length; i++) {
            if (fonts[i].canDisplay(c)) {
                which = i;
                break;
            }
        }
        int width = Math.min(this.scratch.getWidth(), metrics[which].charWidth(c) + 4);
        Graphics2D g = this.scratchGraphics;
        g.setComposite(java.awt.AlphaComposite.Clear);
        g.fillRect(0, 0, this.scratch.getWidth(), this.scratch.getHeight());
        g.setComposite(java.awt.AlphaComposite.SrcOver);
        g.setFont(fonts[which]);
        g.setColor(Color.WHITE);
        if (exact) {
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        }
        g.drawString(String.valueOf(c), 2, baseline);
        if (exact) {
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        }
        if (this.penX + width > PAGE) {
            this.penX = 0;
            this.penY += this.rowHeight;
            this.rowHeight = 0;
        }
        if (this.pages.isEmpty() || this.penY + cell > PAGE) {
            newPage();
        }
        this.rowHeight = Math.max(this.rowHeight, cell);
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * cell * 4);
        for (int y = 0; y < cell; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = this.scratch.getRGB(x, y) >>> 24;
                if (exact) {
                    alpha = Math.max(0, Math.min(255, (alpha - 26) * 255 / 200));
                }
                pixels.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) alpha);
            }
        }
        pixels.flip();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.pages.get(this.pages.size() - 1).intValue());
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, this.penX, this.penY, width, cell, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        glyph.page = this.pages.size() - 1;
        glyph.offset = -2.0F;
        glyph.width = width;
        glyph.u0 = this.penX / (float) PAGE;
        glyph.u1 = (this.penX + width) / (float) PAGE;
        glyph.v0 = this.penY / (float) PAGE;
        glyph.v1 = (this.penY + cell) / (float) PAGE;
        this.penX += width + 1;
    }

    private void newPage() {
        int texture = TextureUtil.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, PAGE, PAGE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                BufferUtils.createByteBuffer(PAGE * PAGE * 4));
        this.pages.add(Integer.valueOf(texture));
        this.penX = 0;
        this.penY = 0;
        this.rowHeight = 0;
    }

    private static final class Sized {
        int px;
        Font[] fonts;
        FontMetrics[] metrics;
        int cell;
        int height;
        int baseline;
    }

    private static final class Glyph {
        int page;
        float advance;
        float offset;
        int width;
        float u0;
        float u1;
        float v0;
        float v1;
    }
}
