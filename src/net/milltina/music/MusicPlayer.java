package net.milltina.music;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

public final class MusicPlayer {
    public enum Mode {
        LIST("List"), ONE("Repeat one"), SHUFFLE("Shuffle");

        public final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    public static final String[] LEVELS = {"standard", "higher", "exhigh"};

    public final NcmApi api;
    private final AudioOut out;
    private final Object lock = new Object();
    private final Random random = new Random();

    private List<Song> queue = new ArrayList<Song>();
    private int index = -1;
    private final List<Integer> history = new ArrayList<Integer>();

    private volatile Song current;
    private volatile int generation;
    private volatile boolean paused;
    private volatile long seekRequest = -1L;
    private volatile Mp3Data data;
    private volatile String status = "";
    private volatile Lyrics lyrics = Lyrics.NONE;
    private volatile long trialStart = -1L;
    private volatile long trialEnd = -1L;
    private volatile float loaded;
    private volatile int failures;

    private volatile long positionMs;
    private volatile long positionAt;
    private volatile boolean sounding;

    public volatile String source = "";

    private volatile boolean restored;
    public volatile Mode mode = Mode.LIST;
    public volatile String level = "exhigh";

    public volatile float volume = 0.5F;

    public volatile float master = 1.0F;

    public MusicPlayer(NcmApi api, AudioOut out) {
        this.api = api;
        this.out = out;
        Thread worker = new Thread(new Runnable() {
            public void run() {
                loop();
            }
        }, "Music player");
        worker.setDaemon(true);
        worker.start();
    }

    public void play(List<Song> list, int start) {
        play(list, start, "");
    }

    public void play(List<Song> list, int start, String source) {
        synchronized (this.lock) {
            this.queue = new ArrayList<Song>(list);
            this.history.clear();
            this.failures = 0;
            this.source = source == null ? "" : source;
            begin(start);
        }
    }

    public void continueWith(List<Song> list, String source) {
        synchronized (this.lock) {
            Song now = this.current;
            if (now != null && !list.isEmpty() && list.get(0).id == now.id) {
                this.queue = new ArrayList<Song>(list);
                this.history.clear();
                this.failures = 0;
                this.source = source == null ? "" : source;
                this.index = 0;
                return;
            }
        }
        play(list, 0, source);
    }

    public void toggle() {
        synchronized (this.lock) {
            if (this.restored) {
                this.restored = false;
                long at = this.positionMs;
                int index = this.index;
                this.index = -1;
                begin(index);
                if (at > 1000L) {
                    this.seekRequest = at;
                    this.positionMs = at;
                }
                return;
            }
        }
        if (this.current != null) {
            this.paused = !this.paused;
            wake();
        }
    }

    public void pause() {
        if (this.current != null && !this.paused) {
            this.paused = true;
            wake();
        }
    }

    public void restore(List<Song> list, int at, long ms, String source) {
        synchronized (this.lock) {
            if (this.current != null || at < 0 || at >= list.size()) {
                return;
            }
            this.queue = new ArrayList<Song>(list);
            this.index = at;
            this.current = this.queue.get(at);
            this.paused = true;
            this.positionMs = Math.max(0L, ms);
            this.positionAt = System.nanoTime();
            this.sounding = false;
            this.source = source == null ? "" : source;
            this.status = "";
            this.restored = true;
        }
    }

    public void next() {
        advance(false);
    }

    public void previous() {
        if (position() > 3000L) {
            seek(0L);
            return;
        }
        synchronized (this.lock) {
            if (this.queue.isEmpty()) {
                return;
            }
            if (this.mode == Mode.SHUFFLE && !this.history.isEmpty()) {
                int back = this.history.remove(this.history.size() - 1).intValue();
                begin(back);
                this.history.remove(this.history.size() - 1);
            } else {
                begin((this.index - 1 + this.queue.size()) % this.queue.size());
            }
        }
    }

    public void seek(long ms) {
        if (this.current == null) {
            return;
        }
        long low = Math.max(0L, this.trialStart);
        long high = this.trialEnd > 0L ? this.trialEnd - 1000L : this.current.durationMs - 500L;
        ms = Math.max(low, Math.min(high, ms));
        this.seekRequest = ms;
        this.positionMs = ms;
        this.positionAt = System.nanoTime();
        wake();
    }

    public void stop() {
        synchronized (this.lock) {
            this.current = null;
            this.index = -1;
            this.generation++;
            closeData();
            this.lock.notifyAll();
        }
    }

    public void cycleMode() {
        Mode[] modes = Mode.values();
        this.mode = modes[(this.mode.ordinal() + 1) % modes.length];
    }

    public Song current() {
        return this.current;
    }

    public boolean paused() {
        return this.paused;
    }

    public long position() {
        long at = this.positionMs;
        if (this.sounding && !this.paused && this.seekRequest < 0L) {
            long since = (System.nanoTime() - this.positionAt) / 1000000L;

            at += Math.min(since, 250L);
        }
        Song song = this.current;
        return song == null ? 0L : Math.max(0L, Math.min(at, song.durationMs));
    }

    public String status() {
        return this.status;
    }

    public Lyrics lyrics() {
        return this.lyrics;
    }

    public long trialStart() {
        return this.trialStart;
    }

    public long trialEnd() {
        return this.trialEnd;
    }

    public float loaded() {
        return this.loaded;
    }

    public List<Song> queue() {
        synchronized (this.lock) {
            return new ArrayList<Song>(this.queue);
        }
    }

    public int index() {
        synchronized (this.lock) {
            return this.index;
        }
    }

    private void begin(int at) {
        if (at < 0 || at >= this.queue.size()) {
            return;
        }
        if (this.index >= 0) {
            this.history.add(Integer.valueOf(this.index));
            if (this.history.size() > 200) {
                this.history.remove(0);
            }
        }
        this.index = at;
        this.current = this.queue.get(at);
        this.restored = false;
        this.paused = false;
        this.seekRequest = -1L;
        this.positionMs = 0L;
        this.positionAt = System.nanoTime();
        this.sounding = false;
        this.lyrics = Lyrics.NONE;
        this.trialStart = -1L;
        this.trialEnd = -1L;
        this.loaded = 0.0F;
        this.status = "Loading";
        this.generation++;
        closeData();
        this.lock.notifyAll();
    }

    private void advance(boolean ended) {
        synchronized (this.lock) {
            if (this.queue.isEmpty()) {
                return;
            }
            int next;
            if (ended && this.mode == Mode.ONE) {
                next = this.index;
            } else if (this.mode == Mode.SHUFFLE && this.queue.size() > 1) {
                next = this.random.nextInt(this.queue.size() - 1);
                if (next >= this.index) {
                    next++;
                }
            } else {
                next = (this.index + 1) % this.queue.size();
            }
            begin(next);
        }
    }

    private void closeData() {
        Mp3Data old = this.data;
        if (old != null) {
            old.close();
        }
    }

    private void wake() {
        synchronized (this.lock) {
            this.lock.notifyAll();
        }
    }

    private float gain() {
        return this.volume * this.volume * this.master;
    }

    private void loop() {
        int seen = 0;
        while (true) {
            int generation;
            Song song;
            synchronized (this.lock) {
                while (this.generation == seen) {
                    this.out.poll(this.paused, gain());
                    try {
                        this.lock.wait(50L);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                generation = seen = this.generation;
                song = this.current;
            }
            this.out.flush();
            if (song == null) {
                this.status = "";
                continue;
            }
            int result;
            try {
                result = playSong(song, generation);
            } catch (RuntimeException e) {
                this.status = "Error: " + e;
                result = STOPPED;
            }
            if (this.generation != generation) {
                continue;
            }
            if (result == ENDED) {
                this.failures = 0;
                advance(true);
            } else if (result == SKIPPED) {
                int count = ++this.failures;
                if (count >= Math.min(5, Math.max(1, queue().size()))) {
                    this.status = "Can't play " + count + " songs in a row";
                    this.sounding = false;
                } else {
                    pause(1500L, generation);
                    if (this.generation == generation) {
                        advance(false);
                    }
                }
            } else {
                this.sounding = false;
            }
        }
    }

    private static final int ENDED = 0;
    private static final int SKIPPED = 1;
    private static final int STOPPED = 2;

    private int playSong(final Song song, final int generation) {
        fetchLyrics(song, generation);
        NcmApi.SongUrl url;
        try {
            url = this.api.songUrl(song.id, this.level);
        } catch (IOException e) {
            this.status = "Network error: " + e.getMessage();
            return STOPPED;
        }
        if (this.generation != generation) {
            return STOPPED;
        }
        if (url == null) {
            this.status = "Not playable here (no rights or region)";
            return SKIPPED;
        }
        if (url.trialEnd > 0L) {
            this.trialStart = url.trialStart;
            this.trialEnd = url.trialEnd;
        }
        long offset = Math.max(0L, url.trialStart);
        Mp3Data mp3 = new Mp3Data(url.url, url.size);
        this.data = mp3;
        if (this.generation != generation) {
            mp3.close();
            return STOPPED;
        }
        AudioOut.Stop stop = new AudioOut.Stop() {
            public boolean now() {
                MusicPlayer.this.out.poll(MusicPlayer.this.paused, gain());
                return MusicPlayer.this.generation != generation || MusicPlayer.this.seekRequest >= 0L;
            }
        };
        try {
            long base = 0L;
            int skip = 0;
            Bitstream bits = new Bitstream(mp3.from(0));
            Decoder decoder = new Decoder();
            int rate = 44100;
            playing:
            while (true) {
                while (true) {
                    if (this.generation != generation) {
                        return STOPPED;
                    }
                    long seek = this.seekRequest;
                    if (seek >= 0L) {
                        this.seekRequest = -1L;
                        long[] at = mp3.seek(seek - offset);
                        if (this.generation != generation) {
                            return STOPPED;
                        }
                        if (at == null) {
                            return ENDED;
                        }
                        this.out.flush();
                        closeQuietly(bits);
                        bits = new Bitstream(mp3.from((int) at[0]));
                        decoder = new Decoder();

                        skip = at[1] == at[2] ? 0 : 1;
                        base = at[2];
                        this.positionMs = offset + base;
                        this.positionAt = System.nanoTime();
                    }
                    Header header;
                    SampleBuffer samples;
                    try {
                        header = bits.readFrame();
                        if (header == null) {
                            break;
                        }
                        samples = (SampleBuffer) decoder.decodeFrame(header, bits);
                        bits.closeFrame();
                    } catch (Exception e) {
                        if (this.generation != generation) {
                            return STOPPED;
                        }
                        if (mp3.error() != null) {
                            this.status = "Download failed: " + mp3.error().getMessage();
                            return STOPPED;
                        }

                        bits.closeFrame();
                        continue;
                    }
                    this.loaded = mp3.loaded(url.size);
                    if (skip > 0) {
                        skip--;
                        continue;
                    }
                    rate = samples.getSampleFrequency();
                    if (!this.out.write(samples.getBuffer(), samples.getBufferLength(), rate, samples.getChannelCount(), stop)) {
                        continue;
                    }
                    publish(offset + base, rate);
                }
                if (mp3.error() != null && this.generation == generation) {
                    this.status = "Download failed: " + mp3.error().getMessage();
                    return STOPPED;
                }

                while (!this.out.drained()) {
                    if (stop.now()) {
                        if (this.generation != generation) {
                            return STOPPED;
                        }

                        continue playing;
                    }
                    publish(offset + base, rate);
                    pause(10L, generation);
                }
                return this.generation == generation ? ENDED : STOPPED;
            }
        } finally {
            mp3.close();
            if (this.data == mp3) {
                this.data = null;
            }
        }
    }

    private void publish(long base, int rate) {
        this.positionMs = base + this.out.played() * 1000L / rate;
        this.positionAt = System.nanoTime();
        this.sounding = true;
        if (!this.status.isEmpty()) {
            this.status = this.trialEnd > 0L ? "Trial only (VIP song)" : "";
        }
    }

    private void fetchLyrics(final Song song, final int generation) {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    String[] text = MusicPlayer.this.api.lyrics(song.id);
                    if (MusicPlayer.this.generation == generation) {
                        MusicPlayer.this.lyrics = Lyrics.parse(text[0], text[1]);
                    }
                } catch (IOException ignored) {
                }
            }
        }, "Music lyrics");
        thread.setDaemon(true);
        thread.start();
    }

    private void pause(long ms, int generation) {
        long end = System.currentTimeMillis() + ms;
        while (this.generation == generation && System.currentTimeMillis() < end) {
            this.out.poll(this.paused, gain());
            try {
                Thread.sleep(5L);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void closeQuietly(Bitstream bits) {
        try {
            bits.close();
        } catch (Exception ignored) {
        }
    }

    public void releaseAudio() {
        this.out.release();
    }
}
