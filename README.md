# Milltina Music

Minecraft 1.7.10 (MCP) 内嵌网易云音乐播放器，有平面界面、3D 世界卡片和 HUD 歌词卡片。

## 需要

- MCP 1.7.10，Java 8
- JLayer 1.0.1（`javazoom:jlayer:1.0.1`）
- qrcodegen 1.8.0（`io.nayuki:qrcodegen:1.8.0`）

## 安装

把 `src/net` 和 `src/assets` 复制进客户端，加上面两个 jar，然后在原版类里调用 `MusicMod`：

| 位置 | 调用 |
|---|---|
| `Minecraft.runTick` 鼠标循环开头 | `if (MusicMod.takeMouse(this)) continue;` |
| `Minecraft.runTick` 键盘循环开头 | `if (MusicMod.takeKey(this)) continue;` |
| 无界面时按下按键 / 鼠标键 | `MusicMod.onKey(this, key);` |
| `Minecraft.loadWorld(null)` | `MusicMod.leftWorld();` |
| `EntityRenderer.renderWorld` 实体之前 | `MusicMod.renderWorld(this, partialTicks);` |
| `GuiIngame.renderGameOverlay` | `MusicMod.renderHud(mc, resolution);` |
| `GuiChat` 发送消息 | `if (MusicMod.onChat(mc, message)) return;` |
| `GuiChat` 点击 / 绘制 / 松开 | `chatPress` / `chatDrag` / `chatRelease` |
| `SoundManager` 卸载前 | `MusicMod.beforeSoundUnload();` |

## 命令

`.music` 打开，`.music <关键词>` 搜索，`.music pause|next|prev|stop|logout`，`.music set <设置> <值>` 改设置。

## 许可

MIT。字体为 SIL OFL 1.1，见 `licenses/`。
