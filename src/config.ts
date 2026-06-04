import { mkdir, readFile, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { homedir } from "node:os";
import { dirname, join } from "node:path";
import { randomBytes, randomUUID } from "node:crypto";
import type { PanelConfig } from "./types";

const CONFIG_PATH =
  process.env.PHONE_PANEL_CONFIG ??
  join(homedir(), ".config", "phone-panel", "config.json");

const ICONS_DIR =
  process.env.PHONE_PANEL_ICONS ??
  join(homedir(), ".config", "phone-panel", "icons");

function randomToken(): string {
  return randomBytes(16).toString("hex");
}

function defaultConfig(): PanelConfig {
  return {
    token: randomToken(),
    port: 7788,
    buttons: [
      {
        id: randomUUID(),
        label: "Hello",
        icon: "👋",
        color: "#3b82f6",
        type: "shell",
        command: 'osascript -e \'display notification "Hello from phone-panel" with title "Phone Panel"\'',
      },
      {
        id: randomUUID(),
        label: "锁屏",
        icon: "🔒",
        color: "#475569",
        type: "keystroke",
        command: "ctrl+cmd+q",
      },
      {
        id: randomUUID(),
        label: "截图",
        icon: "📸",
        color: "#10b981",
        type: "keystroke",
        command: "cmd+shift+4",
      },
    ],
  };
}

export async function loadConfig(): Promise<PanelConfig> {
  if (!existsSync(CONFIG_PATH)) {
    const cfg = defaultConfig();
    await saveConfig(cfg);
    return cfg;
  }
  const raw = await readFile(CONFIG_PATH, "utf8");
  const cfg = JSON.parse(raw) as PanelConfig;
  // 兼容老配置：缺字段补默认
  if (!cfg.token) cfg.token = randomToken();
  if (!cfg.port) cfg.port = 7788;
  if (!Array.isArray(cfg.buttons)) cfg.buttons = [];
  return cfg;
}

export async function saveConfig(cfg: PanelConfig): Promise<void> {
  await mkdir(dirname(CONFIG_PATH), { recursive: true });
  await writeFile(CONFIG_PATH, JSON.stringify(cfg, null, 2), "utf8");
}

export function getConfigPath(): string {
  return CONFIG_PATH;
}

export function getIconsDir(): string {
  return ICONS_DIR;
}

export function getIconPath(buttonId: string): string {
  return join(ICONS_DIR, `${buttonId}.png`);
}
