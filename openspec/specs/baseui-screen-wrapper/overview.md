# BaseUIScreen 概览

## 背景

当前仓库已经有：

- `Screen` 侧入口（如 `ExamplePlaceholderScreen`）
- `BaseUIRender` 帧级协调层
- `BaseUIElement` retained tree（布局、事件、递归渲染）

但调用方若要新开一个 BaseUI 屏幕，仍需手动拼接 Screen 生命周期、输入转发、上下文构建和异常兜底。`BaseUIScreen` 的目标是把这条“重复胶水层”标准化。

---

## 设计目标

`BaseUIScreen` 作为统一封装，提供以下能力：

1. **开箱即用**：调用方只需提供 `Supplier<BaseUIElement<?>>`
2. **内置桥接**：默认内置 `BaseUIRender` 协调
3. **可扩展**：允许 options 注入自定义渲染实现
4. **生命周期一致**：固定 `init/resize/onClose+dispose` 映射
5. **输入一致**：全量鼠标/键盘/字符事件转发
6. **安全兜底**：异常时 fallback error overlay + 可关闭

---

## 分层关系

```text
Caller
  │
  │ open(rootFactory[, options])
  ▼
BaseUIScreen (Screen wrapper)
  ├─ lifecycle bridge (init/resize/close)
  ├─ input forwarding (mouse/key/char)
  ├─ context assembly (GuiGraphics + mouse + partialTick + debug)
  └─ failure fallback overlay
        │
        ▼
BaseUIRender (frame-level orchestrator)
        │
        ▼
BaseUIElement root tree (layout + event + recursive render)
```

---

## 推荐 API 形态（契约层）

```java
BaseUIScreen.open(Supplier<BaseUIElement<?>> rootFactory)
BaseUIScreen.open(Supplier<BaseUIElement<?>> rootFactory, BaseUIScreenOptions options)
```

其中 `BaseUIScreenOptions` 至少覆盖：

- `pauseScreen`（默认 `false`）
- `drawBackground`（默认 `true`）
- `debugEnabled`（默认 `false`）
- `rendererFactory` 或等价注入入口（可选）

---

## 生命周期与帧流程

```text
open(...) [client thread]
   │
   ▼
Screen init
   └─ render.initialize(width, height)

each frame:
   render(graphics, mouseX, mouseY, partialTick)
      ├─ background (if enabled)
      ├─ build BaseUIRenderContext
      ├─ render.renderFrame(context)
      ├─ super.render(...)
      └─ fallback overlay (only if error state)

input events:
   mouse/key/char -> render.forwardXXX(context, ...)

resize:
   └─ render.resize(width, height)

close/removed:
   └─ render.onClose() + render.dispose()
```

---

## 失败策略（本期约束）

当 rootFactory 构建或帧渲染出现异常时：

1. 记录错误日志
2. 标记进入 fallback 模式
3. 显示 fallback error overlay
4. 保证用户仍可通过常规方式关闭屏幕

该策略优先保证“可见故障 + 可恢复退出”，避免静默失败或界面卡死。

---

## 非目标（本期不实现）

- 主题/皮肤系统实现
- DSL -> Tree 增量 reconcile 实现
- 缓存/批处理优化
- 插件化热更新

以上能力仅保留为后续阶段扩展点，不作为本期 Screen 封装验收前提。
