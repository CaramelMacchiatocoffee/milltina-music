package net.milltina.music;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class Mp3Data {
    private static final int TIMEOUT = 15000;

    private static final int[][] BITRATES = {
        {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0},
        {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0}};
    private static final int[][] RATES = {{44100, 48000, 32000}, {22050, 24000, 16000}, {11025, 12000, 8000}};

    private final String url;
    private byte[] data;
    private volatile int length;
    private volatile boolean done;
    private volatile boolean closed;
    private volatile IOException error;

    private int[] frameOffsets = new int[4096];
    private long[] frameTimes = new long[4096];
    private int frames;
    private int scanAt = -1;
    private long scanTimeUs;

    Mp3Data(String url, long expectedSize) {
        this.url = url;
        this.data = new byte[(int) Math.max(1 << 16, Math.min(expectedSize > 0 ? expectedSize : 8 << 20, 200 << 20))];
        Thread thread = new Thread(new Runnable() {
            public void run() {
                download();
            }
        }, "Music download");
        thread.setDaemon(true);
        thread.start();
    }

    private void download() {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(this.url).openConnection();
            connection.setConnectTimeout(TIMEOUT);
            connection.setReadTimeout(TIMEOUT);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            int status = connection.getResponseCode();
            if (status >= 400) {
                throw new IOException("HTTP " + status);
            }
            InputStream in = connection.getInputStream();
            try {
                byte[] buffer = new byte[32768];
                int n;
                while (!this.closed && (n = in.read(buffer)) > 0) {
                    synchronized (this) {
                        if (this.length + n > this.data.length) {
                            byte[] bigger = new byte[Math.max(this.data.length * 2, this.length + n)];
                            System.arraycopy(this.data, 0, bigger, 0, this.length);
                            this.data = bigger;
                        }
                        System.arraycopy(buffer, 0, this.data, this.length, n);
                        this.length += n;
                        notifyAll();
                    }
                }
            } finally {
                in.close();
            }
        } catch (IOException e) {
            this.error = e;
        } finally {
            synchronized (this) {
                this.done = true;
                notifyAll();
            }
        }
    }

    void close() {
        synchronized (this) {
            this.closed = true;
            notifyAll();
        }
    }

    IOException error() {
        return this.error;
    }

    float loaded(long expectedSize) {
        if (this.done) {
            return 1.0F;
        }
        return expectedSize > 0 ? Math.min(1.0F, this.length / (float) expectedSize) : 0.0F;
    }

    private synchronized boolean waitFor(int offset) {
        while (offset >= this.length && !this.done && !this.closed) {
            try {
                wait(200L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return offset < this.length && !this.closed;
    }

    InputStream from(final int start) {
        return new InputStream() {
            private int at = start;

            public int read() {
                if (!waitFor(this.at)) {
                    return -1;
                }
                synchronized (Mp3Data.this) {
                    return Mp3Data.this.data[this.at++] & 255;
                }
            }

            public int read(byte[] buffer, int offset, int count) {
                if (count == 0) {
                    return 0;
                }
                if (!waitFor(this.at)) {
                    return -1;
                }
                synchronized (Mp3Data.this) {
                    int n = Math.min(count, Mp3Data.this.length - this.at);
                    System.arraycopy(Mp3Data.this.data, this.at, buffer, offset, n);
                    this.at += n;
                    return n;
                }
            }

            public int available() {
                return Math.max(0, Mp3Data.this.length - this.at);
            }
        };
    }

    long[] seek(long ms) {
        long targetUs = ms * 1000L;
        if (this.scanAt < 0) {
            this.scanAt = skipTag();
            if (this.scanAt < 0) {
                return null;
            }
        }
        while (this.frames == 0 || this.frameTimes[this.frames - 1] <= targetUs) {
            if (!scanFrame()) {
                break;
            }
        }
        if (this.frames == 0) {
            return null;
        }

        int low = 0;
        int high = this.frames - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (this.frameTimes[middle] <= targetUs) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        if (this.frameTimes[low] + 100000L < targetUs && this.done) {
            return null;
        }

        int from = Math.max(0, low - 1);
        return new long[] {this.frameOffsets[from], this.frameTimes[from] / 1000L, this.frameTimes[low] / 1000L};
    }

    private int skipTag() {
        if (!waitFor(9)) {
            return -1;
        }
        synchronized (this) {
            if (this.data[0] == 'I' && this.data[1] == 'D' && this.data[2] == '3') {
                return 10 + ((this.data[6] & 127) << 21 | (this.data[7] & 127) << 14 | (this.data[8] & 127) << 7 | this.data[9] & 127);
            }
        }
        return 0;
    }

    private boolean scanFrame() {
        while (true) {
            if (!waitFor(this.scanAt + 3)) {
                return false;
            }
            int b1;
            int b2;
            synchronized (this) {
                if ((this.data[this.scanAt] & 255) != 255 || (this.data[this.scanAt + 1] & 0xE0) != 0xE0) {
                    this.scanAt++;
                    continue;
                }
                b1 = this.data[this.scanAt + 1] & 255;
                b2 = this.data[this.scanAt + 2] & 255;
            }
            int version = b1 >> 3 & 3;
            int layer = b1 >> 1 & 3;
            int bitrateIndex = b2 >> 4;
            int rateIndex = b2 >> 2 & 3;
            if (version == 1 || layer != 1 || bitrateIndex == 0 || bitrateIndex == 15 || rateIndex == 3) {
                this.scanAt++;
                continue;
            }
            boolean mpeg1 = version == 3;
            int rate = RATES[mpeg1 ? 0 : version == 2 ? 1 : 2][rateIndex];
            int bitrate = BITRATES[mpeg1 ? 0 : 1][bitrateIndex] * 1000;
            int padding = b2 >> 1 & 1;
            int size = (mpeg1 ? 144 : 72) * bitrate / rate + padding;
            int samples = mpeg1 ? 1152 : 576;
            if (this.frames == this.frameOffsets.length) {
                this.frameOffsets = java.util.Arrays.copyOf(this.frameOffsets, this.frames * 2);
                this.frameTimes = java.util.Arrays.copyOf(this.frameTimes, this.frames * 2);
            }
            this.frameOffsets[this.frames] = this.scanAt;
            this.frameTimes[this.frames] = this.scanTimeUs;
            this.frames++;
            this.scanTimeUs += samples * 1000000L / rate;
            this.scanAt += size;
            return true;
        }
    }
}
