package net.milltina.music;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public final class MusicSettings {
    static final String[] QUALITY = {"Standard", "Higher", "Exhigh"};
    static final String[] PLAY_MODES = {"List", "Repeat one", "Shuffle"};

    public static boolean enabled = true;

    public static int playerKey = Keyboard.KEY_NONE;

    private static final Map<String, Object> VALUES = new LinkedHashMap<String, Object>();
    private static boolean loaded;

    static {
        VALUES.put("3D Player", Boolean.FALSE);
        VALUES.put("Dark Mode", Boolean.FALSE);
        VALUES.put("HUD Lyrics", Boolean.TRUE);
        VALUES.put("Translation", Boolean.TRUE);
        VALUES.put("HUD Scale", Double.valueOf(1.0D));
        VALUES.put("Quality", "Exhigh");
        VALUES.put("Volume", Double.valueOf(50.0D));
        VALUES.put("Play Mode", "List");
    }

    private MusicSettings() {
    }

    public static Map<String, Object> all() {
        load();
        return VALUES;
    }

    static boolean check(String name, boolean fallback) {
        load();
        Object value = VALUES.get(name);
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
    }

    static double slider(String name, double fallback) {
        load();
        Object value = VALUES.get(name);
        return value instanceof Double ? ((Double) value).doubleValue() : fallback;
    }

    static String mode(String name, String fallback) {
        load();
        Object value = VALUES.get(name);
        return value instanceof String ? (String) value : fallback;
    }

    static void toggleDark() {
        VALUES.put("Dark Mode", Boolean.valueOf(!check("Dark Mode", false)));
        save();
    }

    static void apply(MusicPlayer player) {
        int index = Arrays.asList(QUALITY).indexOf(mode("Quality", "Exhigh"));
        player.level = MusicPlayer.LEVELS[index < 0 ? 2 : index];
        player.volume = (float) (slider("Volume", 50.0D) / 100.0D);
        String mode = mode("Play Mode", "List");
        for (MusicPlayer.Mode m : MusicPlayer.Mode.values()) {
            if (m.label.equals(mode)) {
                player.mode = m;
            }
        }
    }

    static void setVolume(MusicPlayer player, float volume) {
        player.volume = volume;
        VALUES.put("Volume", Double.valueOf(volume * 100.0F));
    }

    static void store(MusicPlayer player) {
        VALUES.put("Volume", Double.valueOf(Math.round(player.volume * 100.0F)));
        VALUES.put("Play Mode", player.mode.label);
        save();
    }

    static String set(String name, String text) {
        load();
        if (name.equalsIgnoreCase("HUD")) {
            enabled = parseBoolean(text, enabled);
            save();
            return "HUD = " + enabled;
        }
        if (name.equalsIgnoreCase("Key")) {
            int key = Keyboard.getKeyIndex(text.toUpperCase(Locale.ROOT));
            if (key == Keyboard.KEY_NONE && !text.equalsIgnoreCase("NONE")) {
                return null;
            }
            playerKey = key;
            save();
            return "Key = " + Keyboard.getKeyName(key);
        }
        for (Map.Entry<String, Object> entry : VALUES.entrySet()) {
            if (!entry.getKey().replace(" ", "").equalsIgnoreCase(name.replace(" ", ""))) {
                continue;
            }
            Object old = entry.getValue();
            Object value = parse(entry.getKey(), old, text);
            if (value == null) {
                return null;
            }
            entry.setValue(value);
            save();
            return entry.getKey() + " = " + value;
        }
        return null;
    }

    private static Object parse(String name, Object old, String text) {
        if (old instanceof Boolean) {
            return Boolean.valueOf(parseBoolean(text, ((Boolean) old).booleanValue()));
        }
        if (old instanceof Double) {
            try {
                double value = Double.parseDouble(text);
                return name.equals("Volume") ? Math.max(0.0D, Math.min(100.0D, value))
                        : Math.max(0.5D, Math.min(2.0D, value));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        String[] choices = name.equals("Quality") ? QUALITY : PLAY_MODES;
        for (String choice : choices) {
            if (choice.replace(" ", "").equalsIgnoreCase(text.replace(" ", ""))) {
                return choice;
            }
        }
        return null;
    }

    private static boolean parseBoolean(String text, boolean fallback) {
        String t = text.toLowerCase(Locale.ROOT);
        if (t.equals("on") || t.equals("true") || t.equals("1")) {
            return true;
        }
        if (t.equals("off") || t.equals("false") || t.equals("0")) {
            return false;
        }
        return !fallback;
    }

    static String describe() {
        load();
        StringBuilder out = new StringBuilder("HUD=" + enabled + ", Key=" + Keyboard.getKeyName(playerKey));
        for (Map.Entry<String, Object> entry : VALUES.entrySet()) {
            out.append(", ").append(entry.getKey().replace(" ", "")).append('=').append(entry.getValue());
        }
        return out.toString();
    }

    private static File file() {
        return new File(new File(Minecraft.getMinecraft().mcDataDir, "music"), "settings.properties");
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        File file = file();
        if (!file.isFile()) {
            return;
        }
        Properties properties = new Properties();
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            properties.load(in);
        } catch (IOException e) {
            System.err.println("[Music] Reading settings failed: " + e);
            return;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
        enabled = Boolean.parseBoolean(properties.getProperty("HUD", String.valueOf(enabled)));
        try {
            playerKey = Integer.parseInt(properties.getProperty("Key", String.valueOf(playerKey)));
        } catch (NumberFormatException ignored) {
        }
        for (Map.Entry<String, Object> entry : VALUES.entrySet()) {
            String text = properties.getProperty(entry.getKey());
            if (text == null) {
                continue;
            }
            Object value = entry.getValue() instanceof Boolean ? Boolean.valueOf(Boolean.parseBoolean(text))
                    : parse(entry.getKey(), entry.getValue(), text);
            if (value != null) {
                entry.setValue(value);
            }
        }
    }

    public static void save() {
        Properties properties = new Properties();
        properties.setProperty("HUD", String.valueOf(enabled));
        properties.setProperty("Key", String.valueOf(playerKey));
        for (Map.Entry<String, Object> entry : VALUES.entrySet()) {
            properties.setProperty(entry.getKey(), String.valueOf(entry.getValue()));
        }
        File file = file();
        file.getParentFile().mkdirs();
        OutputStream out = null;
        try {
            out = new FileOutputStream(file);
            properties.store(out, "Milltina Music");
        } catch (IOException e) {
            System.err.println("[Music] Saving settings failed: " + e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
