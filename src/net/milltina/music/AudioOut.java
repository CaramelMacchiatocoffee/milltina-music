package net.milltina.music;

interface AudioOut {
    boolean write(short[] pcm, int length, int rate, int channels, Stop stop);

    void poll(boolean paused, float volume);

    void flush();

    long played();

    boolean drained();

    void release();

    interface Stop {
        boolean now();
    }
}
