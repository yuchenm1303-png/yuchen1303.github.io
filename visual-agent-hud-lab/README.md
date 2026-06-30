# 视觉智能体 HUD 网页调试台

入口：`visual-agent-hud-lab/index.html`

## 一比一原则

调试台不会复制 App 的边缘光、SVG 鼠标、信息面板和时间线实现，而是通过同源 iframe 直接加载：

`ai-ledger-android/app/src/main/assets/visual_agent_hud_runtime.html`

因此 App 资产中的以下文件就是网页预览的真实渲染源：

- `visual_agent_hud_runtime.html`
- `visual_agent_hud_base.css`
- `visual_agent_hud_cursor.css`
- `visual_agent_hud_layout.css`
- `visual_agent_hud_runtime.js`

顶部灵动胶囊由于 App 中由原生 Android View 绘制，网页调试台按照当前 `VisualAgentCapsuleHost.kt` 的尺寸、渐变、圆角、阶段色和展开动画单独复刻，用于整体视觉联调。

## 本地打开

必须从仓库根目录启动静态服务器，不能只双击单个 HTML 文件，否则浏览器可能禁止父页面访问 iframe：

```bash
python -m http.server 8000
```

然后访问：

```text
http://localhost:8000/visual-agent-hud-lab/
```

## 参数回接 App

网页导出的 JSON 与 `VisualAgentHudParameters` 使用相同字段，并保留 `_schemaVersion: 4`。

调试完成后可以：

1. 点击“复制 Kotlin”，得到完整 `VisualAgentHudParameters(...)`。
2. 将确认后的值固化到 `VisualAgentHudTuningStore.kt` 默认参数。
3. 同步检查 `visual_agent_hud_base.css` 与 `visual_agent_hud_cursor.css` 中的初始 CSS 变量，避免首次载入帧与 Kotlin payload 之间闪变。
4. 保持网页运行层和 App WebView 继续共用同一套 HUD 资产，不另建第二套视觉实现。

## 当前功能

- Android 手机、平板和横屏尺寸预览
- 屏幕内拖动鼠标坐标
- 五阶段状态切换
- 点击反馈模拟
- detail / simple / dev 模式
- 截图安全模式
- 原生胶囊折叠、展开和暂停图标预览
- 全部 `VisualAgentHudParameters` 实时滑杆
- JSON 导入、导出、下载
- Kotlin 参数生成
- 浏览器本地方案保存
