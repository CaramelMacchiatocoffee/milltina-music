package net.milltina.music;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import org.lwjgl.input.Keyboard;

public final class MusicCommand {
    private static final String COMMAND = ".music";

    private MusicCommand() {
    }

    public static boolean handle(Minecraft mc, String message) {
        String trimmed = message.trim();
        String[] words = trimmed.split("\\s+");
        if (!words[0].equalsIgnoreCase(COMMAND)) {
            return false;
        }
        mc.ingameGUI.getChatGUI().func_146239_a(message);
        String rest = trimmed.substring(COMMAND.length()).trim();
        String action = rest.toLowerCase(Locale.ROOT);
        MusicPlayer player = Music.get().player;
        if (rest.isEmpty()) {
            open(mc, null);
        } else if (action.equals("pause") || action.equals("play")) {
            player.toggle();
        } else if (action.equals("next")) {
            player.next();
        } else if (action.equals("prev")) {
            player.previous();
        } else if (action.equals("stop")) {
            player.stop();
        } else if (action.equals("logout")) {
            Music.get().logout();
            say(mc, "Logged out of NetEase Cloud Music");
        } else if (action.equals("resethud")) {
            MusicHud.resetPosition();
        } else if (action.equals("set") || action.startsWith("set ")) {
            String[] parts = rest.substring(3).trim().split("\\s+", 2);
            if (parts.length < 2) {
                say(mc, MusicSettings.describe());
            } else {
                String result = MusicSettings.set(parts[0], parts[1]);
                say(mc, result == null ? "Unknown setting or value: " + rest.substring(3).trim() : result);
            }
        } else {
            open(mc, rest);
        }
        return true;
    }

    public static void onKey(Minecraft mc, int key) {
        if (key == Keyboard.KEY_NONE || key != MusicSettings.playerKey || Keyboard.isKeyDown(Keyboard.KEY_F3)) {
            return;
        }
        if (VisionMusic.isActive()) {
            VisionMusic.close();
        } else {
            open(mc, null);
        }
    }

    public static void open(Minecraft mc, String search) {
        if (MusicSettings.check("3D Player", false) && VisionMusic.open(mc, search)) {
            return;
        }
        mc.displayGuiScreen(new GuiMusic(search));
    }

    private static void say(Minecraft mc, String text) {
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText("§7[Music] §f" + text));
        }
    }
}
