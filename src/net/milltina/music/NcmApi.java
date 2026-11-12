package net.milltina.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class NcmApi {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String WEAPI_KEY = "0CoJUm6Qyw8W8jud";
    private static final String IV = "0102030405060708";
    private static final String EAPI_KEY = "e82ckenh8dichen8";
    private static final BigInteger RSA_MODULUS = new BigInteger(
            "e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec"
            + "4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d81"
            + "3cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7", 16);
    private static final BigInteger RSA_EXPONENT = BigInteger.valueOf(0x10001);
    private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String WEB = "https://music.163.com";
    private static final String PC = "https://interface.music.163.com";
    private static final String WEB_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0";
    private static final String PC_AGENT = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/91.0.4472.164 NeteaseMusicDesktop/3.0.18.203152";
    private static final String APP_VERSION = "3.0.18.203152";
    private static final int TIMEOUT = 15000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, String> cookies = new LinkedHashMap<String, String>();
    private final File cookieFile;

    public NcmApi(File cookieFile) {
        this.cookieFile = cookieFile;
        load();
        if (!this.cookies.containsKey("deviceId")) {
            this.cookies.put("deviceId", randomHex(26).toUpperCase());
            save();
        }
    }

    public synchronized boolean loggedIn() {
        return this.cookies.containsKey("MUSIC_U");
    }

    public synchronized void logout() {
        String device = this.cookies.get("deviceId");
        this.cookies.clear();
        this.cookies.put("deviceId", device);
        save();
    }

    public List<Song> search(String words, int offset) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("s", words);
        data.addProperty("type", 1);
        data.addProperty("limit", 30);
        data.addProperty("offset", offset);
        data.addProperty("total", true);

        JsonObject answer = eapi("/api/cloudsearch/pc", data);
        JsonObject result = object(answer, "result");
        return Song.list(result == null ? null : result.get("songs"));
    }

    public SongUrl songUrl(long id, String level) throws IOException {
        anonymous();
        JsonObject data = new JsonObject();
        data.addProperty("ids", "[" + id + "]");
        data.addProperty("level", level);
        data.addProperty("encodeType", "mp3");
        JsonObject answer = eapi("/api/song/enhance/player/url/v1", data);
        JsonArray list = array(answer, "data");
        if (list == null || list.size() == 0) {
            return null;
        }
        JsonObject one = list.get(0).getAsJsonObject();
        String url = string(one, "url");
        if (url == null) {
            return null;
        }
        SongUrl result = new SongUrl();
        result.url = url;
        result.type = string(one, "type");
        result.bitrate = (int) number(one, "br");
        result.size = number(one, "size");
        JsonObject trial = object(one, "freeTrialInfo");
        if (trial != null) {
            result.trialStart = number(trial, "start") * 1000L;
            result.trialEnd = number(trial, "end") * 1000L;
        }
        return result;
    }

    private void anonymous() throws IOException {
        String device;
        synchronized (this) {
            if (this.cookies.containsKey("MUSIC_U") || this.cookies.containsKey("MUSIC_A")) {
                return;
            }
            device = this.cookies.get("deviceId");
        }
        String xorKey = "3go8&$8*3*3h0k(2)2";
        StringBuilder xored = new StringBuilder();
        for (int i = 0; i < device.length(); i++) {
            xored.append((char) (device.charAt(i) ^ xorKey.charAt(i % xorKey.length())));
        }
        String hash = java.util.Base64.getEncoder().encodeToString(md5(xored.toString().getBytes(UTF8)));
        JsonObject data = new JsonObject();
        data.addProperty("username", java.util.Base64.getEncoder().encodeToString((device + " " + hash).getBytes(UTF8)));
        weapi("/register/anonimous", data);
        save();
    }

    public String[] lyrics(long id) throws IOException {
        JsonObject answer = parse(request(WEB + "/api/song/lyric?id=" + id + "&lv=-1&tv=-1", null, WEB_AGENT, false));
        return new String[] {lrc(answer, "lrc"), lrc(answer, "tlyric")};
    }

    public String qrKey() throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("type", 1);
        JsonObject answer = weapi("/login/qrcode/unikey", data);
        String key = string(answer, "unikey");
        if (key == null) {
            throw new IOException("No QR key: " + answer);
        }
        return key;
    }

    public static String qrContent(String key) {
        return "https://music.163.com/login?codekey=" + key;
    }

    public int qrCheck(String key) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("key", key);
        data.addProperty("type", 1);
        JsonObject answer = weapi("/login/qrcode/client/login", data);
        int code = (int) number(answer, "code");
        if (code == 803) {
            save();
        }
        return code;
    }

    public Object[] account() throws IOException {
        JsonObject answer = weapi("/w/nuser/account/get", new JsonObject());
        JsonObject profile = object(answer, "profile");
        if (profile == null) {
            return null;
        }
        return new Object[] {Long.valueOf(number(profile, "userId")), string(profile, "nickname")};
    }

    public List<Playlist> playlists(long uid) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("uid", uid);
        data.addProperty("limit", 1000);
        data.addProperty("offset", 0);
        data.addProperty("includeVideo", true);
        JsonObject answer = weapi("/user/playlist", data);
        List<Playlist> result = new ArrayList<Playlist>();
        JsonArray list = array(answer, "playlist");
        if (list != null) {
            for (JsonElement element : list) {
                JsonObject one = element.getAsJsonObject();
                JsonObject creator = object(one, "creator");
                result.add(new Playlist(number(one, "id"), string(one, "name"), (int) number(one, "trackCount"),
                        creator != null && number(creator, "userId") == uid, string(one, "coverImgUrl"), null));
            }
        }
        return result;
    }

    public List<Song> playlistSongs(long id) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("id", id);
        data.addProperty("n", 100000);
        data.addProperty("s", 8);
        JsonObject answer = weapi("/v6/playlist/detail", data);
        JsonObject playlist = object(answer, "playlist");
        JsonArray ids = playlist == null ? null : array(playlist, "trackIds");
        List<Long> all = new ArrayList<Long>();
        if (ids != null) {
            for (JsonElement element : ids) {
                all.add(Long.valueOf(number(element.getAsJsonObject(), "id")));
            }
        }
        return songs(all);
    }

    public List<Song> songs(List<Long> ids) throws IOException {
        List<Song> result = new ArrayList<Song>();
        for (int start = 0; start < ids.size(); start += 500) {
            StringBuilder c = new StringBuilder("[");
            int end = Math.min(ids.size(), start + 500);
            for (int i = start; i < end; i++) {
                c.append(i > start ? "," : "").append("{\"id\":").append(ids.get(i)).append('}');
            }
            JsonObject data = new JsonObject();
            data.addProperty("c", c.append(']').toString());
            JsonObject answer = weapi("/v3/song/detail", data);
            result.addAll(Song.list(answer.get("songs")));
        }
        return result;
    }

    public List<Song> dailySongs() throws IOException {
        JsonObject answer = weapi("/v3/discovery/recommend/songs", new JsonObject());
        JsonObject data = object(answer, "data");
        return Song.list(data == null ? null : data.get("dailySongs"));
    }

    public List<Playlist> recommendedPlaylists() throws IOException {
        List<Playlist> result = new ArrayList<Playlist>();
        if (loggedIn()) {
            try {
                addPlaylists(result, array(weapi("/v1/discovery/recommend/resource", new JsonObject()), "recommend"));
            } catch (IOException ignored) {
            }
        }
        JsonObject data = new JsonObject();
        data.addProperty("limit", 30);
        data.addProperty("total", true);
        data.addProperty("n", 1000);
        addPlaylists(result, array(weapi("/personalized/playlist", data), "result"));
        return result;
    }

    public static final String[][] HOME_BLOCKS = {
        {"HOMEPAGE_BLOCK_PLAYLIST_RCMD", "Recommended playlists"},
        {"HOMEPAGE_BLOCK_MGC_PLAYLIST", "Your radar"},
        {"HOMEPAGE_BLOCK_OFFICIAL_PLAYLIST", "For the moment"},
    };

    public java.util.Map<String, List<Playlist>> homeBlocks() throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("refresh", false);
        JsonObject page = object(weapi("/homepage/block/page", data), "data");
        java.util.Map<String, List<Playlist>> result = new java.util.LinkedHashMap<String, List<Playlist>>();
        JsonArray blocks = page == null ? null : array(page, "blocks");
        if (blocks == null) {
            return result;
        }
        for (JsonElement b : blocks) {
            if (!b.isJsonObject()) {
                continue;
            }
            String code = string(b.getAsJsonObject(), "blockCode");
            JsonArray creatives = array(b.getAsJsonObject(), "creatives");
            if (code == null || creatives == null) {
                continue;
            }
            List<Playlist> lists = new ArrayList<Playlist>();
            for (JsonElement c : creatives) {
                JsonArray resources = c.isJsonObject() ? array(c.getAsJsonObject(), "resources") : null;
                if (resources == null) {
                    continue;
                }
                for (JsonElement r : resources) {
                    if (!r.isJsonObject() || !"list".equals(string(r.getAsJsonObject(), "resourceType"))) {
                        continue;
                    }
                    JsonObject one = r.getAsJsonObject();
                    long id;
                    try {
                        id = Long.parseLong(string(one, "resourceId"));
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    JsonObject ui = object(one, "uiElement");
                    JsonObject title = ui == null ? null : object(ui, "mainTitle");
                    JsonObject image = ui == null ? null : object(ui, "image");
                    lists.add(new Playlist(id, title == null ? null : string(title, "title"), 0, false,
                            image == null ? null : string(image, "imageUrl"), null));
                }
            }
            if (!lists.isEmpty()) {
                result.put(code, lists);
            }
        }
        return result;
    }

    private static void addPlaylists(List<Playlist> result, JsonArray list) {
        if (list == null) {
            return;
        }
        for (JsonElement element : list) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject one = element.getAsJsonObject();
            long id = number(one, "id");
            boolean known = id == 0L;
            for (Playlist p : result) {
                known |= p.id == id;
            }
            if (!known) {
                result.add(new Playlist(id, string(one, "name"), (int) number(one, "trackCount"), false,
                        string(one, "picUrl"), string(one, "copywriter")));
            }
        }
    }

    public List<Song> heartbeat(long seed, long likedPlaylist) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("songId", seed);
        data.addProperty("type", "fromPlayOne");
        data.addProperty("playlistId", likedPlaylist);
        data.addProperty("startMusicId", seed);
        data.addProperty("count", 1);
        JsonObject answer = weapi("/playmode/intelligence/list", data);
        JsonArray list = array(answer, "data");
        if (list == null) {
            throw new IOException("Heartbeat: " + number(answer, "code"));
        }
        JsonArray songs = new JsonArray();
        for (JsonElement element : list) {
            JsonObject info = element.isJsonObject() ? object(element.getAsJsonObject(), "songInfo") : null;
            if (info != null) {
                songs.add(info);
            }
        }
        return Song.list(songs);
    }

    public List<Long> likedIds(long uid) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("uid", uid);
        JsonObject answer = weapi("/song/like/get", data);
        List<Long> result = new ArrayList<Long>();
        JsonArray ids = array(answer, "ids");
        if (ids != null) {
            for (JsonElement element : ids) {
                result.add(Long.valueOf(element.getAsLong()));
            }
        }
        return result;
    }

    public boolean like(long id, boolean like) throws IOException {
        JsonObject data = new JsonObject();
        data.addProperty("alg", "itembased");
        data.addProperty("trackId", id);
        data.addProperty("like", like);
        data.addProperty("time", "3");
        return number(weapi("/radio/like?alg=itembased&trackId=" + id + "&like=" + like + "&time=3", data), "code") == 200;
    }

    private JsonObject weapi(String path, JsonObject data) throws IOException {
        String csrf;
        synchronized (this) {
            csrf = this.cookies.get("__csrf");
        }
        data.addProperty("csrf_token", csrf == null ? "" : csrf);
        String secret = randomBase62(16);
        String params = aes(aes(data.toString(), WEAPI_KEY, true), secret, true);
        String reversed = new StringBuilder(secret).reverse().toString();
        String encSecKey = new BigInteger(1, reversed.getBytes(UTF8)).modPow(RSA_EXPONENT, RSA_MODULUS).toString(16);
        while (encSecKey.length() < 256) {
            encSecKey = "0" + encSecKey;
        }
        String body = "params=" + URLEncoder.encode(params, "UTF-8") + "&encSecKey=" + encSecKey;
        String query = path.indexOf('?') >= 0 ? "&" : "?";
        return parse(request(WEB + "/weapi" + path + query + "csrf_token=" + (csrf == null ? "" : csrf), body, WEB_AGENT, false));
    }

    private JsonObject eapi(String path, JsonObject data) throws IOException {
        JsonObject header = new JsonObject();
        synchronized (this) {
            header.addProperty("osver", "Microsoft-Windows-10-Professional-build-19045-64bit");
            header.addProperty("deviceId", this.cookies.get("deviceId"));
            header.addProperty("appver", APP_VERSION);
            header.addProperty("versioncode", "140");
            header.addProperty("buildver", String.valueOf(System.currentTimeMillis() / 1000L));
            header.addProperty("resolution", "1920x1080");
            header.addProperty("os", "pc");
            header.addProperty("channel", "netease");
            header.addProperty("requestId", System.currentTimeMillis() + "_" + (1000 + RANDOM.nextInt(9000)));
            String csrf = this.cookies.get("__csrf");
            if (csrf != null) {
                header.addProperty("__csrf", csrf);
            }
            if (this.cookies.containsKey("MUSIC_U")) {
                header.addProperty("MUSIC_U", this.cookies.get("MUSIC_U"));
            } else if (this.cookies.containsKey("MUSIC_A")) {
                header.addProperty("MUSIC_A", this.cookies.get("MUSIC_A"));
            }
        }
        data.add("header", header);
        data.addProperty("e_r", false);
        String text = data.toString();
        String digest = hex(md5(("nobody" + path + "use" + text + "md5forencrypt").getBytes(UTF8)));
        String message = path + "-36cd479b6b5-" + text + "-36cd479b6b5-" + digest;
        String params;
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(EAPI_KEY.getBytes(UTF8), "AES"));
            params = hex(cipher.doFinal(message.getBytes(UTF8))).toUpperCase();
        } catch (Exception e) {
            throw new IOException(e);
        }
        return parse(request(PC + "/eapi" + path.substring("/api".length()), "params=" + params, PC_AGENT, true));
    }

    private String request(String address, String body, String agent, boolean pc) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(TIMEOUT);
        connection.setReadTimeout(TIMEOUT);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", agent);
        connection.setRequestProperty("Referer", "https://music.163.com/");
        connection.setRequestProperty("Cookie", cookieHeader(pc));
        if (body != null) {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            byte[] bytes = body.getBytes(UTF8);
            connection.setFixedLengthStreamingMode(bytes.length);
            OutputStream out = connection.getOutputStream();
            try {
                out.write(bytes);
            } finally {
                out.close();
            }
        }
        int status = connection.getResponseCode();
        keepCookies(connection);
        InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (in == null) {
            throw new IOException("HTTP " + status);
        }
        try {
            return new String(readAll(in), UTF8);
        } finally {
            in.close();
        }
    }

    private synchronized String cookieHeader(boolean pc) {
        StringBuilder header = new StringBuilder("__remember_me=true; os=pc; appver=").append(APP_VERSION);
        for (Map.Entry<String, String> cookie : this.cookies.entrySet()) {
            header.append("; ").append(cookie.getKey()).append('=').append(cookie.getValue());
        }
        return header.toString();
    }

    private synchronized void keepCookies(HttpURLConnection connection) {
        boolean changed = false;
        for (Map.Entry<String, List<String>> field : connection.getHeaderFields().entrySet()) {
            if (field.getKey() == null || !field.getKey().equalsIgnoreCase("Set-Cookie")) {
                continue;
            }
            for (String line : field.getValue()) {
                String pair = line.split(";", 2)[0];
                int equals = pair.indexOf('=');
                if (equals <= 0) {
                    continue;
                }
                String name = pair.substring(0, equals).trim();
                String value = pair.substring(equals + 1).trim();

                if (!name.equals("MUSIC_U") && !name.equals("__csrf") && !name.equals("MUSIC_A") && !name.equals("NMTID")) {
                    continue;
                }
                if (value.length() == 0 || line.toLowerCase().contains("max-age=0")) {
                    changed |= this.cookies.remove(name) != null;
                } else if (!value.equals(this.cookies.get(name))) {
                    this.cookies.put(name, value);
                    changed = true;
                }
            }
        }
        if (changed && this.cookies.containsKey("MUSIC_U")) {
            save();
        }
    }

    private synchronized void load() {
        if (this.cookieFile == null || !this.cookieFile.isFile()) {
            return;
        }
        Properties properties = new Properties();
        try {
            InputStream in = new FileInputStream(this.cookieFile);
            try {
                properties.load(in);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return;
        }
        for (String name : properties.stringPropertyNames()) {
            this.cookies.put(name, properties.getProperty(name));
        }
    }

    private synchronized void save() {
        if (this.cookieFile == null) {
            return;
        }
        Properties properties = new Properties();
        for (Map.Entry<String, String> cookie : this.cookies.entrySet()) {
            properties.setProperty(cookie.getKey(), cookie.getValue());
        }
        try {
            File parent = this.cookieFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            OutputStream out = new FileOutputStream(this.cookieFile);
            try {
                properties.store(out, "NetEase login, keep private");
            } finally {
                out.close();
            }
        } catch (IOException ignored) {
        }
    }

    private static String aes(String text, String key, boolean base64) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getBytes(UTF8), "AES"), new IvParameterSpec(IV.getBytes(UTF8)));
            return java.util.Base64.getEncoder().encodeToString(cipher.doFinal(text.getBytes(UTF8)));
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static byte[] md5(byte[] bytes) throws IOException {
        try {
            return MessageDigest.getInstance("MD5").digest(bytes);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            text.append(Character.forDigit(b >> 4 & 15, 16)).append(Character.forDigit(b & 15, 16));
        }
        return text.toString();
    }

    private static String randomBase62(int length) {
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return text.toString();
    }

    private static String randomHex(int length) {
        byte[] bytes = new byte[(length + 1) / 2];
        RANDOM.nextBytes(bytes);
        return hex(bytes).substring(0, length);
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static JsonObject parse(String text) throws IOException {
        try {
            JsonElement element = new JsonParser().parse(text);
            if (element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        } catch (RuntimeException ignored) {
        }
        throw new IOException("Not JSON: " + (text.length() > 200 ? text.substring(0, 200) : text));
    }

    private static String lrc(JsonObject answer, String name) {
        JsonObject part = object(answer, name);
        String text = part == null ? null : string(part, "lyric");
        return text == null ? "" : text;
    }

    static JsonObject object(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    static JsonArray array(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    static String string(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    static long number(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        if (element == null || !element.isJsonPrimitive()) {
            return 0L;
        }
        try {
            return element.getAsLong();
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    public static final class SongUrl {
        public String url;
        public String type;
        public int bitrate;
        public long size;
        public long trialStart = -1L;
        public long trialEnd = -1L;
    }
}
