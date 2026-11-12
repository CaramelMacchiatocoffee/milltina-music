package net.milltina.music;

import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public final class GuiMusic extends GuiScreen {
    private static final float FILL = 0.88F;
    private static final float OPEN_SECONDS = 0.22F;
    private static final float CLOSE_SECONDS = 0.14F;

    private static MusicView view;

    private float scale = 1.0F;
    private float left;
    private float top;
    private long openedAt;
    private long closingAt;

    static MusicView view() {
        if (view == null) {
            view = new MusicView(Music.get());
        }
        return view;
    }

    public GuiMusic(String search) {
        MusicView v = view();
        v.size(MusicView.DEFAULT_W, MusicView.DEFAULT_H);
        v.opened();
        if (search != null) {
            v.searchFor(search);
        }
    }

    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        if (this.openedAt == 0L) {
            this.openedAt = System.nanoTime();
        }
    }

    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    public boolean doesGuiPauseGame() {
        return false;
    }

    private void layout() {
        this.scale = scaleFor(view().immersiveAmount());
        this.left = (this.width - MusicView.DEFAULT_W * this.scale) / 2.0F;
        this.top = (this.height - MusicView.DEFAULT_H * this.scale) / 2.0F;
    }

    private float scaleFor(float immersive) {
        float fill = FILL + (1.0F - FILL) * immersive;
        return Math.max(0.5F, Math.min(4.0F, Math.min(this.width * fill / MusicView.DEFAULT_W,
                this.height * fill / MusicView.DEFAULT_H)));
    }

    private float shown() {
        long now = System.nanoTime();
        if (this.closingAt != 0L) {
            return 1.0F - Math.min(1.0F, (now - this.closingAt) / 1.0E9F / CLOSE_SECONDS);
        }
        return Math.min(1.0F, (now - this.openedAt) / 1.0E9F / OPEN_SECONDS);
    }

    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Music.frame(this.mc);
        float t = shown();
        if (this.closingAt != 0L && t <= 0.0F) {
            this.mc.displayGuiScreen(null);
            return;
        }
        float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
        if (this.mc.theWorld != null && MusicMod.backdrop != null) {
            MusicMod.backdrop.draw(this.mc, this.width, this.height);
        }
        drawRect(0, 0, this.width, this.height, (int) (90 * eased) << 24);
        MusicView v = view();

        v.size(MusicView.DEFAULT_W, MusicView.DEFAULT_H);
        v.screenBackdrop = true;
        v.drawScreenBackdrop(this.width, this.height, v.immersiveAmount() * eased);
        layout();

        float f = this.mc.displayWidth / (float) this.width;
        float immersive = v.immersiveAmount();
        GL11.glPushMatrix();
        if (eased >= 1.0F && (immersive <= 0.0F || immersive >= 1.0F)) {
            this.left = Math.round(this.left * f) / f;
            this.top = Math.round(this.top * f) / f;
            GL11.glTranslatef(this.left, this.top, 0.0F);
            GL11.glScalef(this.scale, this.scale, 1.0F);
            MusicFont.pixels(this.scale * f, true);
        } else {
            float lift = (1.0F - eased) * 10.0F;
            float grow = 0.965F + 0.035F * eased;
            GL11.glTranslatef(this.width / 2.0F, this.height / 2.0F + lift, 0.0F);
            GL11.glScalef(this.scale * grow, this.scale * grow, 1.0F);
            GL11.glTranslatef(-MusicView.DEFAULT_W / 2.0F, -MusicView.DEFAULT_H / 2.0F, 0.0F);

            MusicFont.pixels(scaleFor(v.immersiveWanted() ? 1.0F : 0.0F) * f, false);
        }
        v.draw(unitX(), unitY(), eased);
        MusicFont.pixels(0.0F, false);
        GL11.glPopMatrix();
    }

    private float unitX() {
        float x = Mouse.getX() * this.width / (float) this.mc.displayWidth;
        return (x - this.left) / this.scale;
    }

    private float unitY() {
        float y = this.height - Mouse.getY() * this.height / (float) this.mc.displayHeight - 1;
        return (y - this.top) / this.scale;
    }

    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (this.closingAt != 0L) {
            return;
        }
        if (!view().press(unitX(), unitY(), button) && button == 0 && !view().immersiveOpen()) {
            close();
        }
    }

    protected void mouseMovedOrUp(int mouseX, int mouseY, int which) {
        if (which >= 0) {
            view().release(which);
        }
    }

    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            view().wheel(unitX(), unitY(), wheel);
        }
    }

    protected void keyTyped(char c, int code) {
        if (this.closingAt != 0L) {
            return;
        }
        MusicView v = view();
        if (code == Keyboard.KEY_ESCAPE && !v.typing()) {
            if (!v.back()) {
                close();
            }
            return;
        }
        if (v.key(c, code)) {
            return;
        }
        if (code == MusicSettings.playerKey && code != Keyboard.KEY_NONE) {
            close();
        }
    }

    public void close() {
        if (this.closingAt == 0L) {
            this.closingAt = System.nanoTime() - (long) ((1.0F - shown()) * CLOSE_SECONDS * 1.0E9F);
        }
    }
}
