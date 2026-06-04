import { file } from "bun";
import { randomUUID } from "node:crypto";
import { join } from "node:path";
import QRCode from "qrcode-svg";
import { loadConfig, saveConfig, getConfigPath } from "./config";
import { executeButton } from "./executor";
import { getLanIp } from "./net";
import type { PanelButton, PanelConfig } from "./types";

const PUBLIC_DIR = join(import.meta.dir, "..", "public");

const json = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });

const text = (s: string, status = 200, type = "text/plain; charset=utf-8") =>
  new Response(s, { status, headers: { "content-type": type } });

/** 来自电脑本机（127.0.0.1/::1）的请求拥有管理权限 */
function isLocalRequest(server: ReturnType<typeof Bun.serve>, req: Request): boolean {
  const ip = server.requestIP(req)?.address ?? "";
  return ip === "127.0.0.1" || ip === "::1" || ip === "::ffff:127.0.0.1";
}

/** 校验手机端 token：query ?token=xxx 或 header x-panel-token */
function hasValidToken(req: Request, cfg: PanelConfig): boolean {
  const url = new URL(req.url);
  const t = url.searchParams.get("token") ?? req.headers.get("x-panel-token");
  return !!t && t === cfg.token;
}

async function serveStatic(path: string): Promise<Response> {
  const f = file(join(PUBLIC_DIR, path));
  if (!(await f.exists())) return text("not found", 404);
  return new Response(f);
}

async function main() {
  let cfg = await loadConfig();

  const server = Bun.serve({
    port: cfg.port,
    hostname: "0.0.0.0",
    async fetch(req) {
      const url = new URL(req.url);
      const path = url.pathname;
      const local = isLocalRequest(server, req);

      // 根路径：本机给 admin，外部给 panel
      if (path === "/") {
        return Response.redirect(local ? "/admin" : "/panel", 302);
      }

      // 静态页面
      if (path === "/admin") {
        if (!local) return text("admin only available on this Mac (127.0.0.1)", 403);
        return serveStatic("admin.html");
      }
      if (path === "/panel") {
        return serveStatic("panel.html");
      }
      if (path.startsWith("/static/")) {
        return serveStatic(path.replace(/^\/static\//, ""));
      }

      // ---- API ----
      // 配置读取：admin 直接给；panel 需 token
      if (path === "/api/config" && req.method === "GET") {
        if (!local && !hasValidToken(req, cfg)) return json({ error: "unauthorized" }, 401);
        // 不暴露 token 给手机
        return json({
          port: cfg.port,
          buttons: cfg.buttons,
        });
      }

      // 仅本机：完整 config（含 token，用于 admin 显示）
      if (path === "/api/admin/config" && req.method === "GET") {
        if (!local) return json({ error: "forbidden" }, 403);
        return json(cfg);
      }

      // 仅本机：保存按钮列表
      if (path === "/api/admin/buttons" && req.method === "PUT") {
        if (!local) return json({ error: "forbidden" }, 403);
        const body = (await req.json()) as { buttons: PanelButton[] };
        if (!Array.isArray(body.buttons)) return json({ error: "invalid body" }, 400);
        // 给没有 id 的补 id
        cfg.buttons = body.buttons.map((b) => ({ ...b, id: b.id || randomUUID() }));
        await saveConfig(cfg);
        return json({ ok: true, buttons: cfg.buttons });
      }

      // 仅本机：重置 token（手机配对作废）
      if (path === "/api/admin/rotate-token" && req.method === "POST") {
        if (!local) return json({ error: "forbidden" }, 403);
        cfg = await loadConfig();
        const { randomBytes } = await import("node:crypto");
        cfg.token = randomBytes(16).toString("hex");
        await saveConfig(cfg);
        return json({ ok: true, token: cfg.token });
      }

      // 仅本机：返回二维码 SVG（手机扫码进入 /panel）
      if (path === "/api/admin/qrcode" && req.method === "GET") {
        if (!local) return json({ error: "forbidden" }, 403);
        const ip = getLanIp();
        const panelUrl = `http://${ip}:${cfg.port}/panel?token=${cfg.token}`;
        const svg = new QRCode({
          content: panelUrl,
          padding: 2,
          width: 256,
          height: 256,
          color: "#000",
          background: "#fff",
          ecl: "M",
        }).svg()
          // qrcode-svg 不输出 viewBox，CSS 缩放时会被裁剪——主动注入
          .replace(/<svg(\s+[^>]*)?>/, (m) =>
            /viewBox=/.test(m) ? m : m.replace("<svg", '<svg viewBox="0 0 256 256" preserveAspectRatio="xMidYMid meet"'));
        return new Response(JSON.stringify({ url: panelUrl, svg }), {
          headers: { "content-type": "application/json; charset=utf-8" },
        });
      }

      // 执行按钮：admin（本机）或带正确 token 的 panel
      const m = path.match(/^\/api\/execute\/([^/]+)$/);
      if (m && req.method === "POST") {
        if (!local && !hasValidToken(req, cfg)) return json({ error: "unauthorized" }, 401);
        const id = m[1];
        const btn = cfg.buttons.find((b) => b.id === id);
        if (!btn) return json({ error: "button not found" }, 404);
        const result = await executeButton(btn);
        return json(result);
      }

      return text("not found", 404);
    },
  });

  const ip = getLanIp();
  console.log("┌──────────────────────────────────────────────");
  console.log(`│ phone-panel running`);
  console.log(`│ config:  ${getConfigPath()}`);
  console.log(`│ admin :  http://127.0.0.1:${cfg.port}/admin`);
  console.log(`│ panel :  http://${ip}:${cfg.port}/panel?token=${cfg.token}`);
  console.log("└──────────────────────────────────────────────");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
