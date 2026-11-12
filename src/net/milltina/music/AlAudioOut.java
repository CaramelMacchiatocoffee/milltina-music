package net.milltina.music;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;

final class AlAudioOut implements AudioOut {
    static final Object LOCK = new Object();
    private static final int BUFFERS = 12;

    private static final int FRAMES_PER_BUFFER = 4096;

    private int source;
    private final int[] buffers = new int[BUFFERS];
    private final int[] bufferSamples = new int[BUFFERS];

    private final boolean[] queued = new boolean[BUFFERS];
    private int free;
    private final int[] freeList = new int[BUFFERS];
    private ShortBuffer chunk;
    private ByteBuffer chunkBytes;
    private int chunkRate;
    private int chunkChannels;
    private long played;
    private long queuedSamples;
    private int inQueue;
    private boolean paused;
    private float volume = -1.0F;
    private final IntBuffer one = BufferUtils.createIntBuffer(1);

    public boolean write(short[] pcm, int length, int rate, int channels, Stop stop) {
        int at = 0;
        while (at < length) {
            if (this.chunk == null || rate != this.chunkRate || channels != this.chunkChannels) {
                if (this.chunk != null && this.chunk.position() > 0 && !submit(stop)) {
                    return false;
                }
                this.chunkBytes = BufferUtils.createByteBuffer(FRAMES_PER_BUFFER * channels * 2).order(ByteOrder.nativeOrder());
                this.chunk = this.chunkBytes.asShortBuffer();
                this.chunkRate = rate;
                this.chunkChannels = channels;
            }
            int n = Math.min(length - at, this.chunk.remaining());
            this.chunk.put(pcm, at, n);
            at += n;
            if (!this.chunk.hasRemaining() && !submit(stop)) {
                return false;
            }
        }
        return true;
    }

    private boolean submit(Stop stop) {
        while (true) {
            if (stop.now()) {
                return false;
            }
            synchronized (LOCK) {
                if (open()) {
                    reclaim();
                    if (this.free > 0) {
                        int index = this.freeList[--this.free];
                        int samples = this.chunk.position();
                        this.chunk.flip();
                        AL10.alBufferData(this.buffers[index], this.chunkChannels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16,
                                this.chunk, this.chunkRate);
                        this.chunk.clear();
                        this.bufferSamples[index] = samples / this.chunkChannels;
                        this.queuedSamples += samples / this.chunkChannels;
                        this.inQueue++;
                        this.queued[index] = true;
                        this.one.clear();
                        this.one.put(this.buffers[index]).flip();
                        AL10.alSourceQueueBuffers(this.source, this.one);
                        if (!this.paused && AL10.alGetSourcei(this.source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                            AL10.alSourcePlay(this.source);
                        }
                        return true;
                    }
                }
            }
            sleep();
        }
    }

    private boolean open() {
        if (this.source != 0) {
            return true;
        }
        if (!AL.isCreated()) {
            return false;
        }
        AL10.alGetError();
        this.one.clear();
        AL10.alGenSources(this.one);
        if (AL10.alGetError() != AL10.AL_NO_ERROR) {
            return false;
        }
        this.source = this.one.get(0);
        IntBuffer names = BufferUtils.createIntBuffer(BUFFERS);
        AL10.alGenBuffers(names);
        for (int i = 0; i < BUFFERS; i++) {
            this.buffers[i] = names.get(i);
            this.freeList[i] = i;
            this.queued[i] = false;
        }
        this.free = BUFFERS;
        this.inQueue = 0;
        this.queuedSamples = this.played;
        AL10.alSourcei(this.source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
        AL10.alSource3f(this.source, AL10.AL_POSITION, 0.0F, 0.0F, 0.0F);
        AL10.alSourcef(this.source, AL10.AL_ROLLOFF_FACTOR, 0.0F);
        AL10.alSourcei(this.source, AL10.AL_LOOPING, AL10.AL_FALSE);
        this.volume = -1.0F;
        return true;
    }

    private void reclaim() {
        AL10.alGetError();
        int done = AL10.alGetSourcei(this.source, AL10.AL_BUFFERS_PROCESSED);

        done = Math.max(0, Math.min(done, this.inQueue));
        for (int i = 0; i < done; i++) {
            this.one.clear();
            this.one.put(0, 0);
            AL10.alSourceUnqueueBuffers(this.source, this.one);
            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                return;
            }
            int name = this.one.get(0);
            for (int index = 0; index < BUFFERS; index++) {
                if (this.buffers[index] == name && this.queued[index]) {
                    this.queued[index] = false;
                    this.played += this.bufferSamples[index];
                    this.freeList[this.free++] = index;
                    this.inQueue--;
                    break;
                }
            }
        }
    }

    public void poll(boolean paused, float volume) {
        synchronized (LOCK) {
            if (this.source == 0 || !AL.isCreated()) {
                this.paused = paused;
                return;
            }
            reclaim();
            if (volume != this.volume) {
                this.volume = volume;
                AL10.alSourcef(this.source, AL10.AL_GAIN, volume);
            }
            if (paused != this.paused) {
                this.paused = paused;
                if (paused) {
                    AL10.alSourcePause(this.source);
                } else if (this.inQueue > 0) {
                    AL10.alSourcePlay(this.source);
                }
            }
        }
    }

    public void flush() {
        synchronized (LOCK) {
            if (this.source != 0 && AL.isCreated()) {
                AL10.alSourceStop(this.source);
                reclaim();
            }
            this.played = 0L;
            this.queuedSamples = 0L;
            this.inQueue = 0;
        }
        if (this.chunk != null) {
            this.chunk.clear();
        }
    }

    public long played() {
        synchronized (LOCK) {
            if (this.source == 0 || !AL.isCreated()) {
                return this.played;
            }
            return this.played + AL10.alGetSourcei(this.source, 0x1025  );
        }
    }

    public boolean drained() {
        if (this.chunk != null && this.chunk.position() > 0) {
            if (!submit(new Stop() {
                public boolean now() {
                    return false;
                }
            })) {
                return false;
            }
        }
        synchronized (LOCK) {
            if (this.source == 0 || !AL.isCreated()) {
                return true;
            }
            reclaim();
            return this.inQueue == 0;
        }
    }

    public void release() {
        synchronized (LOCK) {
            if (this.source != 0 && AL.isCreated()) {
                AL10.alSourceStop(this.source);
                this.one.clear();
                this.one.put(this.source).flip();
                AL10.alDeleteSources(this.one);
                IntBuffer names = BufferUtils.createIntBuffer(BUFFERS);
                names.put(this.buffers).flip();
                AL10.alDeleteBuffers(names);
            }

            this.played = this.queuedSamples;
            this.source = 0;
            this.inQueue = 0;
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(5L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
