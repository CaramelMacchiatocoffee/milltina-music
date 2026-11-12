package net.milltina.music;

import io.nayuki.qrcodegen.QrCode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

final class MusicView {
    static final int DEFAULT_W = 460;
    static final int DEFAULT_H = 270;

    static final int MIN_W = 400;
    static final int MIN_H = 220;
    static final int MAX_W = 1000;
    static final int MAX_H = 600;

    private static final int SIDE_W = 104;
    private static final int RIGHT_W = 132;
    private static final int BAR_H = 40;

    private static final int TOP_H = 28;

    private static final int HEAD_H = TOP_H + 34;
    private static final float SEARCH_X = 104 + 12;
    private static final float SEARCH_Y = 6;
    private static final int ROW_H = 22;
    private static final int MAIN_X = SIDE_W;

    int W = DEFAULT_W;
    int H = DEFAULT_H;
    private float SEARCH_W = DEFAULT_W - RIGHT_W - SIDE_W - 24;
    private int MAIN_R = DEFAULT_W - RIGHT_W;
    private int BAR_Y = DEFAULT_H - BAR_H;

    static final int[] LIGHT = {0xFFFFFF, 0xE8E9EC, 0x16181C, 0x6E7580, 0x3F4550, 0xA4AAB3, 0x000000, 0xEFF0F2, 0xF1F2F4};
    static final int[] DARK = {0x000000, 0x1C1D20, 0xF0F2F5, 0x8C939D, 0xB0B6BF, 0x5A5F67, 0xFFFFFF, 0x15161A, 0x1A1B1F};
    private int BG;
    private int LINE;
    private int TEXT;
    private int DIM;
    private int NAV;
    private int FAINT;

    private int INK;

    private int TILE;
    private int TOAST;
    private float darkT = -1.0F;
    private static final int DEFAULT_ACCENT = 0xE5484D;

    private static final int P_SEARCH = 0;
    private static final int P_DAILY = 1;
    private static final int P_LIKED = 2;
    private static final int P_QUEUE = 3;
    private static final int P_CHART = 4;
    private static final int P_PLAYLIST = 5;
    private static final int P_LOGIN = 6;
    private static final int P_HOME = 7;
    private static final long[] CHARTS = {3778678L, 3779629L, 19723756L};
    private static final String[] CHART_NAMES = {"Hot", "New", "Rising"};

    private static final int K_DAILY = 1;
    private static final int K_DAILY_PLAY = 2;
    private static final int K_HEART = 3;
    private static final int K_CHART = 4;
    private static final int K_CHART_PLAY = 5;
    private static final int K_LIST = 6;
    private static final int K_LIST_PLAY = 7;

    private final Music music;
    private final MusicPlayer player;
    private final MusicFont font = MusicFont.get();

    private int page = P_HOME;
    private long pageId;
    private String pageTitle = "For you";

    private final List<Object[]> backStack = new ArrayList<Object[]>();
    private float pageTime = 10.0F;

    private boolean autoPlay;
    private final List<Hit> hits = new ArrayList<Hit>();
    private float homeContent;
    private final java.util.Map<String, String[]> wraps = new java.util.HashMap<String, String[]>();

    private boolean immersive;
    private float immersiveT;
    private float bigCoverScale = 1.0F;
    private float bigLyricCurrent = -1.0F;
    private final List<float[]> lyricHits = new ArrayList<float[]>();

    boolean screenBackdrop;

    private float clipTop = -1.0E9F;
    private float clipBottom = 1.0E9F;
    private final Scroller listScroll = new Scroller();
    private final Scroller sideScroll = new Scroller();
    private final Scroller lyricScroll = new Scroller();

    private String query = "";
    private String searched = "";
    private boolean searchFocused = true;
    private boolean selectAll;
    private Music.SongList searchList;

    private String qrKey;
    private QrCode qr;
    private volatile int qrState;
    private volatile String qrError;
    private long qrNextCheck;
    private boolean qrBusy;

    private float alpha = 1.0F;
    private long lastFrame;
    private float dt;
    private float time;
    private int accent = DEFAULT_ACCENT;
    private float accentR = (DEFAULT_ACCENT >> 16 & 255);
    private float accentG = (DEFAULT_ACCENT >> 8 & 255);
    private float accentB = (DEFAULT_ACCENT & 255);
    private float navY = -1.0F;
    private float playMorph;
    private float barHover;
    private float coverFade = 1.0F;
    private int coverShown;
    private int coverOld;
    private float statusFade;
    private String statusShown = "";
    private final float[] hover = new float[512];
    private float lyricCurrent = -1.0F;

    private float mouseX = -1000.0F;
    private float mouseY = -1000.0F;
    private int dragging;
    private static final int DRAG_PROGRESS = 1;
    private static final int DRAG_VOLUME = 2;

    private static final int DRAG_THUMB = 3;

    private static final int DRAG_TOUCH = 4;
    private static final int AREA_SIDE = 1;
    private static final int AREA_MAIN = 2;
    private float seekPreview = -1.0F;
    private Scroller held;
    private float pressX;
    private float pressY;
    private float pressTarget;
    private float grab;
    private boolean moved;
    private int pressArea;

    MusicView(Music music) {
        this.music = music;
        this.player = music.player;
        this.searchFocused = false;
    }

    void size(int w, int h) {
        this.W = Math.max(MIN_W, Math.min(MAX_W, w));
        this.H = Math.max(MIN_H, Math.min(MAX_H, h));
        this.SEARCH_W = this.W - RIGHT_W - SIDE_W - 24;
        this.MAIN_R = this.W - RIGHT_W;
        this.BAR_Y = this.H - BAR_H;
    }

    void opened() {
        this.pageTime = 0.0F;
        this.lastFrame = 0L;
        this.immersive = false;
        this.immersiveT = 0.0F;
        this.music.refreshAccount();
    }

    boolean back() {
        if (this.immersive) {
            this.immersive = false;
            return true;
        }
        if (!this.backStack.isEmpty()) {
            goBack();
            return true;
        }
        return false;
    }

    boolean immersiveWanted() {
        return this.immersive;
    }

    boolean immersiveOpen() {
        return this.immersive || this.immersiveT > 0.02F;
    }

    float immersiveAmount() {
        return smoothInOut(this.immersiveT);
    }

    boolean typing() {
        return this.searchFocused && !this.immersive;
    }

    void draw(float mx, float my, float alpha) {
        long now = System.nanoTime();
        this.dt = this.lastFrame == 0L ? 0.016F : Math.min(0.1F, (now - this.lastFrame) / 1.0E9F);
        this.lastFrame = now;
        this.time += this.dt;
        this.pageTime += this.dt;
        this.alpha = alpha;
        this.mouseX = mx;
        this.mouseY = my;
        updateAccent();
        if (this.dragging == DRAG_PROGRESS) {
            this.seekPreview = progressAt(mx);
        } else if (this.dragging == DRAG_VOLUME) {
            MusicSettings.setVolume(this.player, volumeAt(mx));
        } else if (this.dragging == DRAG_THUMB) {
            this.held.dragThumb(my, this.grab);
        } else if (this.dragging == DRAG_TOUCH) {
            if (!this.moved && (Math.abs(my - this.pressY) > 4.0F || Math.abs(mx - this.pressX) > 4.0F)) {
                this.moved = true;
                this.held.hold();
                this.pressTarget = this.held.target;
                this.pressY = my;
            }
            if (this.moved) {
                this.held.holdAt(this.pressTarget - (my - this.pressY));
            }
        }
        pollLogin();
        this.immersiveT = clamp(this.immersiveT + (this.immersive ? 1 : -1) * this.dt / 0.32F);
        if (this.player.current() == null) {
            this.immersive = false;
        }
        float full = smoothInOut(this.immersiveT);

        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        float darkTarget = MusicSettings.check("Dark Mode", false) ? 1.0F : 0.0F;
        this.darkT = this.darkT < 0 ? darkTarget : ease(this.darkT, darkTarget, 9.0F);
        palette(this.darkT);

        this.alpha = alpha * (1.0F - full);
        if (this.alpha > 0.01F) {
            rect(0, 0, W, H, BG, 255);
            rect(SIDE_W - 1, 0, SIDE_W, BAR_Y, LINE, 255);
            rect(MAIN_R, 0, MAIN_R + 1, BAR_Y, LINE, 255);

            drawMain();
            drawSearchField(SEARCH_X, SEARCH_Y, SEARCH_W);
            drawSidebar();
            drawNowPlaying();
        }
        this.alpha = alpha;
        if (full > 0.001F) {
            palette(1.0F);
            drawImmersive(full, alpha);
        }
        this.alpha = alpha;

        palette(this.darkT + (1.0F - this.darkT) * full);
        drawBar(full);
        drawStatus(full);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void palette(float d) {
        this.BG = blend(LIGHT[0], DARK[0], d);
        this.LINE = blend(LIGHT[1], DARK[1], d);
        this.TEXT = blend(LIGHT[2], DARK[2], d);
        this.DIM = blend(LIGHT[3], DARK[3], d);
        this.NAV = blend(LIGHT[4], DARK[4], d);
        this.FAINT = blend(LIGHT[5], DARK[5], d);
        this.INK = blend(LIGHT[6], DARK[6], d);
        this.TILE = blend(LIGHT[7], DARK[7], d);
        this.TOAST = blend(LIGHT[8], DARK[8], d);
    }

    private void updateAccent() {
        Song song = this.player.current();
        Music.Cover cover = song == null ? null : this.music.cover(song.cover, 200);
        int target = cover == null ? (song == null ? DEFAULT_ACCENT : this.accent) : cover.accent;
        this.accentR = ease(this.accentR, target >> 16 & 255, 4.0F);
        this.accentG = ease(this.accentG, target >> 8 & 255, 4.0F);
        this.accentB = ease(this.accentB, target & 255, 4.0F);
        this.accent = (int) this.accentR << 16 | (int) this.accentG << 8 | (int) this.accentB;
    }

    private void drawSidebar() {
        text("Music", 10, 10, TEXT, 255, 1.25F);
        themeSwitch(SIDE_W - 17, 16);
        refreshButton(SIDE_W - 35, 16);

        List<Nav> items = navItems();
        float top = 34;
        float bottom = BAR_Y - 22;
        float content = 0;
        for (Nav nav : items) {
            content += nav.header ? 18 : 15;
        }
        this.sideScroll.update(Math.max(0, content - (bottom - top)), this.dt);
        float y = top - this.sideScroll.pos;
        float selectedY = -1;
        for (Nav nav : items) {
            if (!nav.header && isCurrent(nav)) {
                selectedY = y;
            }
            y += nav.header ? 18 : 15;
        }

        if (selectedY >= 0) {
            this.navY = this.navY < 0 ? selectedY : ease(this.navY, selectedY, 18.0F);
        } else {
            this.navY = -1.0F;
        }
        this.clipTop = top;
        this.clipBottom = bottom;
        if (this.navY >= 0) {
            float edge = fadeEdge(this.navY, 15, top, bottom);
            rect(4, this.navY, SIDE_W - 8, this.navY + 15, INK, (int) (235 * edge));
        }
        y = top - this.sideScroll.pos;
        int hoverIndex = 0;
        for (Nav nav : items) {
            float h = nav.header ? 18 : 15;
            float edge = fadeEdge(y, h, top, bottom);
            if (nav.header) {
                text(nav.label, 10, y + 7, FAINT, (int) (255 * edge), 0.8F);
            } else {
                boolean over = pointerFree() && edge > 0.5F && in(0, y, SIDE_W - 1, h) && this.mouseY >= top && this.mouseY < bottom;
                float hv = hover(310 + hoverIndex++ % 90, over);

                float under = this.navY < 0 ? 0.0F : clamp(1.0F - Math.abs(this.navY - y) / h);
                rect(4, y, SIDE_W - 8, y + h, INK, (int) (9 * hv * (1.0F - under) * edge));
                String label = this.font.trim(nav.label, SIDE_W - 26, 0.9F);
                text(label, 12, y + 3.5F, blend(NAV, BG, under), (int) (255 * edge), 0.9F);
            }
            y += h;
        }
        this.clipTop = -1.0E9F;
        this.clipBottom = 1.0E9F;
        this.sideScroll.drawBar(SIDE_W - 5, top, bottom, content, this.accent, INK, this.alpha,
                this.sideScroll.onBar(this.mouseX, this.mouseY) || this.held == this.sideScroll && this.dragging == DRAG_THUMB);

        rect(0, BAR_Y - 20, SIDE_W - 1, BAR_Y - 19, LINE, 255);
        String who;
        if (this.music.loggedIn()) {
            who = this.font.trim(this.music.nickname == null ? "" : this.music.nickname, SIDE_W - 46, 0.85F);
            text(who, 10, BAR_Y - 14, DIM, 255, 0.85F);
            button(SIDE_W - 36, BAR_Y - 16, 30, 12, "Log out", false, 300);
        } else {
            button(8, BAR_Y - 16, SIDE_W - 16, 12, this.page == P_LOGIN ? "Scan the QR code" : "Log in", true, 300);
        }
    }

    private long refreshAt;

    private void refreshButton(float cx, float cy) {
        float hv = hover(493, pointerFree() && in(cx - 8, cy - 8, 16, 16));
        rect(cx - 8, cy - 8, cx + 8, cy + 8, INK, (int) (16 * hv));
        int color = blend(DIM, TEXT, hv);
        float spin = (System.currentTimeMillis() - this.refreshAt) / 450.0F;
        GL11.glPushMatrix();
        if (spin < 1.0F) {
            GL11.glTranslatef(cx, cy, 0.0F);
            GL11.glRotatef(360.0F * smooth(spin), 0.0F, 0.0F, 1.0F);
            GL11.glTranslatef(-cx, -cy, 0.0F);
        }
        rect(cx - 3, cy - 5, cx + 1, cy - 4, color, 255);
        rect(cx - 4, cy - 4, cx - 3, cy - 3, color, 255);
        rect(cx - 5, cy - 3, cx - 4, cy + 3, color, 255);
        rect(cx - 4, cy + 3, cx - 3, cy + 4, color, 255);
        rect(cx - 3, cy + 4, cx + 3, cy + 5, color, 255);
        rect(cx + 3, cy + 3, cx + 4, cy + 4, color, 255);
        rect(cx + 4, cy - 1, cx + 5, cy + 3, color, 255);
        triangle(cx + 1, cy - 7.5F, cx + 1, cy - 1.5F, cx + 4.5F, cy - 4.5F, color, 255);
        GL11.glPopMatrix();
    }

    private void refresh() {
        this.refreshAt = System.currentTimeMillis();
        this.music.refresh();
        if (this.page == P_SEARCH && this.searched != null && !this.searched.isEmpty()) {
            final String words = this.searched;
            this.searchList = this.music.list("search:" + words, words, new Music.Loader() {
                public List<Song> load() throws IOException {
                    return MusicView.this.music.api.search(words, 0);
                }
            }, true);
        }
        if (this.page != P_QUEUE) {
            this.listScroll.reset();
            this.pageTime = 0.0F;
        }
    }

    private void themeSwitch(float cx, float cy) {
        float hv = hover(492, pointerFree() && in(cx - 8, cy - 8, 16, 16));
        rect(cx - 8, cy - 8, cx + 8, cy + 8, INK, (int) (16 * hv));
        int color = blend(DIM, TEXT, hv);
        float d = this.darkT;
        float moon = 1.0F - smooth(clamp(d * 2.0F));
        float sun = smooth(clamp(d * 2.0F - 1.0F));
        if (moon > 0.01F) {
            float s = 0.7F + 0.3F * moon;
            int a = (int) (255 * moon);
            float[][] cells = {{-2, -4, 0, -3}, {-3, -3, -1, -2}, {-4, -2, -2, 1}, {3, 0, 4, 1}, {-4, 1, -1, 2},
                    {2, 1, 4, 2}, {-3, 2, 3, 3}, {-2, 3, 2, 4}};
            for (float[] c : cells) {
                rect(cx + c[0] * s, cy + c[1] * s, cx + c[2] * s, cy + c[3] * s, color, a);
            }
        }
        if (sun > 0.01F) {
            float s = 0.7F + 0.3F * sun;
            int a = (int) (255 * sun);
            rect(cx - 2 * s, cy - 2 * s, cx + 2 * s, cy + 2 * s, color, a);

            float r = 4.5F * s;
            float q = 3.3F * s;
            float k = 0.6F * s;
            rect(cx - k, cy - r - k, cx + k, cy - r + k, color, a);
            rect(cx - k, cy + r - k, cx + k, cy + r + k, color, a);
            rect(cx - r - k, cy - k, cx - r + k, cy + k, color, a);
            rect(cx + r - k, cy - k, cx + r + k, cy + k, color, a);
            rect(cx - q - k, cy - q - k, cx - q + k, cy - q + k, color, a);
            rect(cx + q - k, cy - q - k, cx + q + k, cy - q + k, color, a);
            rect(cx - q - k, cy + q - k, cx - q + k, cy + q + k, color, a);
            rect(cx + q - k, cy + q - k, cx + q + k, cy + q + k, color, a);
        }
    }

    private boolean isCurrent(Nav nav) {
        if (nav.page != this.page) {
            return false;
        }
        return (nav.page != P_PLAYLIST && nav.page != P_CHART) || nav.id == this.pageId;
    }

    private List<Nav> navItems() {
        List<Nav> items = new ArrayList<Nav>();
        items.add(new Nav("For you", P_HOME, 0));
        items.add(new Nav("Daily picks", P_DAILY, 0));
        items.add(new Nav("Liked songs", P_LIKED, 0));
        items.add(new Nav("Now playing", P_QUEUE, 0));
        items.add(new Nav("CHARTS"));
        for (int i = 0; i < CHARTS.length; i++) {
            items.add(new Nav(CHART_NAMES[i], P_CHART, CHARTS[i]));
        }
        List<Playlist> lists = this.music.playlists;
        if (lists.size() > 1) {
            items.add(new Nav("PLAYLISTS"));

            for (int i = 1; i < lists.size(); i++) {
                items.add(new Nav(lists.get(i).name, P_PLAYLIST, lists.get(i).id));
            }
        }
        return items;
    }

    private void drawMain() {
        float enter = smooth(Math.min(1.0F, this.pageTime / 0.28F));
        float shift = (1.0F - enter) * 10.0F;
        if (this.page == P_LOGIN) {
            drawLogin(enter, shift);
            return;
        }
        if (this.page == P_HOME) {
            drawHome(enter, shift);
            return;
        }
        Music.SongList list = currentList();
        if (this.autoPlay && list != null && !list.loading) {
            this.autoPlay = false;
            if (!list.songs.isEmpty()) {
                this.player.play(list.songs, 0, this.pageTitle);
            }
        }

        float hx = MAIN_X + 12;
        String sub;
        if (this.page == P_SEARCH) {
            sub = list == null ? "" : list.loading ? "Searching..."
                    : list.songs.size() + " songs for “" + this.searched + "”";
        } else {
            sub = list == null ? "" : list.loading ? "Loading..." : list.songs.size() + " songs";
        }
        if (!this.backStack.isEmpty()) {
            float bh = hover(483, pointerFree() && in(MAIN_X + 6, TOP_H + 3, 16, 18));
            rect(MAIN_X + 6, TOP_H + 3, MAIN_X + 22, TOP_H + 21, INK, (int) (14 * bh));
            chevronLeft(MAIN_X + 14, TOP_H + 12, blend(DIM, TEXT, bh));
            hx += 15;
        }
        text(this.font.trim(this.pageTitle, MAIN_R - hx - 68, 1.3F), hx + shift, TOP_H + 4, TEXT, (int) (255 * enter), 1.3F);
        text(this.font.trim(sub, MAIN_R - hx - 68, 0.8F), hx + shift, TOP_H + 19, DIM, (int) (255 * enter), 0.8F);
        if (list != null && !list.loading && !list.songs.isEmpty()) {
            accentButton(MAIN_R - 52, TOP_H + 7, 40, 14, "Play all", 301);
        }
        rect(MAIN_X + 12, HEAD_H - 1, MAIN_R - 12, HEAD_H, LINE, 255);

        float top = HEAD_H + 2;
        float bottom = BAR_Y - 2;
        if (list == null) {
            if (this.page == P_SEARCH) {
                hint("Type in the search bar above and press Enter", top, bottom);
            } else {
                hint("Log in to see this", top, bottom);
            }
            return;
        }
        if (list.error != null) {
            hint("Couldn't load: " + list.error, top, bottom);
            return;
        }
        if (list.loading) {
            loadingDots((MAIN_X + MAIN_R) / 2.0F, (top + bottom) / 2.0F);
            return;
        }
        List<Song> songs = list.songs;
        if (songs.isEmpty()) {
            hint("Nothing here", top, bottom);
            return;
        }
        this.listScroll.update(Math.max(0, songs.size() * ROW_H + 6 - (bottom - top)), this.dt);
        Song playing = this.player.current();
        this.clipTop = top;
        this.clipBottom = bottom;
        int first = Math.max(0, (int) ((this.listScroll.pos - 12) / ROW_H));
        for (int i = first; i < songs.size(); i++) {
            float y = top + 3 + i * ROW_H - this.listScroll.pos;
            if (y > bottom) {
                break;
            }

            float rowIn = smooth(clamp((this.pageTime - Math.min(i - first, 12) * 0.022F) / 0.25F));
            float edge = fadeEdge(y, ROW_H, top, bottom) * rowIn;
            if (edge <= 0.01F) {
                continue;
            }
            float x = MAIN_X + 6 + (1.0F - rowIn) * 12.0F;
            Song song = songs.get(i);
            boolean over = pointerFree() && in(MAIN_X + 6, y, MAIN_R - MAIN_X - 12, ROW_H)
                    && this.mouseY >= top && this.mouseY < bottom;
            float hv = hover(i % 300, over);
            boolean isPlaying = playing != null && playing.id == song.id;
            if (isPlaying) {
                rect(x, y, MAIN_R - 6, y + ROW_H, this.accent, (int) (22 * edge));
            }
            rect(x, y, MAIN_R - 6, y + ROW_H, INK, (int) (10 * hv * edge));
            int a = (int) (255 * edge);

            float cs = ROW_H - 4;
            rect(x + 3, y + 2, x + 3 + cs, y + 2 + cs, TILE, a);
            Music.Cover thumb = this.music.cover(song.cover, coverPx(cs));
            if (thumb != null) {
                image(thumb.texture, x + 3, y + 2, cs, edge);
            } else {
                noteIconSmall(x + 3 + cs / 2, y + 2 + cs / 2, FAINT, a);
            }
            if (isPlaying) {
                rect(x + 3, y + 2, x + 3 + cs, y + 2 + cs, 0x000000, (int) (110 * edge));
                equalizer(x + 3 + cs / 2 - 4, y + 2 + cs / 2 - 4, a);
            }
            float textX = x + 27 + hv * 2.0F;
            String time = clock(song.durationMs);
            float room = MAIN_R - 10 - textX - this.font.width(time, 0.75F) - 14;
            text(this.font.trim(song.name, room, 0.9F), textX, y + 2.5F, isPlaying ? blend(TEXT, this.accent, 0.6F) : TEXT, a, 0.9F);
            String detail = song.album.length() == 0 ? song.artists : song.artists + "  ·  " + song.album;
            text(this.font.trim(detail, room, 0.72F), textX, y + 12.5F, DIM, a, 0.72F);
            text(time, MAIN_R - 12 - this.font.width(time, 0.75F), y + 7, FAINT, a, 0.75F);
            if (this.music.isLiked(song)) {
                heart(MAIN_R - 26 - this.font.width(time, 0.75F), y + 8, this.accent, a);
            }
        }
        this.clipTop = -1.0E9F;
        this.clipBottom = 1.0E9F;
        this.listScroll.drawBar(MAIN_R - 5, top, bottom, songs.size() * ROW_H + 6, this.accent, INK, this.alpha, mainBarHot());
    }

    private boolean mainBarHot() {
        return this.listScroll.onBar(this.mouseX, this.mouseY) || this.held == this.listScroll && this.dragging == DRAG_THUMB;
    }

    private boolean pointerFree() {
        return this.dragging == 0 || this.dragging == DRAG_TOUCH && !this.moved;
    }

    private void drawHome(float enter, float shift) {
        float left = MAIN_X + 12;
        float right = MAIN_R - 12;
        String greeting = this.music.loggedIn() && this.music.nickname != null ? "Picked for " + this.music.nickname
                : this.music.api.loggedIn() ? "Loading your account..." : "Log in for picks made for you";
        text("For you", left + shift, TOP_H + 4, TEXT, (int) (255 * enter), 1.3F);
        text(this.font.trim(greeting, right - left, 0.8F), left + shift, TOP_H + 19, DIM, (int) (255 * enter), 0.8F);
        rect(left, HEAD_H - 1, right, HEAD_H, LINE, 255);

        float top = HEAD_H + 1;
        float bottom = BAR_Y - 1;
        this.listScroll.update(Math.max(0, this.homeContent - (bottom - top)), this.dt);
        this.hits.clear();
        this.clipTop = top;
        this.clipBottom = bottom;
        float gap = 7;

        int columns = Math.max(3, (int) ((right - left + gap) / (62 + gap) + 0.3F));
        float tile = (right - left - gap * (columns - 1)) / columns;

        float big = Math.min((right - left - gap * 2) / 3.0F, tile * 1.5F);
        float bigStep = (right - left - big) / 2.0F;
        float rise = (1.0F - enter) * 8.0F;
        float y = top + 8 - this.listScroll.pos + rise;

        Music.SongList daily = null;
        if (this.music.loggedIn()) {
            daily = this.music.list("daily", "Daily picks", new Music.Loader() {
                public List<Song> load() throws IOException {
                    return MusicView.this.music.api.dailySongs();
                }
            }, false);
        }
        Music.Cover dailyCover = daily == null || daily.loading || daily.songs.isEmpty() ? null
                : this.music.cover(daily.songs.get(0).cover, coverPx(big));
        String day = String.valueOf(java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH));
        bigCard(480, left, y, big, dailyCover, 0x3A3F4A, day, "Daily picks",
                this.music.loggedIn() ? "Fresh songs for you" : "Log in first", false, false, K_DAILY, K_DAILY_PLAY);

        List<Playlist> mine = this.music.playlists;
        Music.Cover likedCover = mine.isEmpty() ? null : this.music.cover(mine.get(0).cover, coverPx(big));
        bigCard(481, left + bigStep, y, big, likedCover, 0x8C2F39, null, "Heartbeat",
                this.music.loggedIn() ? "Liked songs and more like them" : "Log in first", this.music.heartbeatBusy,
                "Heartbeat".equals(this.player.source) && this.player.current() != null, K_HEART, K_HEART);

        bigCard(482, left + bigStep * 2, y, big, null, 0xC7363C, "Hot", "Charts", "Top songs on NetEase", false,
                false, K_CHART, K_CHART_PLAY);
        y += big + 14;

        Music.Shelf shelf = this.music.recommended();
        y = shelf(shelf.items, shelf.loading, shelf.error, "Recommended playlists", left, y, tile, gap, columns, 18, 420);

        int moreSlot = 460;
        for (Map.Entry<String, List<Playlist>> row : shelf.more.entrySet()) {
            if (moreSlot > 470) {
                break;
            }
            y = shelf(row.getValue(), false, null, row.getKey(), left, y + 4, tile, gap, columns, 10, moreSlot);
            moreSlot += 10;
        }
        if (this.music.loggedIn() && !mine.isEmpty()) {
            y = shelf(mine, false, null, "Your playlists", left, y + 4, tile, gap, columns, 60, 440);
        }
        this.homeContent = y + this.listScroll.pos - rise - top + 4;
        this.clipTop = -1.0E9F;
        this.clipBottom = 1.0E9F;
        this.listScroll.drawBar(MAIN_R - 5, top, bottom, this.homeContent, this.accent, INK, this.alpha, mainBarHot());
    }

    private float shelf(List<Playlist> items, boolean loading, String error, String title, float left, float y, float tile,
            float gap, int columns, int max, int slot) {
        text(title, left, y, TEXT, 255, 1.0F);
        y += 15;
        float across = tile * columns + gap * (columns - 1);
        if (loading) {
            loadingDots(left + across / 2, y + 16);
            return y + 36;
        }
        if (error != null) {
            text(this.font.trim("Couldn't load: " + error, across, 0.75F), left, y + 4, DIM, 255, 0.75F);
            return y + 20;
        }
        int count = Math.min(max, items.size());
        float rowH = tile + 24;
        for (int i = 0; i < count; i++) {
            float x = left + (i % columns) * (tile + gap);
            float ty = y + (i / columns) * rowH;
            if (ty + rowH < this.clipTop || ty > this.clipBottom) {
                continue;
            }
            playlistTile(slot + i % 20, x, ty, tile, items.get(i), i);
        }
        return y + ((count + 2) / 3) * rowH;
    }

    private void playlistTile(int slot, float x, float y, float size, Playlist list, int index) {
        boolean over = pointerFree() && inClip() && in(x, y, size, size + 20);
        float hv = hover(slot, over);
        float lift = hv * 1.5F;
        float cy = y - lift;
        rect(x, cy, x + size, cy + size, TILE, 255);
        Music.Cover cover = this.music.cover(list.cover, coverPx(size));
        if (cover != null) {
            image(cover.texture, x, cy, size, 1.0F);
        } else {
            noteIcon(x + size / 2, cy + size / 2, FAINT);
        }
        rect(x, cy, x + size, cy + size, 0xFFFFFF, (int) (14 * hv));
        if (list.tag != null) {
            String tag = this.font.trim(list.tag, size - 10, 0.6F);
            rect(x + 3, cy + 3, x + 3 + this.font.width(tag, 0.6F) + 5, cy + 12, 0x000000, 150);
            text(tag, x + 5.5F, cy + 4.5F, 0xFFFFFF, 230, 0.6F);
        }
        playSquare(x + size - 15, cy + size - 15, 12, hv);
        this.hits.add(new Hit(x, y, size, size + 20, K_LIST, list));
        this.hits.add(new Hit(x + size - 17, cy + size - 17, 16, 16, K_LIST_PLAY, list));
        String[] lines = wrap2(list.name, size, 0.72F);
        for (int i = 0; i < lines.length; i++) {
            text(lines[i], x, y + size + 3 + i * 9, blend(DIM, TEXT, 0.6F + 0.4F * hv), 255, 0.72F);
        }
    }

    private void bigCard(int slot, float x, float y, float size, Music.Cover cover, int fill, String big, String title,
            String sub, boolean busy, boolean active, int open, int play) {
        boolean over = pointerFree() && inClip() && in(x, y, size, size);
        float hv = hover(slot, over);
        float cy = y - hv * 1.5F;
        if (cover != null) {
            image(cover.texture, x, cy, size, 1.0F);
        } else {
            gradient(x, cy, x + size, cy + size, blend(fill, 0xFFFFFF, 0.18F), 255, fill, 255);
        }
        if (big != null) {
            if (cover != null) {
                gradient(x, cy, x + size, cy + 26, 0x000000, 120, 0x000000, 0);
                text(big, x + 6, cy + 5, 0xFFFFFF, 255, 1.9F);
            } else {
                centered(big, x + size / 2, cy + size / 2 - 16, 0xFFFFFF, 255, 1.9F);
            }
        }
        gradient(x, cy + size - 28, x + size, cy + size, 0x000000, 0, 0x000000, 200);
        rect(x, cy, x + size, cy + size, 0xFFFFFF, (int) (14 * hv));
        text(this.font.trim(title, size - 20, 0.85F), x + 5, cy + size - 21, 0xFFFFFF, 255, 0.85F);
        text(this.font.trim(sub, size - 20, 0.58F), x + 5, cy + size - 10, 0xD0D4DA, 220, 0.58F);
        if (busy) {
            loadingDots(x + size / 2, cy + size / 2, 0xFFFFFF);
        } else if (active) {
            equalizer(x + size - 13, cy + 5, 255);
        }
        playSquare(x + size - 14, cy + size - 14, 11, hv);
        this.hits.add(new Hit(x, y, size, size, open, null));
        this.hits.add(new Hit(x + size - 16, cy + size - 16, 15, 15, play, null));
    }

    private void playSquare(float x, float y, float s, float hv) {
        rect(x, y, x + s, y + s, blend(0x000000, this.accent, hv), (int) (150 + 105 * hv));
        float cx = x + s / 2 + 0.5F;
        float cy = y + s / 2;
        triangle(cx - 2, cy - 3, cx - 2, cy + 3, cx + 3, cy, 0xFFFFFF, 255);
    }

    private boolean inClip() {
        return this.mouseY >= this.clipTop && this.mouseY < this.clipBottom;
    }

    private String[] wrap2(String text, float w, float scale) {
        String key = text + '\u0000' + (int) w + '\u0000' + scale;
        String[] known = this.wraps.get(key);
        if (known != null) {
            return known;
        }
        String[] lines;
        if (this.font.width(text, scale) <= w) {
            lines = new String[] {text};
        } else {
            int cut = text.length();
            while (cut > 1 && this.font.width(text.substring(0, cut), scale) > w) {
                cut--;
            }

            int space = text.lastIndexOf(' ', cut);
            if (space > cut - 8 && space > 0) {
                cut = space;
            }
            lines = new String[] {text.substring(0, cut).trim(), this.font.trim(text.substring(cut).trim(), w, scale)};
        }
        if (this.wraps.size() > 400) {
            this.wraps.clear();
        }
        this.wraps.put(key, lines);
        return lines;
    }

    private void tapHome(float mx, float my) {
        for (int i = this.hits.size() - 1; i >= 0; i--) {
            Hit hit = this.hits.get(i);
            if (mx < hit.x || mx >= hit.x + hit.w || my < hit.y || my >= hit.y + hit.h || my < HEAD_H || my >= BAR_Y) {
                continue;
            }
            boolean loggedIn = this.music.loggedIn();
            switch (hit.kind) {
                case K_DAILY:
                case K_DAILY_PLAY:
                    if (!loggedIn) {
                        open(P_LOGIN, 0, "Log in");
                    } else {
                        open(P_DAILY, 0, "Daily picks");
                        this.autoPlay = hit.kind == K_DAILY_PLAY;
                    }
                    return;
                case K_HEART:
                    if (!loggedIn) {
                        open(P_LOGIN, 0, "Log in");
                    } else {
                        this.music.heartbeat();
                    }
                    return;
                case K_CHART:
                case K_CHART_PLAY:
                    open(P_CHART, CHARTS[0], CHART_NAMES[0]);
                    this.autoPlay = hit.kind == K_CHART_PLAY;
                    return;
                case K_LIST:
                case K_LIST_PLAY:
                    Playlist list = (Playlist) hit.data;
                    boolean liked = !this.music.playlists.isEmpty() && this.music.playlists.get(0).id == list.id;
                    if (liked) {
                        open(P_LIKED, 0, "Liked songs");
                    } else {
                        open(P_PLAYLIST, list.id, list.name);
                    }
                    this.autoPlay = hit.kind == K_LIST_PLAY;
                    return;
                default:
                    return;
            }
        }
    }

    private void drawSearchField(float x, float y, float w) {
        boolean over = in(x, y, w, 18);
        float hv = hover(400, over || this.searchFocused);
        rect(x, y, x + w, y + 18, INK, (int) (10 + 8 * hv));
        rect(x, y + 17, x + w, y + 18, this.searchFocused ? this.accent : LINE, 255);
        magnifier(x + 6, y + 5, this.searchFocused ? TEXT : DIM);
        float tx = x + 20;
        if (this.query.isEmpty()) {
            text("Search songs", tx, y + 5, FAINT, 255, 0.9F);
        } else {
            String shown = this.query;
            while (shown.length() > 1 && this.font.width(shown, 0.9F) > w - 28) {
                shown = shown.substring(1);
            }
            if (this.selectAll) {
                rect(tx - 1, y + 3, tx + this.font.width(shown, 0.9F) + 1, y + 15, this.accent, 90);
            }
            text(shown, tx, y + 5, TEXT, 255, 0.9F);
            tx += this.font.width(shown, 0.9F);
        }
        if (this.searchFocused && (this.time % 1.0F) < 0.55F) {
            rect(tx + 1, y + 4, tx + 2, y + 14, TEXT, 220);
        }
    }

    private void drawLogin(float enter, float shift) {
        float cx = (MAIN_X + MAIN_R) / 2.0F;
        text("Log in to NetEase Cloud Music", cx - this.font.width("Log in to NetEase Cloud Music", 1.1F) / 2 + shift, TOP_H + 6,
                TEXT, (int) (255 * enter), 1.1F);
        float size = 118;
        float qx = cx - size / 2;
        float qy = TOP_H + 26;

        rect(qx - 1, qy - 1, qx + size + 1, qy + size + 1, LINE, (int) (255 * enter));
        rect(qx, qy, qx + size, qy + size, 0xFFFFFF, (int) (255 * enter));
        if (this.qr != null) {
            int n = this.qr.size;
            float cell = (size - 12) / n;
            float ox = qx + (size - cell * n) / 2;
            float oy = qy + (size - cell * n) / 2;
            boolean faded = this.qrState == 802 || this.qrState == 800;
            int color = 0x111111;
            int a = (int) ((faded ? 60 : 255) * enter);
            Tessellator t = beginQuads(color, a);
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    if (this.qr.getModule(x, y)) {
                        quad(t, ox + x * cell, oy + y * cell, ox + (x + 1) * cell, oy + (y + 1) * cell);
                    }
                }
            }
            t.draw();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            if (this.qrState == 802) {
                centered("Scanned", cx, qy + size / 2 - 5, 0x111111, 255, 1.1F);
            } else if (this.qrState == 800) {
                centered("Expired", cx, qy + size / 2 - 9, 0x111111, 255, 1.1F);
                centered("Click to refresh", cx, qy + size / 2 + 4, 0x444444, 255, 0.8F);
            }
        } else {
            loadingDots(cx, qy + size / 2, 0x111111);
        }
        String line = this.qrError != null ? this.qrError
                : this.qrState == 802 ? "Confirm the login on your phone"
                : this.qrState == 803 ? "Logged in"
                : "Scan with the NetEase Cloud Music app";
        centered(line, cx, qy + size + 12, this.qrError != null ? blend(0xD93036, 0xFF7070, this.darkT) : DIM,
                (int) (255 * enter), 0.85F);
        centered("Your login stays on this computer only", cx, qy + size + 26, FAINT, (int) (255 * enter), 0.72F);
    }

    private void drawNowPlaying() {
        Song song = this.player.current();
        float x = MAIN_R + 11;
        float size = RIGHT_W - 22;
        float y = 12;
        Music.Cover cover = song == null ? null : this.music.cover(song.cover, coverPx(size));
        int texture = cover == null ? 0 : cover.texture;
        if (texture != this.coverShown) {
            this.coverOld = this.coverShown;
            this.coverShown = texture;
            this.coverFade = 0.0F;
        }
        this.coverFade = Math.min(1.0F, this.coverFade + this.dt * 3.5F);
        rect(x, y, x + size, y + size, TILE, 255);
        if (this.coverOld > 0 && this.coverFade < 1.0F) {
            image(this.coverOld, x, y, size, 1.0F - smooth(this.coverFade));
        }
        if (this.coverShown > 0) {
            float s = 0.97F + 0.03F * smooth(this.coverFade);
            float inset = size * (1.0F - s) / 2;
            image(this.coverShown, x + inset, y + inset, size - inset * 2, smooth(this.coverFade));
        } else if (song == null) {
            noteIcon(x + size / 2, y + size / 2, FAINT);
        }
        if (song != null) {
            float chv = hover(491, pointerFree() && in(x, y, size, size));
            rect(x, y, x + size, y + size, 0xFFFFFF, (int) (16 * chv));
            expandIcon(x + size - 9, y + 9, (int) (255 * chv));
        }
        y += size + 8;
        if (song == null) {
            centered("Nothing playing", x + size / 2, y + 4, DIM, 255, 0.85F);
            return;
        }
        text(this.font.trim(song.name, size, 1.0F), x, y, TEXT, 255, 1.0F);
        text(this.font.trim(song.artists, size, 0.78F), x, y + 11, DIM, 255, 0.78F);
        drawLyrics(x, y + 26, size, BAR_Y - 8);
    }

    private void drawLyrics(float x, float top, float w, float bottom) {
        Lyrics lyrics = this.player.lyrics();
        if (lyrics.lines.isEmpty()) {
            return;
        }
        long now = this.player.position();
        int current = lyrics.indexAt(now + 150L);
        boolean translation = MusicSettings.check("Translation", true);

        float[] offsets = new float[lyrics.lines.size() + 1];
        for (int i = 0; i < lyrics.lines.size(); i++) {
            Lyrics.Line line = lyrics.lines.get(i);
            offsets[i + 1] = offsets[i] + 12 + (translation && line.translation != null ? 9 : 0);
        }
        float target = current < 0 ? 0 : offsets[current];
        this.lyricCurrent = this.lyricCurrent < 0 ? target : ease(this.lyricCurrent, target, 7.0F);
        float middle = top + (bottom - top) * 0.35F;
        for (int i = 0; i < lyrics.lines.size(); i++) {
            float y = middle + offsets[i] - this.lyricCurrent;
            if (y < top - 20 || y > bottom) {
                continue;
            }
            float edge = fadeSoft(y, 12, top, bottom);
            boolean on = i == current;
            float distance = Math.abs(i - current);
            int a = (int) (255 * edge * (on ? 1.0F : Math.max(0.25F, 0.7F - distance * 0.12F)));
            Lyrics.Line line = lyrics.lines.get(i);
            float scale = on ? 0.92F : 0.82F;
            text(this.font.trim(line.text, w, scale), x, y, on ? TEXT : DIM, a, scale);
            if (translation && line.translation != null) {
                text(this.font.trim(line.translation, w, 0.7F), x, y + 10.5F, on ? blend(DIM, TEXT, 0.5F) : FAINT, a, 0.7F);
            }
        }
    }

    void drawScreenBackdrop(float w, float h, float amount) {
        if (amount <= 0.001F) {
            return;
        }
        float saved = this.alpha;
        this.alpha = amount;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        backdrop(0, 0, w, h);
        this.alpha = saved;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void backdrop(float x, float y, float w, float h) {
        Song song = this.player.current();
        Music.Cover cover = song == null ? null : this.music.cover(song.cover, 300);
        rect(x, y, x + w, y + h, blend(this.accent, 0x000000, 0.8F), 255);
        if (cover != null && cover.blurTexture > 0) {
            float u = 0;
            float v = 0;
            if (w > h) {
                v = (1.0F - h / w) / 2;
            } else {
                u = (1.0F - w / h) / 2;
            }

            float drift = (float) Math.sin(this.time * 0.15F) * 0.04F;
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, cover.blurTexture);
            GL11.glColor4f(0.62F, 0.62F, 0.62F, this.alpha);
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            t.addVertexWithUV(x, y + h, 0.0D, u + 0.05F + drift, 0.95F - v);
            t.addVertexWithUV(x + w, y + h, 0.0D, 0.95F - u + drift, 0.95F - v);
            t.addVertexWithUV(x + w, y, 0.0D, 0.95F - u + drift, v + 0.05F);
            t.addVertexWithUV(x, y, 0.0D, u + 0.05F + drift, v + 0.05F);
            t.draw();
        }
        gradient(x, y + h * 0.45F, x + w, y + h, 0x000000, 0, 0x000000, 150);
        rect(x, y, x + w, y + h, 0x000000, 50);
    }

    private void drawImmersive(float full, float base) {
        this.alpha = base * full;
        if (!this.screenBackdrop) {
            backdrop(0, 0, W, H);
        }
        float rise = (1.0F - full) * 26.0F;
        Song song = this.player.current();

        float back = hover(490, pointerFree() && in(6, 6, 24, 22));
        rect(6, 6, 30, 28, 0xFFFFFF, (int) (22 * back));
        chevronDown(18, 17, blend(0xC8CDD5, 0xFFFFFF, back));
        String source = this.player.source;
        boolean heart = "Heartbeat".equals(source);
        String label = source.isEmpty() ? "Now playing" : source;
        float lw = this.font.width(label, 0.85F);
        if (heart) {
            heart(W / 2.0F - lw / 2 - 7, 17, this.accent, 255);
        }
        text(label, W / 2.0F - lw / 2 + (heart ? 3 : 0), 13, 0xE6E9EE, 255, 0.85F);
        if (song == null) {
            return;
        }

        this.bigCoverScale = ease(this.bigCoverScale, this.player.paused() ? 0.9F : 1.0F, 6.0F);

        float grow = Math.max(1.0F, Math.min(1.6F, Math.min(W / (float) DEFAULT_W, H / (float) DEFAULT_H)));
        float full148 = 148 * grow;
        float size = full148 * this.bigCoverScale;
        float cx = 122 * grow + (W - DEFAULT_W * grow) * 0.25F;
        float cy = BAR_Y / 2.0F + 13 + rise;
        for (int k = 4; k >= 1; k--) {
            float g = k * 2.5F;
            rect(cx - size / 2 - g + 2, cy - size / 2 - g + 5, cx + size / 2 + g + 2, cy + size / 2 + g + 5, 0x000000, 18);
        }

        Music.Cover cover = this.music.cover(song.cover, coverPx(full148));
        rect(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2, 0x1D2026, 255);
        if (cover != null) {
            image(cover.texture, cx - size / 2, cy - size / 2, size, 1.0F);
        } else {
            noteIcon(cx, cy, FAINT);
        }

        float rx = cx + 126 * grow;
        float rw = Math.min(W - rx - 24, 360);
        text(this.font.trim(song.name, rw, 1.55F), rx, 46 + rise, 0xFFFFFF, 255, 1.55F);
        text(this.font.trim(song.artists, rw, 0.85F), rx, 65 + rise, 0xC8CDD5, 255, 0.85F);
        if (song.album.length() > 0) {
            text(this.font.trim(song.album, rw, 0.7F), rx, 77 + rise, 0x9AA2AE, 255, 0.7F);
        }
        drawBigLyrics(rx, 94 + rise, rw, BAR_Y - 6);
    }

    private void drawBigLyrics(float x, float top, float w, float bottom) {
        this.lyricHits.clear();
        Lyrics lyrics = this.player.lyrics();
        if (lyrics.lines.isEmpty()) {
            if (this.player.status().isEmpty()) {
                text("No lyrics", x, top + 30, 0x9AA2AE, 255, 0.9F);
            }
            return;
        }
        long now = this.player.position();
        int current = lyrics.indexAt(now + 150L);
        boolean translation = MusicSettings.check("Translation", true);
        int n = lyrics.lines.size();
        float[] offsets = new float[n + 1];
        String[][] wrapped = new String[n][];
        for (int i = 0; i < n; i++) {
            Lyrics.Line line = lyrics.lines.get(i);
            wrapped[i] = wrap2(line.text, w, 1.0F);
            offsets[i + 1] = offsets[i] + wrapped[i].length * 11 + 7 + (translation && line.translation != null ? 10 : 0);
        }
        float target = current < 0 ? 0 : offsets[current];
        this.bigLyricCurrent = this.bigLyricCurrent < 0 ? target : ease(this.bigLyricCurrent, target, 6.0F);
        float middle = top + (bottom - top) * 0.32F;
        for (int i = 0; i < n; i++) {
            float y = middle + offsets[i] - this.bigLyricCurrent;
            float h = offsets[i + 1] - offsets[i];
            if (y + h < top || y > bottom) {
                continue;
            }
            float edge = fadeSoft(y, h - 7, top, bottom);
            boolean on = i == current;
            float distance = Math.abs(i - current);
            float over = pointerFree() && in(x - 4, y - 3, w + 8, h - 1) ? 1.0F : 0.0F;
            int a = (int) (255 * edge * (on ? 1.0F : Math.max(0.3F, 0.62F - distance * 0.08F) + over * 0.25F));
            Lyrics.Line line = lyrics.lines.get(i);
            if (over > 0 && !on) {
                rect(x - 4, y - 3, x + w + 4, y + h - 4, 0xFFFFFF, (int) (14 * edge));
            }
            for (int k = 0; k < wrapped[i].length; k++) {
                text(wrapped[i][k], x, y + k * 11, on ? 0xFFFFFF : 0xC8CDD5, a, 1.0F);
            }
            if (translation && line.translation != null) {
                text(this.font.trim(line.translation, w, 0.75F), x, y + wrapped[i].length * 11, on ? 0xE6E9EE : 0x9AA2AE,
                        a, 0.75F);
            }
            if (edge > 0.5F) {
                this.lyricHits.add(new float[] {y - 3, y + h - 4, line.time});
            }
        }
    }

    private void drawBar(float full) {
        rect(0, BAR_Y, W, H, BG, (int) (255 * (1.0F - full * 0.8F)));
        Song song = this.player.current();
        long duration = song == null ? 0L : song.durationMs;
        long position = this.player.position();

        boolean overBar = in(0, BAR_Y - 4, W, 9) || this.dragging == DRAG_PROGRESS;
        this.barHover = ease(this.barHover, overBar && song != null ? 1.0F : 0.0F, 14.0F);
        float thick = 2.0F + this.barHover * 2.0F;
        float shown = this.seekPreview >= 0 ? this.seekPreview : duration <= 0 ? 0 : position / (float) duration;
        rect(0, BAR_Y, W, BAR_Y + thick, INK, 22);
        if (this.player.trialEnd() > 0 && duration > 0) {
            rect(W * this.player.trialStart() / (float) duration, BAR_Y, W * this.player.trialEnd() / (float) duration,
                    BAR_Y + thick, INK, 30);
        }
        if (song != null) {
            float loaded = this.player.loaded();
            if (loaded < 1.0F && this.player.trialEnd() < 0) {
                rect(0, BAR_Y, W * loaded, BAR_Y + thick, INK, 18);
            }
            rect(0, BAR_Y, W * shown, BAR_Y + thick, this.accent, 255);
            if (this.barHover > 0.05F) {
                float kx = W * shown;
                rect(kx - 3 * this.barHover, BAR_Y + thick / 2 - 3 * this.barHover, kx + 3 * this.barHover,
                        BAR_Y + thick / 2 + 3 * this.barHover, TEXT, (int) (255 * this.barHover));
            }
        }

        float cy = BAR_Y + 4 + BAR_H / 2.0F - 2;
        if (song != null) {
            Music.Cover cover = this.music.cover(song.cover, coverPx(26));
            rect(8, cy - 13, 34, cy + 13, TILE, 255);
            if (cover != null) {
                image(cover.texture, 8, cy - 13, 26, 1.0F);
            }
            text(this.font.trim(song.name, 108, 0.9F), 40, cy - 10, TEXT, 255, 0.9F);
            text(this.font.trim(song.artists, 108, 0.72F), 40, cy + 1, DIM, 255, 0.72F);
        }

        float mid = W / 2.0F;
        this.playMorph = ease(this.playMorph, this.player.paused() || song == null ? 0.0F : 1.0F, 16.0F);
        modeIcon(mid - 58, cy, iconButton(mid - 58, cy, 302));
        prevIcon(mid - 30, cy, iconButton(mid - 30, cy, 303), false);
        float hv = hover(304, in(mid - 10, cy - 10, 20, 20));
        float grow = hv * 1.5F;
        rect(mid - 10 - grow, cy - 10 - grow, mid + 10 + grow, cy + 10 + grow, this.accent, 255);
        playPause(mid, cy, this.playMorph);
        prevIcon(mid + 30, cy, iconButton(mid + 30, cy, 305), true);
        if (song != null) {
            boolean liked = this.music.isLiked(song);
            float lhv = iconButton(mid + 58, cy, 306);
            heart(mid + 58, cy, liked ? this.accent : blend(DIM, TEXT, lhv), this.music.loggedIn() ? 255 : 90);
        }

        heartbeatIcon(mid + 84, cy, iconButton(mid + 84, cy, 309));

        String clock = clock(this.seekPreview >= 0 ? (long) (this.seekPreview * duration) : position) + " / " + clock(duration);
        text(clock, mid + 99, cy - 4, DIM, song == null ? 0 : 255, 0.78F);
        float vx = W - 72;
        speaker(vx, cy, DIM);
        float vw = 58;
        boolean overVolume = in(vx + 10, cy - 6, vw + 4, 12) || this.dragging == DRAG_VOLUME;
        float vh = hover(307, overVolume);
        float vt = 2.0F + vh;
        rect(vx + 12, cy - vt / 2, vx + 12 + vw, cy + vt / 2, INK, 30);
        rect(vx + 12, cy - vt / 2, vx + 12 + vw * this.player.volume, cy + vt / 2, blend(TEXT, this.accent, vh), 255);
        if (vh > 0.05F) {
            float kx = vx + 12 + vw * this.player.volume;
            rect(kx - 2.5F * vh, cy - 2.5F * vh, kx + 2.5F * vh, cy + 2.5F * vh, TEXT, (int) (255 * vh));
        }
    }

    private void drawStatus(float full) {
        String status = this.player.status();
        if (status.equals("Loading")) {
            status = "";
        }
        if (status.isEmpty() && System.currentTimeMillis() - this.music.noticeAt < 2500L) {
            status = this.music.notice;
        }
        if (!status.isEmpty()) {
            this.statusShown = status;
        }
        this.statusFade = ease(this.statusFade, status.isEmpty() ? 0.0F : 1.0F, 8.0F);
        if (this.statusFade < 0.02F) {
            return;
        }
        float w = this.font.width(this.statusShown, 0.8F) + 16;
        float x = (MAIN_X + MAIN_R) / 2.0F + (W / 2.0F - (MAIN_X + MAIN_R) / 2.0F) * full - w / 2;
        float y = BAR_Y - 22 + (1.0F - this.statusFade) * 6;
        int a = (int) (255 * this.statusFade);
        rect(x - 1, y - 1, x + w + 1, y + 16, LINE, (int) (240 * this.statusFade));
        rect(x, y, x + w, y + 15, TOAST, (int) (250 * this.statusFade));
        rect(x, y, x + 2, y + 15, this.accent, a);
        text(this.statusShown, x + 9, y + 4, TEXT, a, 0.8F);
    }

    boolean press(float mx, float my, int button) {
        this.mouseX = mx;
        this.mouseY = my;
        if (mx < 0 || my < 0 || mx >= W || my >= H) {
            this.searchFocused = false;
            return false;
        }
        if (button == 3) {
            back();
            return true;
        }
        if (button != 0) {
            return true;
        }
        Song song = this.player.current();

        if (song != null && in(0, BAR_Y - 4, W, 9)) {
            this.dragging = DRAG_PROGRESS;
            this.seekPreview = progressAt(mx);
            return true;
        }
        float cy = BAR_Y + 4 + BAR_H / 2.0F - 2;
        float mid = W / 2.0F;
        if (in(W - 62, cy - 6, 62, 12)) {
            this.dragging = DRAG_VOLUME;
            MusicSettings.setVolume(this.player, volumeAt(mx));
            return true;
        }
        if (my >= BAR_Y) {
            if (in(mid - 10, cy - 10, 20, 20)) {
                if (song == null) {
                    playFirst();
                } else {
                    this.player.toggle();
                }
            } else if (in(mid - 40, cy - 10, 20, 20)) {
                this.player.previous();
            } else if (in(mid + 20, cy - 10, 20, 20)) {
                this.player.next();
            } else if (in(mid - 68, cy - 10, 20, 20)) {
                this.player.cycleMode();
                MusicSettings.store(this.player);
            } else if (in(mid + 48, cy - 10, 20, 20)) {
                this.music.toggleLike(song);
            } else if (in(mid + 74, cy - 10, 20, 20)) {
                if (!this.music.loggedIn()) {
                    open(P_LOGIN, 0, "Log in");
                } else if (this.music.inHeartbeat()) {
                    this.music.leaveHeartbeat();
                } else {
                    this.music.heartbeat();
                }
            } else if (song != null && in(4, BAR_Y + 3, 146, BAR_H - 3)) {
                this.immersive = !this.immersive;
            }
            return true;
        }
        if (this.immersive) {
            pressImmersive(mx, my);
            return true;
        }
        if (mx < SIDE_W) {
            this.searchFocused = false;
            if (in(SIDE_W - 25, 8, 16, 16)) {
                MusicSettings.toggleDark();
            } else if (in(SIDE_W - 43, 8, 16, 16)) {
                refresh();
            } else if (this.sideScroll.onBar(mx, my)) {
                startThumb(this.sideScroll, my);
            } else if (my >= BAR_Y - 20) {
                pressAccount();
            } else if (my >= 34) {
                startTouch(this.sideScroll, AREA_SIDE, mx, my);
            }
            return true;
        }
        if (mx < MAIN_R) {
            if (this.page == P_LOGIN || my < HEAD_H + 2) {
                pressMain(mx, my);
            } else if (this.listScroll.onBar(mx, my)) {
                startThumb(this.listScroll, my);
            } else {
                this.searchFocused = false;
                startTouch(this.listScroll, AREA_MAIN, mx, my);
            }
            return true;
        }
        float size = RIGHT_W - 22;
        if (song != null && in(MAIN_R + 11, 12, size, size)) {
            this.immersive = true;
        }
        return true;
    }

    private void pressImmersive(float mx, float my) {
        if (in(6, 6, 24, 22)) {
            this.immersive = false;
            return;
        }
        if (mx >= 240) {
            for (float[] line : this.lyricHits) {
                if (my >= line[0] && my < line[1]) {
                    this.player.seek((long) line[2]);
                    return;
                }
            }
        }
    }

    private void startThumb(Scroller scroller, float my) {
        this.dragging = DRAG_THUMB;
        this.held = scroller;
        float thumbY = scroller.thumbY();
        float thumbH = scroller.thumbH();

        this.grab = my >= thumbY && my < thumbY + thumbH ? my - thumbY : thumbH / 2;
        scroller.dragThumb(my, this.grab);
    }

    private void startTouch(Scroller scroller, int area, float mx, float my) {
        this.dragging = DRAG_TOUCH;
        this.held = scroller;
        this.pressArea = area;
        this.pressX = mx;
        this.pressY = my;
        this.moved = false;
    }

    private void pressAccount() {
        if (this.music.loggedIn()) {
            if (in(SIDE_W - 36, BAR_Y - 16, 30, 12)) {
                this.music.logout();
                open(P_HOME, 0, "For you");
            }
        } else {
            open(P_LOGIN, 0, "Log in");
        }
    }

    private void tapSidebar(float mx, float my) {
        float y = 34 - this.sideScroll.pos;
        for (Nav nav : navItems()) {
            float h = nav.header ? 18 : 15;
            if (!nav.header && my >= y && my < y + h && my >= 34) {
                open(nav.page, nav.id, nav.label);
                this.backStack.clear();
                return;
            }
            y += h;
        }
    }

    private void pressMain(float mx, float my) {
        this.selectAll = false;
        this.searchFocused = in(SEARCH_X, SEARCH_Y, SEARCH_W, 18);
        if (this.searchFocused || my < TOP_H) {
            return;
        }
        if (this.page == P_LOGIN) {
            if (this.qrState == 800 || this.qrError != null) {
                newQr();
            }
            return;
        }
        if (this.page != P_HOME && !this.backStack.isEmpty() && in(MAIN_X + 6, TOP_H + 3, 16, 18)) {
            goBack();
            return;
        }
        Music.SongList list = currentList();
        if (list == null || list.loading) {
            return;
        }
        if (this.page != P_HOME && in(MAIN_R - 52, TOP_H + 7, 40, 14) && !list.songs.isEmpty()) {
            this.player.play(list.songs, 0, this.pageTitle);
        }
    }

    private void tapMain(float mx, float my) {
        if (this.page == P_HOME) {
            tapHome(mx, my);
            return;
        }
        Music.SongList list = currentList();
        if (list == null || list.loading) {
            if (list == null && this.page != P_SEARCH) {
                open(P_LOGIN, 0, "Log in");
            }
            return;
        }
        if (my < HEAD_H + 2 || my >= BAR_Y - 2) {
            return;
        }
        int row = (int) Math.floor((my - HEAD_H - 5 + this.listScroll.pos) / ROW_H);
        if (row >= 0 && row < list.songs.size()) {
            if (this.page == P_QUEUE) {
                this.player.play(this.player.queue(), row, this.player.source);
            } else {
                this.player.play(list.songs, row, this.pageTitle);
            }
        }
    }

    void release(int button) {
        if (button != 0) {
            return;
        }
        if (this.dragging == DRAG_TOUCH) {
            if (this.moved) {
                this.held.letGo();
            } else if (this.pressArea == AREA_SIDE) {
                tapSidebar(this.pressX, this.pressY);
            } else if (this.pressArea == AREA_MAIN) {
                tapMain(this.pressX, this.pressY);
            }
        } else if (this.dragging == DRAG_PROGRESS && this.seekPreview >= 0) {
            Song song = this.player.current();
            if (song != null) {
                this.player.seek((long) (this.seekPreview * song.durationMs));
            }
        } else if (this.dragging == DRAG_VOLUME) {
            MusicSettings.store(this.player);
        }
        this.dragging = 0;
        this.held = null;
        this.seekPreview = -1.0F;
    }

    void wheel(float mx, float my, int delta) {
        float amount = -Math.signum(delta) * ROW_H * 2.2F;
        if (this.immersive && my < BAR_Y) {
            return;
        }
        if (mx < SIDE_W && my < BAR_Y) {
            this.sideScroll.push(amount);
        } else if (mx < MAIN_R && my < BAR_Y) {
            this.listScroll.push(amount);
        } else if (my >= BAR_Y && mx > W - 80) {
            this.player.volume = clamp(this.player.volume + Math.signum(delta) * 0.05F);
            MusicSettings.store(this.player);
        }
    }

    boolean key(char c, int code) {
        if (typing()) {
            if (code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER) {
                search();
            } else if (code == Keyboard.KEY_BACK) {
                if (this.selectAll || GuiScreen.isCtrlKeyDown()) {
                    this.query = "";
                } else if (!this.query.isEmpty()) {
                    this.query = this.query.substring(0, this.query.length() - 1);
                }
                this.selectAll = false;
            } else if (GuiScreen.isCtrlKeyDown() && code == Keyboard.KEY_A) {
                this.selectAll = true;
            } else if (GuiScreen.isCtrlKeyDown() && code == Keyboard.KEY_V) {
                type(GuiScreen.getClipboardString());
            } else if (code == Keyboard.KEY_ESCAPE) {
                this.searchFocused = false;
            } else if (c >= ' ' && c != 127) {
                type(String.valueOf(c));
            }
            return true;
        }
        if (code == Keyboard.KEY_ESCAPE && back()) {
            return true;
        }
        if (code == Keyboard.KEY_SPACE) {
            this.player.toggle();
            return true;
        }
        if (code == Keyboard.KEY_LEFT && this.player.current() != null) {
            this.player.seek(this.player.position() - 5000L);
            return true;
        }
        if (code == Keyboard.KEY_RIGHT && this.player.current() != null) {
            this.player.seek(this.player.position() + 5000L);
            return true;
        }
        return false;
    }

    private void type(String text) {
        if (text == null) {
            return;
        }
        text = text.replace('\n', ' ').replace('\r', ' ');
        this.query = (this.selectAll ? "" : this.query) + text;
        if (this.query.length() > 80) {
            this.query = this.query.substring(0, 80);
        }
        this.selectAll = false;
    }

    void searchFor(String words) {
        open(P_SEARCH, 0, "Search");
        this.query = words;
        search();
    }

    private void search() {
        final String words = this.query.trim();
        if (words.isEmpty()) {
            return;
        }
        open(P_SEARCH, 0, "Search");
        this.searched = words;
        this.searchFocused = false;
        this.searchList = this.music.list("search:" + words, words, new Music.Loader() {
            public List<Song> load() throws IOException {
                return MusicView.this.music.api.search(words, 0);
            }
        }, true);
        this.listScroll.reset();
        this.pageTime = 0.0F;
    }

    private void playFirst() {
        Music.SongList list = currentList();
        if (list != null && !list.loading && !list.songs.isEmpty()) {
            this.player.play(list.songs, 0);
        }
    }

    private void open(int page, long id, String title) {
        if (page == this.page && id == this.pageId && page != P_LOGIN) {
            return;
        }
        if ((page == P_DAILY || page == P_LIKED) && !this.music.loggedIn() && !this.music.api.loggedIn()) {
            page = P_LOGIN;
            title = "Log in";
        }
        if (this.page != P_LOGIN) {
            this.backStack.add(new Object[] {Integer.valueOf(this.page), Long.valueOf(this.pageId), this.pageTitle});
            if (this.backStack.size() > 20) {
                this.backStack.remove(0);
            }
        }
        show(page, id, title);
    }

    private void goBack() {
        if (this.backStack.isEmpty()) {
            return;
        }
        Object[] last = this.backStack.remove(this.backStack.size() - 1);
        show(((Integer) last[0]).intValue(), ((Long) last[1]).longValue(), (String) last[2]);
    }

    private void show(int page, long id, String title) {
        this.page = page;
        this.pageId = id;
        this.pageTitle = title;
        this.pageTime = 0.0F;
        this.autoPlay = false;
        this.listScroll.reset();
        this.searchFocused = page == P_SEARCH && this.searchList == null;
        if (page == P_LOGIN) {
            newQr();
        }
    }

    private Music.SongList currentList() {
        if ((this.page == P_DAILY || this.page == P_LIKED) && !this.music.loggedIn() && this.music.api.loggedIn()) {
            return new Music.SongList(this.pageTitle);
        }
        switch (this.page) {
            case P_SEARCH:
                return this.searchList;
            case P_DAILY:
                if (!this.music.loggedIn()) {
                    return null;
                }
                return this.music.list("daily", "Daily picks", new Music.Loader() {
                    public List<Song> load() throws IOException {
                        return MusicView.this.music.api.dailySongs();
                    }
                }, false);
            case P_LIKED:
                List<Playlist> lists = this.music.playlists;
                return lists.isEmpty() ? null : this.music.playlist(lists.get(0).id, "Liked songs");
            case P_QUEUE:
                Music.SongList queue = new Music.SongList("Now playing");
                queue.songs = this.player.queue();
                queue.loading = false;
                return queue;
            case P_CHART:
            case P_PLAYLIST:
                return this.music.playlist(this.pageId, this.pageTitle);
            default:
                return null;
        }
    }

    private void newQr() {
        if (this.qrBusy) {
            return;
        }
        this.qrBusy = true;
        this.qr = null;
        this.qrKey = null;
        this.qrState = 0;
        this.qrError = null;
        this.music.background(new Runnable() {
            public void run() {
                try {
                    String key = MusicView.this.music.api.qrKey();
                    MusicView.this.qr = QrCode.encodeText(NcmApi.qrContent(key), QrCode.Ecc.MEDIUM);
                    MusicView.this.qrKey = key;
                    MusicView.this.qrState = 801;
                } catch (IOException e) {
                    MusicView.this.qrError = "Couldn't reach NetEase. Click to try again";
                } finally {
                    MusicView.this.qrBusy = false;
                }
            }
        });
    }

    private void pollLogin() {
        if (this.page != P_LOGIN || this.qrKey == null || this.qrBusy || this.qrState == 800 || this.qrState == 803) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < this.qrNextCheck) {
            return;
        }
        this.qrNextCheck = now + 2000L;
        this.qrBusy = true;
        final String key = this.qrKey;
        this.music.background(new Runnable() {
            public void run() {
                try {
                    int code = MusicView.this.music.api.qrCheck(key);
                    if (code == 800 || code == 801 || code == 802 || code == 803) {
                        MusicView.this.qrState = code;
                    } else if (code == 8821) {
                        MusicView.this.qrError = "NetEase asked for a check. Wait a bit, then click";
                    }
                    if (code == 803) {
                        MusicView.this.music.refreshAccount();
                    }
                } catch (IOException ignored) {
                } finally {
                    MusicView.this.qrBusy = false;
                }
            }
        });
        if (this.qrState == 803 || this.music.loggedIn()) {
            open(P_HOME, 0, "For you");
        }
    }

    private float progressAt(float mx) {
        return clamp(mx / W);
    }

    private float volumeAt(float mx) {
        return clamp((mx - (W - 60)) / 58.0F);
    }

    private boolean in(float x, float y, float w, float h) {
        return this.mouseX >= x && this.mouseX < x + w && this.mouseY >= y && this.mouseY < y + h;
    }

    private float hover(int slot, boolean on) {
        this.hover[slot] = ease(this.hover[slot], on ? 1.0F : 0.0F, 16.0F);
        return this.hover[slot];
    }

    private float iconButton(float cx, float cy, int slot) {
        return hover(slot, in(cx - 10, cy - 10, 20, 20));
    }

    private float ease(float current, float target, float speed) {
        return current + (target - current) * (1.0F - (float) Math.exp(-speed * this.dt));
    }

    private static float smooth(float t) {
        t = clamp(t);

        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }

    private static float smoothInOut(float t) {
        t = clamp(t);
        return t < 0.5F ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    private static float clamp(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    private static float fadeEdge(float y, float h, float top, float bottom) {
        float upper = (y - top) / 8.0F + 1.0F;
        float lower = (bottom - (y + h)) / 8.0F + 1.0F;
        return y + h < top || y > bottom ? 0 : clamp(Math.min(upper, lower));
    }

    private static float fadeSoft(float y, float h, float top, float bottom) {
        float upper = (y - top + 2) / 10.0F;
        float lower = (bottom - (y + h) + 2) / 10.0F;
        return clamp(Math.min(upper, lower) + 0.0F) * (y + h < top || y > bottom ? 0 : 1);
    }

    private static int blend(int a, int b, float t) {
        int r = (int) ((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int g = (int) ((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }

    static String clock(long ms) {
        long s = Math.max(0L, ms / 1000L);
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }

    private void hint(String text, float top, float bottom) {
        float enter = smooth(Math.min(1.0F, this.pageTime / 0.3F));
        centered(text, (MAIN_X + MAIN_R) / 2.0F, (top + bottom) / 2.0F - 4 + (1 - enter) * 6, DIM, (int) (255 * enter), 0.85F);
    }

    private void loadingDots(float cx, float cy) {
        loadingDots(cx, cy, DIM);
    }

    private void loadingDots(float cx, float cy, int color) {
        for (int i = 0; i < 3; i++) {
            float phase = (float) Math.sin(this.time * 6.0F - i * 0.8F) * 0.5F + 0.5F;
            float s = 1.5F + phase * 1.2F;
            float x = cx - 8 + i * 8;
            rect(x - s, cy - s, x + s, cy + s, color, (int) (110 + 145 * phase));
        }
    }

    private void button(float x, float y, float w, float h, String label, boolean filled, int slot) {
        float hv = hover(slot, in(x, y, w, h));
        if (filled) {
            rect(x, y, x + w, y + h, this.accent, (int) (200 + 55 * hv));
        } else {
            rect(x, y, x + w, y + h, INK, (int) (14 + 16 * hv));
        }
        centered(label, x + w / 2, y + h / 2 - 3.5F, filled ? 0xFFFFFF : blend(DIM, TEXT, hv), 255, 0.75F);
    }

    private void accentButton(float x, float y, float w, float h, String label, int slot) {
        button(x, y, w, h, label, true, slot);
    }

    private void text(String text, float x, float y, int rgb, int a, float scale) {
        float h = MusicFont.SIZE * scale;
        if (y + h <= this.clipTop || y >= this.clipBottom) {
            return;
        }
        if (y < this.clipTop || y + h > this.clipBottom) {
            a = (int) (a * clamp(Math.min((y + h - this.clipTop) / h, (this.clipBottom - y) / h)));
        }
        int alpha = (int) (a * this.alpha);
        if (alpha < 3) {
            return;
        }
        this.font.draw(text, x, y, alpha << 24 | rgb & 0xFFFFFF, scale);
    }

    private void centered(String text, float cx, float y, int rgb, int a, float scale) {
        text(text, cx - this.font.width(text, scale) / 2, y, rgb, a, scale);
    }

    private void rect(float x1, float y1, float x2, float y2, int rgb, int a) {
        y1 = Math.max(y1, this.clipTop);
        y2 = Math.min(y2, this.clipBottom);
        int alpha = (int) (Math.max(0, Math.min(255, a)) * this.alpha);
        if (alpha <= 0 || x2 <= x1 || y2 <= y1) {
            return;
        }
        Tessellator t = beginQuads(rgb, alpha);
        quad(t, x1, y1, x2, y2);
        t.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private Tessellator beginQuads(int rgb, int a) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f((rgb >> 16 & 255) / 255.0F, (rgb >> 8 & 255) / 255.0F, (rgb & 255) / 255.0F, a / 255.0F);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        return t;
    }

    private static void quad(Tessellator t, float x1, float y1, float x2, float y2) {
        t.addVertex(x1, y2, 0.0D);
        t.addVertex(x2, y2, 0.0D);
        t.addVertex(x2, y1, 0.0D);
        t.addVertex(x1, y1, 0.0D);
    }

    private void gradient(float x1, float y1, float x2, float y2, int top, int topA, int bottom, int bottomA) {
        if (y1 < this.clipTop || y2 > this.clipBottom) {
            float c1 = Math.max(y1, this.clipTop);
            float c2 = Math.min(y2, this.clipBottom);
            if (c2 <= c1) {
                return;
            }
            float t1 = (c1 - y1) / (y2 - y1);
            float t2 = (c2 - y1) / (y2 - y1);
            int newTop = blend(top, bottom, t1);
            int newBottom = blend(top, bottom, t2);
            int newTopA = (int) (topA + (bottomA - topA) * t1);
            bottomA = (int) (topA + (bottomA - topA) * t2);
            top = newTop;
            bottom = newBottom;
            topA = newTopA;
            y1 = c1;
            y2 = c2;
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA(bottom >> 16 & 255, bottom >> 8 & 255, bottom & 255, (int) (bottomA * this.alpha));
        t.addVertex(x1, y2, 0.0D);
        t.addVertex(x2, y2, 0.0D);
        t.setColorRGBA(top >> 16 & 255, top >> 8 & 255, top & 255, (int) (topA * this.alpha));
        t.addVertex(x2, y1, 0.0D);
        t.addVertex(x1, y1, 0.0D);
        t.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private void triangle(float x1, float y1, float x2, float y2, float x3, float y3, int rgb, int a) {
        int alpha = (int) (a * this.alpha);
        if (alpha <= 0 || Math.min(y1, Math.min(y2, y3)) < this.clipTop || Math.max(y1, Math.max(y2, y3)) > this.clipBottom) {
            return;
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f((rgb >> 16 & 255) / 255.0F, (rgb >> 8 & 255) / 255.0F, (rgb & 255) / 255.0F, alpha / 255.0F);
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_TRIANGLES);
        t.addVertex(x1, y1, 0.0D);
        t.addVertex(x2, y2, 0.0D);
        t.addVertex(x3, y3, 0.0D);
        t.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private void image(int texture, float x, float y, float size, float a) {
        if (texture <= 0 || a <= 0.01F) {
            return;
        }

        float x2 = MusicFont.snap(x + size);
        x = MusicFont.snap(x);
        float yEnd = MusicFont.snap(y + size);
        y = MusicFont.snap(y);
        float h = yEnd - y;
        float y1 = Math.max(y, this.clipTop);
        float y2 = Math.min(yEnd, this.clipBottom);
        if (y2 <= y1 || h <= 0) {
            return;
        }
        float v1 = (y1 - y) / h;
        float v2 = (y2 - y) / h;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, a * this.alpha);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x, y2, 0.0D, 0.0D, v2);
        t.addVertexWithUV(x2, y2, 0.0D, 1.0D, v2);
        t.addVertexWithUV(x2, y1, 0.0D, 1.0D, v1);
        t.addVertexWithUV(x, y1, 0.0D, 0.0D, v1);
        t.draw();
    }

    private void chevronLeft(float cx, float cy, int color) {
        for (int i = 0; i < 5; i++) {
            rect(cx + 1.5F - i, cy - 5 + i, cx + 3 - i, cy - 3.5F + i, color, 255);
            rect(cx + 1.5F - i, cy + 3.5F - i, cx + 3 - i, cy + 5 - i, color, 255);
        }
    }

    private void chevronDown(float cx, float cy, int color) {
        for (int i = 0; i < 5; i++) {
            rect(cx - 5 + i, cy - 2.5F + i, cx - 3.5F + i, cy - 1 + i, color, 255);
            rect(cx + 3.5F - i, cy - 2.5F + i, cx + 5 - i, cy - 1 + i, color, 255);
        }
    }

    private void expandIcon(float cx, float cy, int a) {
        if (a < 3) {
            return;
        }
        rect(cx - 4, cy - 4, cx - 1, cy - 3, 0xFFFFFF, a);
        rect(cx - 4, cy - 4, cx - 3, cy - 1, 0xFFFFFF, a);
        rect(cx + 1, cy + 3, cx + 4, cy + 4, 0xFFFFFF, a);
        rect(cx + 3, cy + 1, cx + 4, cy + 4, 0xFFFFFF, a);
    }

    private void playPause(float cx, float cy, float morph) {
        int a = 255;

        float p = 1.0F - morph;
        if (p > 0.01F) {
            float s = 0.6F + 0.4F * p;
            triangle(cx - 3 * s + 1, cy - 4.5F * s, cx - 3 * s + 1, cy + 4.5F * s, cx + 5 * s + 1, cy, 0xFFFFFF, (int) (a * p));
        }
        if (morph > 0.01F) {
            float s = 0.6F + 0.4F * morph;
            rect(cx - 3.5F * s, cy - 4.5F * s, cx - 1.0F * s, cy + 4.5F * s, 0xFFFFFF, (int) (a * morph));
            rect(cx + 1.0F * s, cy - 4.5F * s, cx + 3.5F * s, cy + 4.5F * s, 0xFFFFFF, (int) (a * morph));
        }
    }

    private void prevIcon(float cx, float cy, float hv, boolean next) {
        int color = blend(DIM, TEXT, hv);
        float d = next ? 1 : -1;
        float s = 1.0F + hv * 0.12F;
        triangle(cx - 3 * d * s, cy - 4 * s, cx - 3 * d * s, cy + 4 * s, cx + 3 * d * s, cy, color, 255);
        float bx = cx + 3 * d * s;
        rect(Math.min(bx, bx + 1.5F * d), cy - 4 * s, Math.max(bx, bx + 1.5F * d), cy + 4 * s, color, 255);
    }

    private void modeIcon(float cx, float cy, float hv) {
        int color = blend(DIM, TEXT, hv);
        MusicPlayer.Mode mode = this.player.mode;
        if (mode == MusicPlayer.Mode.LIST) {
            for (int i = -1; i <= 1; i++) {
                rect(cx - 4.5F, cy + i * 3 - 0.6F, cx + 4.5F, cy + i * 3 + 0.6F, color, 255);
            }
        } else if (mode == MusicPlayer.Mode.ONE) {
            rect(cx - 5, cy - 4, cx + 5, cy - 3, color, 255);
            rect(cx - 5, cy + 3, cx + 5, cy + 4, color, 255);
            rect(cx - 5, cy - 4, cx - 4, cy + 4, color, 255);
            rect(cx + 4, cy - 4, cx + 5, cy + 4, color, 255);
            rect(cx - 0.5F, cy - 2, cx + 0.7F, cy + 2, this.accent, 255);
        } else {
            for (int i = 0; i < 9; i++) {
                float x = cx - 4.5F + i;
                float up = cy + 3 - i * 0.75F;
                float down = cy - 3 + i * 0.75F;
                rect(x, up - 0.6F, x + 1, up + 0.6F, color, 255);
                rect(x, down - 0.6F, x + 1, down + 0.6F, color, 255);
            }
        }
        if (hv > 0.05F) {
            centered(mode.label, cx, cy + 9, DIM, (int) (255 * hv), 0.65F);
        }
    }

    private void heartbeatIcon(float cx, float cy, float hv) {
        boolean on = this.music.inHeartbeat();
        boolean busy = this.music.heartbeatBusy;
        int color = on ? this.accent : blend(DIM, TEXT, hv);
        int a = this.music.loggedIn() ? 255 : 90;
        if (busy) {
            a = (int) (a * (0.55F + 0.45F * (float) Math.sin(System.currentTimeMillis() / 140.0)));
        }
        heart(cx, cy, color, a);

        float y = cy - 1.0F;
        rect(cx - 3.5F, y, cx - 1.5F, y + 0.7F, BG, a);
        rect(cx - 1.5F, y - 2.0F, cx - 0.8F, y + 0.7F, BG, a);
        rect(cx - 0.8F, y - 2.0F, cx - 0.1F, y - 1.3F, BG, a);
        rect(cx - 0.1F, y - 2.0F, cx + 0.6F, y + 2.7F, BG, a);
        rect(cx + 0.6F, y + 2.0F, cx + 1.3F, y + 2.7F, BG, a);
        rect(cx + 1.3F, y, cx + 2.0F, y + 2.7F, BG, a);
        rect(cx + 2.0F, y, cx + 3.5F, y + 0.7F, BG, a);
        if (hv > 0.05F) {
            centered(on ? "Heartbeat on" : "Heartbeat", cx, cy + 9, DIM, (int) (255 * hv), 0.65F);
        }
    }

    private void heart(float cx, float cy, int color, int a) {
        float x = cx - 3.5F;
        float y = cy - 3.0F;
        rect(x, y + 1, x + 7, y + 3, color, a);
        rect(x + 0.5F, y, x + 3, y + 1, color, a);
        rect(x + 4, y, x + 6.5F, y + 1, color, a);
        rect(x + 0.5F, y + 3, x + 6.5F, y + 4, color, a);
        rect(x + 1.5F, y + 4, x + 5.5F, y + 5, color, a);
        rect(x + 2.5F, y + 5, x + 4.5F, y + 6, color, a);
    }

    private void speaker(float x, float cy, int color) {
        rect(x - 4, cy - 1.5F, x - 2, cy + 1.5F, color, 255);
        triangle(x - 2, cy - 1.5F, x - 2, cy + 1.5F, x + 2, cy + 4, color, 255);
        triangle(x - 2, cy - 1.5F, x + 2, cy + 4, x + 2, cy - 4, color, 255);
        if (this.player.volume > 0.0F) {
            rect(x + 3.5F, cy - 1.5F, x + 4.5F, cy + 1.5F, color, 255);
        }
        if (this.player.volume > 0.5F) {
            rect(x + 5.5F, cy - 3, x + 6.5F, cy + 3, color, 255);
        }
    }

    private void magnifier(float x, float y, int color) {
        rect(x, y, x + 6, y + 1, color, 255);
        rect(x, y + 5, x + 6, y + 6, color, 255);
        rect(x, y, x + 1, y + 6, color, 255);
        rect(x + 5, y, x + 6, y + 6, color, 255);
        rect(x + 6, y + 6, x + 7.5F, y + 7.5F, color, 255);
        rect(x + 7, y + 7, x + 8.5F, y + 8.5F, color, 255);
    }

    private void noteIconSmall(float cx, float cy, int color, int a) {
        rect(cx - 3, cy + 1, cx - 0.5F, cy + 3.5F, color, a);
        rect(cx - 1, cy - 4.5F, cx - 0.5F, cy + 1.5F, color, a);
        rect(cx - 1, cy - 4.5F, cx + 3, cy - 3.5F, color, a);
    }

    private static int coverPx(float units) {
        float ppu = MusicFont.ppu() > 0 ? MusicFont.ppu() : 2.0F;
        float need = units * ppu;
        int[] sizes = {64, 100, 140, 200, 300, 400, 600};
        for (int s : sizes) {
            if (s >= need) {
                return s;
            }
        }
        return 600;
    }

    private void noteIcon(float cx, float cy, int color) {
        rect(cx - 6, cy + 2, cx - 1, cy + 7, color, 255);
        rect(cx - 2, cy - 9, cx - 1, cy + 3, color, 255);
        rect(cx - 2, cy - 9, cx + 6, cy - 7, color, 255);
    }

    private void equalizer(float x, float y, int a) {
        boolean moving = !this.player.paused();
        for (int i = 0; i < 3; i++) {
            float h = moving ? 3 + 5 * (0.5F + 0.5F * (float) Math.sin(this.time * (7.0F + i * 2.3F) + i * 1.7F)) : 3;
            rect(x + i * 3, y + 8 - h, x + i * 3 + 2, y + 8, this.accent, a);
        }
    }

    private static final class Nav {
        final String label;
        final int page;
        final long id;
        final boolean header;

        Nav(String label, int page, long id) {
            this.label = label;
            this.page = page;
            this.id = id;
            this.header = false;
        }

        Nav(String header) {
            this.label = header;
            this.page = -1;
            this.id = 0;
            this.header = true;
        }
    }

    private static final class Hit {
        final float x;
        final float y;
        final float w;
        final float h;
        final int kind;
        final Object data;

        Hit(float x, float y, float w, float h, int kind, Object data) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.kind = kind;
            this.data = data;
        }
    }

    private static final class Scroller {
        private static final float GLIDE = 13.0F;
        private static final float FLING = 4.0F;

        float pos;
        float target;
        float max;
        float barAlpha;
        private float idle;
        private float rate = GLIDE;
        private boolean held;
        private float velocity;
        private float lastHeld;
        private float hot;

        private float barX = -100.0F;
        private float barTop;
        private float barBottom;
        private float content = 1.0F;

        void push(float amount) {
            float next = this.target + amount;
            if (next < 0) {
                next = Math.max(-28.0F, this.target + amount * 0.35F);
            } else if (next > this.max) {
                next = Math.min(this.max + 28.0F, this.target + amount * 0.35F);
            }
            this.target = next;
            this.rate = GLIDE;
            this.idle = 0.0F;
            this.barAlpha = 1.0F;
        }

        void hold() {
            this.held = true;
            this.target = this.pos;
            this.lastHeld = this.pos;
            this.velocity = 0.0F;
            this.rate = GLIDE;
        }

        void holdAt(float wanted) {
            float next = wanted;
            if (next < 0) {
                next = Math.max(-40.0F, next * 0.35F);
            } else if (next > this.max) {
                next = Math.min(this.max + 40.0F, this.max + (next - this.max) * 0.35F);
            }
            this.target = next;
            this.pos = next;
            this.idle = 0.0F;
            this.barAlpha = 1.0F;
        }

        void letGo() {
            this.held = false;
            this.rate = FLING;
            float glide = this.velocity / FLING;
            this.target = Math.max(-28.0F, Math.min(this.max + 28.0F, this.pos + glide));
            this.idle = 0.0F;
        }

        void update(float max, float dt) {
            this.max = max;
            if (this.held) {
                if (dt > 0) {
                    float now = (this.target - this.lastHeld) / dt;
                    this.velocity = this.velocity * 0.6F + now * 0.4F;
                }
                this.lastHeld = this.target;
                return;
            }
            this.idle += dt;

            if (this.idle > 0.08F && (this.target < 0 || this.target > max)) {
                float inside = Math.max(0.0F, Math.min(max, this.target));
                this.target += (inside - this.target) * (1.0F - (float) Math.exp(-12.0F * dt));
                this.rate = GLIDE;
            }
            this.pos += (this.target - this.pos) * (1.0F - (float) Math.exp(-this.rate * dt));
            if (Math.abs(this.target - this.pos) < 0.05F) {
                this.pos = this.target;
                this.rate = GLIDE;
            }
            if (this.idle > 1.2F) {
                this.barAlpha = Math.max(0.0F, this.barAlpha - dt * 2.0F);
            }
        }

        void reset() {
            this.pos = 0.0F;
            this.target = 0.0F;
            this.velocity = 0.0F;
            this.held = false;
        }

        float thumbH() {
            float view = this.barBottom - this.barTop;
            return Math.max(16.0F, view * view / Math.max(view, this.content));
        }

        float thumbY() {
            float view = this.barBottom - this.barTop;
            return this.barTop + (view - thumbH()) * Math.max(0.0F, Math.min(1.0F, this.max <= 0 ? 0 : this.pos / this.max));
        }

        boolean onBar(float mx, float my) {
            return this.max > 0 && mx >= this.barX - 6 && mx < this.barX + 5 && my >= this.barTop && my < this.barBottom;
        }

        void dragThumb(float my, float grab) {
            float view = this.barBottom - this.barTop;
            float room = Math.max(1.0F, view - thumbH());
            float t = Math.max(0.0F, Math.min(1.0F, (my - grab - this.barTop) / room));
            this.target = t * this.max;
            this.pos = this.target;
            this.rate = GLIDE;
            this.idle = 0.0F;
            this.barAlpha = 1.0F;
        }

        void drawBar(float x, float top, float bottom, float content, int color, int track, float alpha, boolean pointed) {
            this.barX = x;
            this.barTop = top;
            this.barBottom = bottom;
            this.content = content;
            if (this.max <= 0) {
                return;
            }
            this.hot += ((pointed ? 1.0F : 0.0F) - this.hot) * 0.25F;
            float w = 2.0F + this.hot * 1.5F;
            float shown = Math.max(0.45F, Math.max(this.barAlpha, this.hot));
            float h = thumbH();
            float y = thumbY();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            Tessellator t = Tessellator.instance;
            GL11.glColor4f((track >> 16 & 255) / 255.0F, (track >> 8 & 255) / 255.0F, (track & 255) / 255.0F,
                    (0.05F + 0.05F * this.hot) * alpha);
            t.startDrawingQuads();
            quad(t, x + 2 - w, top, x + 2, bottom);
            t.draw();
            GL11.glColor4f((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F, 0.85F * shown * alpha);
            t.startDrawingQuads();
            quad(t, x + 2 - w, y, x + 2, y + h);
            t.draw();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
        }
    }
}
