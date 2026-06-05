# phone-panel

> 把旧手机改造成可编程的桌面控制面板 —— 类 Stream Deck，但用「手机 + 网页」实现，纯本地、零云。

电脑端跑一个本地 HTTP 服务，通过 WebUI 配置一组带图标的按钮。手机扫码打开网页，横屏显示图标网格，点一下手机上的按钮，对应的 macOS 命令就在电脑上执行。

支持四类动作：

| 类型 | 含义 | 示例 |
|---|---|---|
| `shell` | 用 `zsh -lc` 执行任意命令 | `open ~/Downloads` |
| `open` | `open` 命令（App 名 / 文件路径 / URL） | `Safari` 或 `https://github.com` |
| `applescript` | 一段 AppleScript | `tell application "Music" to playpause` |
| `keystroke` | 模拟快捷键发送给前台 App | `cmd+shift+4` / `ctrl+cmd+q` |

## 快速开始

```bash
bun install
bun run start
```

输出类似：

```
┌──────────────────────────────────────────────
│ phone-panel running
│ config:  /Users/you/.config/phone-panel/config.json
│ admin :  http://127.0.0.1:7788/admin
│ panel :  http://192.168.1.10:7788/panel?token=xxxxxxxx
└──────────────────────────────────────────────
```

1. 浏览器打开 `http://127.0.0.1:7788/admin`，编辑按钮、扫码配对
2. 手机扫二维码，自动进入 `/panel`，token 会持久化到 localStorage
3. iOS 用「添加到主屏幕」、Android 用「安装应用」即可全屏横屏运行（PWA manifest 已配置）

## 权限提示

第一次执行 `keystroke` 或访问辅助功能相关的 AppleScript 时，macOS 会弹出权限申请：

- **系统设置 → 隐私与安全 → 辅助功能**：勾选你运行 Bun 的终端（iTerm / Terminal）
- 必要时也勾选 **输入监控**

## 安全模型

- 服务监听 `0.0.0.0:7788`，但只在**局域网内**可访问（不要把端口转发到公网）
- `/admin` 和所有 `/api/admin/*` 接口**只接受来自 127.0.0.1 的请求**
- `/api/execute/:id` 和 `/api/config` 需要 bearer token（在 URL `?token=` 或 header `x-panel-token`）
- Token 32 位随机十六进制，存在 `~/.config/phone-panel/config.json`
- 想让旧手机失效，在 admin 点「重置 token」即可

## 配置文件

`~/.config/phone-panel/config.json` 结构：

```json
{
  "token": "...",
  "port": 7788,
  "buttons": [
    {
      "id": "uuid",
      "label": "构建",
      "icon": "🔨",
      "color": "#3b82f6",
      "type": "shell",
      "command": "cd ~/proj && bun run build"
    }
  ]
}
```

可以直接手改，也可以走 WebUI。

## 开机自启（LaunchAgent）

仓库里有现成模板：`launchd/com.greatbody.phone-panel.plist`。一键安装：

```bash
# 1) 把 __USER__ 替换成当前用户，写入 LaunchAgents
sed "s|__USER__|$USER|g" launchd/com.greatbody.phone-panel.plist \
  > ~/Library/LaunchAgents/com.greatbody.phone-panel.plist

# 2) 加载并启用
launchctl load -w ~/Library/LaunchAgents/com.greatbody.phone-panel.plist

# 3) 验证
launchctl list | grep phone-panel    # 第二列 0 表示上次正常退出
curl -s http://127.0.0.1:7788/api/admin/config | head -c 80
```

常用运维命令：

```bash
# 重启（改了代码后）
launchctl kickstart -k gui/$UID/com.greatbody.phone-panel

# 看日志
tail -f /tmp/phone-panel.log

# 临时停一会
launchctl unload ~/Library/LaunchAgents/com.greatbody.phone-panel.plist

# 完全卸载
launchctl unload ~/Library/LaunchAgents/com.greatbody.phone-panel.plist
rm ~/Library/LaunchAgents/com.greatbody.phone-panel.plist
```

模板默认行为：登录后自启 / 崩溃后 5 秒重拉 / 日志写 `/tmp/phone-panel.log`。
如果 bun 装在别处（不是 `~/.bun/bin/bun`），改模板的 `ProgramArguments` 第一项。

## 开发

```bash
bun run dev          # 热重载
bun run typecheck    # tsc --noEmit
```

代码结构：

```
src/
  server.ts       HTTP 入口（路由、鉴权）
  config.ts      读写 ~/.config/phone-panel/config.json
  executor.ts    四类动作执行器（含 keystroke -> AppleScript 编译）
  net.ts         局域网 IP 探测
  types.ts       类型定义
public/
  admin.html     电脑端配置页
  panel.html     手机端面板页
  manifest.json  PWA 清单
```

## License

MIT
