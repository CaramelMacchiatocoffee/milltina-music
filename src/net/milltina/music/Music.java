package net.milltina.music;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public final class Music {
    private static Music instance;

    public final NcmApi api;
    public final MusicPlayer player;
    private final ExecutorService network = Executors.newFixedThreadPool(3, new ThreadFactory() {
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "Music network");
            thread.setDaemon(true);
            return thread;
        }
    });

    public volatile long uid;
    public volatile String nickname;
    public volatile List<Playlist> playlists = Collections.emptyList();
    private volatile Set<Long> liked = Collections.emptySet();
    private volatile Shelf recommended;

    public volatile boolean heartbeatBusy;

    public volatile String notice = "";
    public volatile long noticeAt;
    private final Map<String, SongList> lists = new LinkedHashMap<String, SongList>();
    private final Map<String, Cover> covers = new LinkedHashMap<String, Cover>(64, 0.75F, true);

    public static Music get() {
        if (instance == null) {
            File dir = new File(Minecraft.getMinecraft().mcDataDir, "music");
            NcmApi api = new NcmApi(new File(dir, "login.properties"));
            instance = new Music(api);
        }
        return instance;
    }

    public static Music peek() {
        return instance;
    }

    private Music(NcmApi api) {
        this.api = api;
        this.player = new MusicPlayer(api, new AlAudioOut());
        restoreLast();
        refreshAccount();
    }

    public boolean loggedIn() {
        return this.uid != 0L;
    }

    public void refreshAccount() {
        if (!this.api.loggedIn()) {
            return;
        }
        this.network.execute(new Runnable() {
            public void run() {
                try {
                    Object[] account = Music.this.api.account();
                    if (account == null) {
                        Music.this.uid = 0L;
                        return;
                    }
                    long id = ((Long) account[0]).longValue();
                    Music.this.nickname = (String) account[1];
                    if (Music.this.uid != id) {
                        Music.this.recommended = null;
                    }
                    Music.this.uid = id;
                    Music.this.playlists = Music.this.api.playlists(id);
                    Music.this.liked = new HashSet<Long>(Music.this.api.likedIds(id));
                } catch (IOException ignored) {
                }
            }
        });
    }

    public void refresh() {
        this.recommended = null;
        synchronized (this.lists) {
            this.lists.clear();
        }
        refreshAccount();
    }

    public void logout() {
        this.api.logout();
        this.uid = 0L;
        this.nickname = null;
        this.playlists = Collections.emptyList();
        this.liked = Collections.emptySet();
        this.recommended = null;
        synchronized (this.lists) {
            this.lists.remove("daily");
            this.lists.remove("liked");
        }
    }

    public Shelf recommended() {
        Shelf shelf = this.recommended;
        if (shelf != null && shelf.error == null) {
            return shelf;
        }
        final Shelf next = new Shelf();
        this.recommended = next;
        this.network.execute(new Runnable() {
            public void run() {
                try {
                    if (Music.this.api.loggedIn()) {
                        try {
                            Map<String, List<Playlist>> blocks = Music.this.api.homeBlocks();
                            List<Playlist> first = blocks.get(NcmApi.HOME_BLOCKS[0][0]);
                            if (first != null) {
                                Map<String, List<Playlist>> more = new LinkedHashMap<String, List<Playlist>>();
                                for (int i = 1; i < NcmApi.HOME_BLOCKS.length; i++) {
                                    List<Playlist> lists = blocks.get(NcmApi.HOME_BLOCKS[i][0]);
                                    if (lists != null) {
                                        more.put(NcmApi.HOME_BLOCKS[i][1], lists);
                                    }
                                }
                                next.more = more;
                                next.items = first;
                                return;
                            }
                        } catch (IOException ignored) {
                        }
                    }
                    next.items = Music.this.api.recommendedPlaylists();
                } catch (IOException e) {
                    next.error = e.getMessage() == null ? e.toString() : e.getMessage();
                } finally {
                    next.loading = false;
                }
            }
        });
        return next;
    }

    public static final class Shelf {
        public volatile List<Playlist> items = Collections.emptyList();

        public volatile Map<String, List<Playlist>> more = Collections.emptyMap();
        public volatile boolean loading = true;
        public volatile String error;
    }

    public void heartbeat() {
        if (!loggedIn() || this.playlists.isEmpty() || this.heartbeatBusy) {
            return;
        }
        Song now = this.player.current();
        long seed;
        if (now != null && isLiked(now)) {
            seed = now.id;
        } else {
            List<Long> ids = new ArrayList<Long>(this.liked);
            if (ids.isEmpty()) {
                notice("Like a few songs first");
                return;
            }
            seed = ids.get(new java.util.Random().nextInt(ids.size())).longValue();
            now = null;
        }
        final long seedId = seed;
        final Song seedSong = now;
        final long likedList = this.playlists.get(0).id;
        this.heartbeatBusy = true;
        this.network.execute(new Runnable() {
            public void run() {
                try {
                    List<Song> found = Music.this.api.heartbeat(seedId, likedList);
                    Song first = seedSong;
                    if (first == null) {
                        List<Song> one = Music.this.api.songs(Collections.singletonList(Long.valueOf(seedId)));
                        first = one.isEmpty() ? null : one.get(0);
                    }
                    List<Song> queue = new ArrayList<Song>();
                    if (first != null) {
                        queue.add(first);
                    }
                    for (Song song : found) {
                        if (song.id != seedId) {
                            queue.add(song);
                        }
                    }
                    if (queue.isEmpty()) {
                        notice("Heartbeat found nothing");
                    } else {
                        Music.this.player.continueWith(queue, "Heartbeat");
                    }
                } catch (IOException e) {
                    notice("Heartbeat failed: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
                } finally {
                    Music.this.heartbeatBusy = false;
                }
            }
        });
    }

    public boolean inHeartbeat() {
        return "Heartbeat".equals(this.player.source) && this.player.current() != null;
    }

    public void leaveHeartbeat() {
        if (!loggedIn() || this.playlists.isEmpty() || this.heartbeatBusy) {
            return;
        }
        final Playlist likedList = this.playlists.get(0);
        final Song now = this.player.current();
        this.heartbeatBusy = true;
        this.network.execute(new Runnable() {
            public void run() {
                try {
                    List<Song> liked = Music.this.api.playlistSongs(likedList.id);
                    List<Song> queue = new ArrayList<Song>();
                    int at = -1;
                    for (int i = 0; now != null && i < liked.size(); i++) {
                        if (liked.get(i).id == now.id) {
                            at = i;
                            break;
                        }
                    }
                    if (at >= 0) {
                        queue.addAll(liked.subList(at, liked.size()));
                        queue.addAll(liked.subList(0, at));
                    } else {
                        if (now != null) {
                            queue.add(now);
                        }
                        queue.addAll(liked);
                    }
                    if (queue.isEmpty()) {
                        notice("No liked songs");
                    } else {
                        Music.this.player.continueWith(queue, likedList.name);
                        notice("Heartbeat off");
                    }
                } catch (IOException e) {
                    notice("Couldn't load liked songs: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
                } finally {
                    Music.this.heartbeatBusy = false;
                }
            }
        });
    }

    public void notice(String text) {
        this.notice = text;
        this.noticeAt = System.currentTimeMillis();
    }

    public boolean isLiked(Song song) {
        return song != null && this.liked.contains(Long.valueOf(song.id));
    }

    public void toggleLike(final Song song) {
        if (song == null || !loggedIn()) {
            return;
        }
        final boolean like = !isLiked(song);
        Set<Long> next = new HashSet<Long>(this.liked);
        if (like) {
            next.add(Long.valueOf(song.id));
        } else {
            next.remove(Long.valueOf(song.id));
        }
        this.liked = next;
        this.network.execute(new Runnable() {
            public void run() {
                try {
                    Music.this.api.like(song.id, like);
                } catch (IOException ignored) {
                }
            }
        });
    }

    public interface Loader {
        List<Song> load() throws IOException;
    }

    public SongList list(String key, String title, final Loader loader, boolean reload) {
        final SongList list;
        synchronized (this.lists) {
            SongList old = this.lists.get(key);
            if (old != null && !reload && old.error == null) {
                return old;
            }
            list = new SongList(title);
            this.lists.put(key, list);
        }
        this.network.execute(new Runnable() {
            public void run() {
                try {
                    list.songs = loader.load();
                } catch (IOException e) {
                    list.error = e.getMessage() == null ? e.toString() : e.getMessage();
                } finally {
                    list.loading = false;
                }
            }
        });
        return list;
    }

    public SongList playlist(final long id, String title) {
        return list("playlist:" + id, title, new Loader() {
            public List<Song> load() throws IOException {
                return Music.this.api.playlistSongs(id);
            }
        }, false);
    }

    public void background(Runnable task) {
        this.network.execute(task);
    }

    public static final class SongList {
        public final String title;
        public volatile List<Song> songs = Collections.emptyList();
        public volatile boolean loading = true;
        public volatile String error;

        SongList(String title) {
            this.title = title;
        }
    }

    public Cover cover(String url, int size) {
        if (url == null) {
            return null;
        }
        String key = url + "?param=" + size + "y" + size;
        final Cover cover;
        synchronized (this.covers) {
            Cover known = this.covers.get(key);
            if (known != null) {
                if (known.pixels != null) {
                    known.upload();
                }
                return known.texture > 0 ? known : null;
            }
            cover = new Cover();
            this.covers.put(key, cover);
            trimCovers();
        }
        final String address = key;
        this.network.execute(new Runnable() {
            public void run() {
                cover.load(address);
            }
        });
        return null;
    }

    private void trimCovers() {
        long bytes = 0L;
        for (Cover c : this.covers.values()) {
            bytes += (long) c.width * c.height * 4L;
        }
        if (this.covers.size() <= 200 && bytes <= 48L << 20) {
            return;
        }
        Iterator<Map.Entry<String, Cover>> it = this.covers.entrySet().iterator();
        while ((this.covers.size() > 160 || bytes > 40L << 20) && it.hasNext()) {
            Cover old = it.next().getValue();
            bytes -= (long) old.width * old.height * 4L;
            if (old.texture > 0) {
                TextureUtil.deleteTexture(old.texture);
            }
            if (old.blurTexture > 0) {
                TextureUtil.deleteTexture(old.blurTexture);
            }
            it.remove();
        }
    }

    public static final class Cover {
        private static final int BLUR = 32;

        public int texture;

        public int blurTexture;

        public int accent = 0xE5484D;
        private volatile ByteBuffer pixels;
        private ByteBuffer blurPixels;
        private int width;
        private int height;

        private static ByteBuffer blur(int[] argb, int w, int h) {
            float[][] c = new float[3][BLUR * BLUR];
            for (int y = 0; y < BLUR; y++) {
                int y0 = y * h / BLUR;
                int y1 = Math.max(y0 + 1, (y + 1) * h / BLUR);
                for (int x = 0; x < BLUR; x++) {
                    int x0 = x * w / BLUR;
                    int x1 = Math.max(x0 + 1, (x + 1) * w / BLUR);
                    float r = 0;
                    float g = 0;
                    float b = 0;
                    int n = 0;
                    for (int yy = y0; yy < y1; yy += 2) {
                        for (int xx = x0; xx < x1; xx += 2) {
                            int p = argb[yy * w + xx];
                            r += p >> 16 & 255;
                            g += p >> 8 & 255;
                            b += p & 255;
                            n++;
                        }
                    }
                    c[0][y * BLUR + x] = r / n;
                    c[1][y * BLUR + x] = g / n;
                    c[2][y * BLUR + x] = b / n;
                }
            }
            float[] tmp = new float[BLUR * BLUR];
            for (int pass = 0; pass < 3; pass++) {
                for (float[] ch : c) {
                    for (int horizontal = 0; horizontal < 2; horizontal++) {
                        for (int y = 0; y < BLUR; y++) {
                            for (int x = 0; x < BLUR; x++) {
                                float sum = 0;
                                for (int k = -2; k <= 2; k++) {
                                    int sx = horizontal == 0 ? Math.max(0, Math.min(BLUR - 1, x + k)) : x;
                                    int sy = horizontal == 1 ? Math.max(0, Math.min(BLUR - 1, y + k)) : y;
                                    sum += ch[sy * BLUR + sx];
                                }
                                tmp[y * BLUR + x] = sum / 5;
                            }
                        }
                        System.arraycopy(tmp, 0, ch, 0, tmp.length);
                    }
                }
            }
            ByteBuffer out = BufferUtils.createByteBuffer(BLUR * BLUR * 4);
            for (int i = 0; i < BLUR * BLUR; i++) {
                out.put((byte) c[0][i]).put((byte) c[1][i]).put((byte) c[2][i]).put((byte) 255);
            }
            out.flip();
            return out;
        }

        void load(String address) {
            try {
                fetch(address);
            } catch (IOException e) {
                if (address.startsWith("https://")) {
                    try {
                        fetch("http://" + address.substring("https://".length()));
                        return;
                    } catch (IOException again) {
                        e = again;
                    }
                }
                System.out.println("[Music] cover failed: " + address + " " + e);
            }
        }

        private void fetch(String address) throws IOException {
            {
                HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                InputStream in = connection.getInputStream();
                BufferedImage image;
                try {
                    image = ImageIO.read(in);
                } finally {
                    in.close();
                }
                if (image == null) {
                    return;
                }
                int w = image.getWidth();
                int h = image.getHeight();
                int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
                ByteBuffer buffer = BufferUtils.createByteBuffer(w * h * 4);
                double weight = 0.0D;
                double r = 0.0D;
                double g = 0.0D;
                double b = 0.0D;
                for (int i = 0; i < argb.length; i++) {
                    int p = argb[i];
                    int pr = p >> 16 & 255;
                    int pg = p >> 8 & 255;
                    int pb = p & 255;
                    buffer.put((byte) pr).put((byte) pg).put((byte) pb).put((byte) 255);
                    if (i % 7 == 0) {
                        float[] hsb = java.awt.Color.RGBtoHSB(pr, pg, pb, null);

                        double w2 = hsb[1] * hsb[1] * (1.0D - Math.abs(hsb[2] - 0.65D)) + 0.01D;
                        r += pr * w2;
                        g += pg * w2;
                        b += pb * w2;
                        weight += w2;
                    }
                }
                buffer.flip();
                float[] hsb = java.awt.Color.RGBtoHSB((int) (r / weight), (int) (g / weight), (int) (b / weight), null);

                this.accent = java.awt.Color.HSBtoRGB(hsb[0], Math.max(0.45F, Math.min(0.85F, hsb[1] * 1.4F)),
                        Math.max(0.75F, hsb[2])) & 0xFFFFFF;
                this.width = w;
                this.height = h;
                this.blurPixels = blur(argb, w, h);
                this.pixels = buffer;
            }
        }

        void upload() {
            ByteBuffer data = this.pixels;
            if (data == null) {
                return;
            }
            this.pixels = null;
            this.texture = make(data, this.width, this.height);
            if (this.blurPixels != null) {
                this.blurTexture = make(this.blurPixels, BLUR, BLUR);
                this.blurPixels = null;
            }
        }

        private static int make(ByteBuffer data, int w, int h) {
            int id = TextureUtil.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
            return id;
        }
    }

    public static void beforeSoundUnload() {
        if (instance != null) {
            instance.player.releaseAudio();
        }
    }

    public static void frame(Minecraft mc) {
        if (instance == null) {
            if (restoreTried || mc.theWorld == null || !MusicSettings.enabled) {
                return;
            }
            restoreTried = true;
            if (!new File(new File(mc.mcDataDir, "music"), "last.json").isFile()) {
                return;
            }
            get();
        }
        instance.player.master = mc.gameSettings.getSoundLevel(net.minecraft.client.audio.SoundCategory.MASTER);
        MusicSettings.apply(instance.player);
        instance.autosave(false);
    }

    public static void leftWorld() {
        if (instance != null) {
            instance.player.pause();
            instance.autosave(true);
        }
    }

    private static boolean restoreTried;
    private long savedSong = -1L;
    private boolean savedPaused;
    private long savedAt;

    private void autosave(boolean now) {
        Song song = this.player.current();
        long id = song == null ? 0L : song.id;
        boolean paused = this.player.paused();
        long time = System.currentTimeMillis();
        if (!now && id == this.savedSong && paused == this.savedPaused && (paused || time - this.savedAt < 10000L)) {
            return;
        }
        this.savedSong = id;
        this.savedPaused = paused;
        this.savedAt = time;
        if (song == null) {
            return;
        }
        List<Song> queue = this.player.queue();
        int index = Math.max(0, this.player.index());

        int from = Math.max(0, Math.min(index - 500, queue.size() - 1000));
        int to = Math.min(queue.size(), from + 1000);
        com.google.gson.JsonArray songs = new com.google.gson.JsonArray();
        for (int i = from; i < to; i++) {
            songs.add(save(queue.get(i)));
        }
        if (songs.size() == 0) {
            songs.add(save(song));
            index = from = 0;
        }
        final com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("index", index - from);
        root.addProperty("position", this.player.position());
        root.addProperty("source", this.player.source);
        root.add("songs", songs);
        final File file = new File(new File(Minecraft.getMinecraft().mcDataDir, "music"), "last.json");
        this.network.execute(new Runnable() {
            public void run() {
                try {
                    file.getParentFile().mkdirs();
                    java.io.Writer out = new java.io.OutputStreamWriter(new java.io.FileOutputStream(file), "UTF-8");
                    try {
                        out.write(root.toString());
                    } finally {
                        out.close();
                    }
                } catch (IOException ignored) {
                }
            }
        });
    }

    private static com.google.gson.JsonObject save(Song song) {
        com.google.gson.JsonObject one = new com.google.gson.JsonObject();
        one.addProperty("id", song.id);
        one.addProperty("name", song.name);
        one.addProperty("artists", song.artists);
        one.addProperty("album", song.album);
        if (song.cover != null) {
            one.addProperty("cover", song.cover);
        }
        one.addProperty("duration", song.durationMs);
        one.addProperty("fee", song.fee);
        return one;
    }

    private void restoreLast() {
        File file = new File(new File(Minecraft.getMinecraft().mcDataDir, "music"), "last.json");
        if (!file.isFile()) {
            return;
        }
        try {
            InputStream in = new java.io.FileInputStream(file);
            com.google.gson.JsonObject root;
            try {
                root = new com.google.gson.JsonParser().parse(new java.io.InputStreamReader(in, "UTF-8")).getAsJsonObject();
            } finally {
                in.close();
            }
            List<Song> songs = new ArrayList<Song>();
            for (com.google.gson.JsonElement element : root.getAsJsonArray("songs")) {
                com.google.gson.JsonObject one = element.getAsJsonObject();
                songs.add(new Song(NcmApi.number(one, "id"), NcmApi.string(one, "name"), NcmApi.string(one, "artists"),
                        NcmApi.string(one, "album"), NcmApi.string(one, "cover"), NcmApi.number(one, "duration"),
                        (int) NcmApi.number(one, "fee")));
            }
            String source = NcmApi.string(root, "source");
            this.player.restore(songs, (int) NcmApi.number(root, "index"), NcmApi.number(root, "position"), source);
            Song song = this.player.current();
            this.savedSong = song == null ? -1L : song.id;
            this.savedPaused = true;
        } catch (IOException e) {
        } catch (RuntimeException e) {
        }
    }

    static List<Song> copy(List<Song> songs) {
        return new ArrayList<Song>(songs);
    }
}
