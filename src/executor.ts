import { spawn } from "node:child_process";
import type { ActionType, ExecuteResult, PanelButton } from "./types";

/** 把命令丢给子进程，统一收集 stdout/stderr/退出码 */
function run(cmd: string, args: string[], stdin?: string): Promise<ExecuteResult> {
  const start = Date.now();
  return new Promise((resolve) => {
    const child = spawn(cmd, args, { env: process.env });
    let stdout = "";
    let stderr = "";
    child.stdout?.on("data", (d) => (stdout += d.toString()));
    child.stderr?.on("data", (d) => (stderr += d.toString()));
    child.on("error", (err) => {
      resolve({
        ok: false,
        error: err.message,
        durationMs: Date.now() - start,
      });
    });
    child.on("close", (code) => {
      resolve({
        ok: code === 0,
        stdout: stdout.trim() || undefined,
        stderr: stderr.trim() || undefined,
        durationMs: Date.now() - start,
        error: code === 0 ? undefined : `exit code ${code}`,
      });
    });
    if (stdin !== undefined) {
      child.stdin?.write(stdin);
      child.stdin?.end();
    }
  });
}

// ---- shell ----
function execShell(command: string): Promise<ExecuteResult> {
  // -l 加载登录环境（让 PATH 包含 brew/asdf/pyenv 等）；-c 执行命令
  return run("/bin/zsh", ["-lc", command]);
}

// ---- open ----
function execOpen(target: string): Promise<ExecuteResult> {
  const t = target.trim();
  // URL 走 `open URL`；其它当 App 名或文件路径
  if (/^https?:\/\//i.test(t)) {
    return run("/usr/bin/open", [t]);
  }
  // 如果是 .app 名字（不带 / 与 .），用 -a；否则原样 open
  if (!t.includes("/") && !t.includes(".")) {
    return run("/usr/bin/open", ["-a", t]);
  }
  return run("/usr/bin/open", [t]);
}

// ---- applescript ----
function execAppleScript(script: string): Promise<ExecuteResult> {
  // -ss 让输出更紧凑
  return run("/usr/bin/osascript", [], script);
}

// ---- keystroke ----
// 把 "cmd+shift+4" / "ctrl+cmd+q" / "cmd+tab" 编译为 AppleScript
// 支持的修饰键：cmd/command, shift, opt/option/alt, ctrl/control
// 普通键：单字符直接 keystroke；命名键映射到 AppleScript 的 key code
const NAMED_KEY_CODES: Record<string, number> = {
  return: 36, enter: 76, tab: 48, space: 49, delete: 51, backspace: 51,
  escape: 53, esc: 53, up: 126, down: 125, left: 123, right: 124,
  home: 115, end: 119, pageup: 116, pagedown: 121,
  f1: 122, f2: 120, f3: 99, f4: 118, f5: 96, f6: 97, f7: 98,
  f8: 100, f9: 101, f10: 109, f11: 103, f12: 111,
};

function escapeAppleScript(s: string): string {
  return s.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
}

function compileKeystroke(combo: string): string {
  const parts = combo.toLowerCase().split("+").map((p) => p.trim()).filter(Boolean);
  if (parts.length === 0) throw new Error("empty key combo");

  const mods: string[] = [];
  let mainKey = "";
  for (const p of parts) {
    if (p === "cmd" || p === "command") mods.push("command down");
    else if (p === "shift") mods.push("shift down");
    else if (p === "opt" || p === "option" || p === "alt") mods.push("option down");
    else if (p === "ctrl" || p === "control") mods.push("control down");
    else {
      if (mainKey) throw new Error(`multiple main keys: ${mainKey} and ${p}`);
      mainKey = p;
    }
  }
  if (!mainKey) throw new Error("no main key");

  const using = mods.length > 0 ? ` using {${mods.join(", ")}}` : "";
  const named = NAMED_KEY_CODES[mainKey];
  if (named !== undefined) {
    return `tell application "System Events" to key code ${named}${using}`;
  }
  // 单字符按键
  if (mainKey.length === 1) {
    return `tell application "System Events" to keystroke "${escapeAppleScript(mainKey)}"${using}`;
  }
  throw new Error(`unknown key: ${mainKey}`);
}

function execKeystroke(combo: string): Promise<ExecuteResult> {
  let script: string;
  try {
    script = compileKeystroke(combo);
  } catch (err) {
    return Promise.resolve({
      ok: false,
      error: (err as Error).message,
      durationMs: 0,
    });
  }
  return run("/usr/bin/osascript", [], script);
}

// ---- 分发 ----
export async function executeButton(btn: PanelButton): Promise<ExecuteResult> {
  switch (btn.type as ActionType) {
    case "shell": return execShell(btn.command);
    case "open": return execOpen(btn.command);
    case "applescript": return execAppleScript(btn.command);
    case "keystroke": return execKeystroke(btn.command);
    default:
      return { ok: false, error: `unknown action type: ${btn.type}`, durationMs: 0 };
  }
}
