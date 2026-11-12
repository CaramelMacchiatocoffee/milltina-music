package net.milltina.music;

public final class Playlist {
    public final long id;
    public final String name;
    public final int count;

    public final boolean own;

    public final String cover;

    public final String tag;

    public Playlist(long id, String name, int count, boolean own) {
        this(id, name, count, own, null, null);
    }

    public Playlist(long id, String name, int count, boolean own, String cover, String tag) {
        this.id = id;
        this.name = name == null ? "?" : name;
        this.count = count;
        this.own = own;
        this.cover = cover;
        this.tag = tag == null || tag.length() == 0 ? null : tag;
    }
}
