import { networkInterfaces } from "node:os";

/**
 * 获取局域网 IPv4（优先 192.168.* / 10.* / 172.16-31.*）。
 * 找不到就返回 127.0.0.1。
 */
export function getLanIp(): string {
  const ifaces = networkInterfaces();
  const candidates: string[] = [];
  for (const name of Object.keys(ifaces)) {
    for (const ni of ifaces[name] ?? []) {
      if (ni.family !== "IPv4" || ni.internal) continue;
      candidates.push(ni.address);
    }
  }
  // 优先私有网段
  const priv = candidates.find((ip) =>
    /^192\.168\./.test(ip) ||
    /^10\./.test(ip) ||
    /^172\.(1[6-9]|2\d|3[01])\./.test(ip)
  );
  return priv ?? candidates[0] ?? "127.0.0.1";
}
