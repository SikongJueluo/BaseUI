# BaseUIRender 概览

## 背景

当前 BaseUI 客户端体系由三层协作构成：

- `BaseUIScreen`：Screen 封装与调用入口
- `BaseUIRender`：帧级渲染协调层
- `BaseUIElement`：retained tree（组件树、布局、输入分发、递归渲染）

其中 `BaseUIRender` 的定位不是“新组件基类”，而是连接 Screen 生命周期与 UI 树执行语义的桥接层。

---

## 核心定位

`BaseUIRender` 是：

- Screen/Forge 到 UI 树的统一渲染入口
- 帧级上下文与生命周期协调边界
- 运行时容错与状态收尾的执行点

`BaseUIRender` 不是：

- 组件树管理者
- 布局算法执行者
- 事件系统所有者
- 业务状态容器

---

## 能力分层

### 1) Framework（框架边界）

- 明确 `BaseUIRender` 作为帧级协调层
- 保持 retained tree 连续演进
- 保障全局 UI 状态（focus/press/scissor）在 Screen 切换时可安全释放
- 预留主题、渲染原语、调试可视化扩展边界

### 2) Interface（接口契约）

- 定义最小契约：帧入口、输入转发、生命周期钩子、状态收尾
- 定义独立 `RenderContext` 契约（graphics/mouse/partialTick/size/debug）
- 明确职责防线：不得吞并 `BaseUIElement` 与容器职责
- 未来能力以 TODO 扩展点形式显式标注

### 3) Runtime（运行时语义）

- `renderFrame` 固定顺序：preflight -> main render -> post-step
- 单帧异常隔离：记录并继续后续帧
- preflight 失败熔断：阻断渲染、执行清理、持续拒绝后续请求

---

## 与 BaseUIScreen 的关系

`BaseUIScreen` 负责对调用方提供统一 API 与 Screen 壳行为；
`BaseUIRender` 负责屏幕运行期间的帧级协调与执行语义。

可视化关系：

```text
Caller
  │ open(rootFactory[, options])
  ▼
BaseUIScreen
  │ lifecycle/input bridge
  ▼
BaseUIRender
  │ frame orchestration + runtime safety
  ▼
BaseUIElement Root Tree
```

---

## 当前阶段边界

当前规范聚焦“可稳定运行的渲染协调层”，不把以下能力作为本期实现前置：

- 完整主题/皮肤系统
- 插件化热更新
- 完整 reconcile 引擎实现
- 缓存/批处理等高级性能优化

这些能力仅作为后续阶段扩展方向。
