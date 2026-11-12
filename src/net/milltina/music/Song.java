package net.milltina.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

public final class Song {
    public final long id;
    public final String name;
    public final String artists;
    public final String album;

    public final String cover;
    public final long durationMs;

    public final int fee;

    public Song(long id, String name, String artists, String album, String cover, long durationMs, int fee) {
        this.id = id;
        this.name = name;
        this.artists = artists;
        this.album = album;
        this.cover = cover;
        this.durationMs = durationMs;
        this.fee = fee;
    }

    static List<Song> list(JsonElement element) {
        List<Song> result = new ArrayList<Song>();
        if (element == null || !element.isJsonArray()) {
            return result;
        }
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject one = item.getAsJsonObject();
            StringBuilder artists = new StringBuilder();
            JsonArray ar = NcmApi.array(one, "ar");
            if (ar != null) {
                for (JsonElement artist : ar) {
                    String name = artist.isJsonObject() ? NcmApi.string(artist.getAsJsonObject(), "name") : null;
                    if (name != null) {
                        artists.append(artists.length() > 0 ? " / " : "").append(name);
                    }
                }
            }
            JsonObject al = NcmApi.object(one, "al");
            String name = NcmApi.string(one, "name");
            result.add(new Song(NcmApi.number(one, "id"), name == null ? "?" : name, artists.toString(),
                    al == null ? "" : nonNull(NcmApi.string(al, "name")), al == null ? null : NcmApi.string(al, "picUrl"),
                    NcmApi.number(one, "dt"), (int) NcmApi.number(one, "fee")));
        }
        return result;
    }

    private static String nonNull(String text) {
        return text == null ? "" : text;
    }

    public String title() {
        return this.artists.length() == 0 ? this.name : this.name + " - " + this.artists;
    }
}
