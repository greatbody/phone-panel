import { mkdir, unlink, writeFile, stat } from "node:fs/promises";
import { existsSync } from "node:fs";
import { dirname } from "node:path";
import { spawn } from "node:child_process";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { randomUUID } from "node:crypto";
import { getIconPath, getIconsDir } from "./config";

const ICON_MAX_SIZE = 256;

function runSips(input: string, output: string): Promise<{ ok: boolean; err?: string }> {
  return new Promise((resolve) => {
    // -Z 等比缩放到最长边、-s format png 强制 PNG，--out 指定输出
    const child = spawn("/usr/bin/sips", [
      "-Z", String(ICON_MAX_SIZE),
      "-s", "format", "png",
      input,
      "--out", output,
    ]);
    let stderr = "";
    child.stderr?.on("data", (d) => (stderr += d.toString()));
    child.on("error", (err) => resolve({ ok: false, err: err.message }));
    child.on("close", (code) => {
      if (code === 0) resolve({ ok: true });
      else resolve({ ok: false, err: stderr.trim() || `sips exit ${code}` });
    });
  });
}

/**
 * 保存上传的图片为按钮图标：
 * 1. 写入临时文件（保留原扩展名让 sips 能识别）
 * 2. sips 转 256×256 PNG 落到 ~/.config/phone-panel/icons/<id>.png
 * 3. 清理临时文件
 */
export async function saveIcon(
  buttonId: string,
  bytes: Uint8Array,
  originalName: string,
): Promise<{ ok: boolean; error?: string; bytes?: number }> {
  await mkdir(getIconsDir(), { recursive: true });

  const ext = inferExt(originalName);
  const tmpPath = join(tmpdir(), `phone-panel-${randomUUID()}${ext}`);
  const dstPath = getIconPath(buttonId);

  try {
    await writeFile(tmpPath, bytes);
    const result = await runSips(tmpPath, dstPath);
    if (!result.ok) return { ok: false, error: result.err ?? "sips failed" };
    const s = await stat(dstPath);
    return { ok: true, bytes: s.size };
  } finally {
    if (existsSync(tmpPath)) await unlink(tmpPath).catch(() => {});
  }
}

export async function deleteIcon(buttonId: string): Promise<void> {
  const p = getIconPath(buttonId);
  if (existsSync(p)) await unlink(p).catch(() => {});
}

export async function getIconStat(buttonId: string) {
  const p = getIconPath(buttonId);
  if (!existsSync(p)) return null;
  return stat(p);
}

/** 根据文件名猜扩展名（去掉所有非字母数字），保证 sips 能正确识别格式 */
function inferExt(name: string): string {
  const m = /\.([A-Za-z0-9]{1,6})$/.exec(name);
  const captured = m?.[1];
  return captured ? `.${captured.toLowerCase()}` : "";
}
