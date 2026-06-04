export type ActionType = "shell" | "open" | "applescript" | "keystroke";
export type IconType = "emoji" | "image";

export interface PanelButton {
  id: string;
  label: string;
  icon: string;       // iconType=emoji 时是字符；iconType=image 时此字段保留供回退/不显示
  /** 默认 "emoji"，老配置无此字段时按 emoji 处理 */
  iconType?: IconType;
  color: string;      // 背景色 hex
  type: ActionType;
  /**
   * 各类型含义：
   * - shell: 完整 shell 命令，用 zsh -lc 执行
   * - open: 传给 `open` 命令的参数（App 名、文件路径、URL）
   * - applescript: AppleScript 源码
   * - keystroke: 形如 "cmd+shift+4" 或 "cmd+tab"，发送给当前前台 App
   */
  command: string;
}

export interface PanelConfig {
  token: string;
  port: number;
  buttons: PanelButton[];
}

export interface ExecuteResult {
  ok: boolean;
  stdout?: string;
  stderr?: string;
  durationMs: number;
  error?: string;
}
