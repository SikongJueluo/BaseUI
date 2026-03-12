# BaseUIRender 框架探索概览

## 背景

当前仓库已经具备一套较清晰的保留式（retained-mode）UI 树结构：

- `BaseUIElement` 负责树形结构、锚点布局、裁剪、焦点、事件路由与基础渲染入口。
- `BaseUIButton`、`BaseUISlider`、`BaseUIScrollView`、`BaseUIGrid` 已经分别体现出组件、复合组件、布局容器的角色分层。
- `BaseUINineSliceTexture` 已经承担了一个很明确的“渲染原语/绘制资源”职责。
- `BaseUIRender` 目前仍为空类，因此正好可以被定义为**渲染框架总线/服务层**，而不是继续把更多职责塞回 `BaseUIElement`。

这意味着，`BaseUIRender` 的最佳定位并不是“另一个超大基类”，而是一个站在 `BaseUIElement` 树之上的**客户端 Screen/Forge 集成层 + 渲染协调边界**。

> 方向更新：
> 本项目后续**不再以 XML 作为热加载主载体**。
> “前端”表达形式采用类似 Rust GPUI 的**链式声明式调用风格**（declarative-in-code），而非外部标记语言。
> 
> **阶段约束（重要）**：项目刚启动，当前阶段**不实现热加载**。
> 插件化 Java 热更新（UI/逻辑热替换）仅作为后续阶段预留方向，不纳入当前里程碑实现。

---

## 建议的总体结构

建议把整个客户端 UI 渲染框架拆成五层（其中插件运行时为后续阶段）：

```text
Screen / Forge Client Hook
        │
        ▼
Future Plugin Runtime (Java)
  - plugin lifecycle
  - classloader isolation
  - hot swap coordinator
        │
        ▼
BaseUIRender  ← Screen 桥接、帧入口、全局渲染工具边界
        │
        ▼
BaseUIElement Tree  ← 组件树、布局、命中测试、焦点、事件路由
        │
        ▼
Render Primitives / Assets
  - BaseUINineSliceTexture
  - text/icon/shape/background helpers
```

### 1. Screen / Forge Client Hook 层

职责：

- 与 Minecraft Forge 的客户端生命周期对接。
- 将 `GuiGraphics`、鼠标坐标、partial tick、屏幕尺寸等信息交给 `BaseUIRender`。
- 承接 Screen 级别的生命周期，例如打开、关闭、resize、tick。

这一层不应承担复杂绘制逻辑。

### 2. Future Plugin Runtime (Java) 层（后续阶段）

职责：

- 作为 Java 热加载的运行时容器，负责插件加载、卸载、重载。
- 通过类加载器隔离插件实现，避免旧逻辑类残留。
- 在热更新时协调“状态快照 -> 新插件加载 -> 状态迁移 -> 原子切换 -> 回滚”的流程。
- 对外暴露稳定的 Host API（渲染入口、状态访问、动作注册、生命周期钩子）。

这一层是“热加载控制面”，不是 UI 组件树本身。
当前阶段仅保留接口边界，不做实现。

### 3. BaseUIRender 层

职责：

- 作为**帧级渲染入口**，桥接 Minecraft `Screen` 生命周期与根 `BaseUIElement`。
- 负责根节点 render / input forwarding / resize / close-reset 这一类屏幕级协调工作。
- 作为少量全局渲染工具的聚合边界，例如共享 alpha 处理、调试层挂载、公共原语访问入口。
- （后续）作为插件运行时与 UI 树之间的执行边界：
  - 接收插件构建出的 UI 描述/构建结果
  - 驱动 reconciler 将变化应用到 retained tree
  - 与 dirty-sign 体系协同决定最小化刷新
- 为未来可能出现的扩展保留入口，例如：
  - 调试 overlay
  - 主题/皮肤查找
  - 文本、九宫格、图标等公共绘制帮助器
  - 更高级的分层或批处理能力

这一层**不直接拥有业务状态**，也**不替代组件树的布局与事件路由**；它首先是桥接层，其次才是扩展入口。

### 4. BaseUIElement Tree 层

职责：

- 继续保持当前仓库已经形成的核心职责：树结构、锚点布局、渲染递归、焦点与事件分发。
- 组件通过 `drawSelf(...)` 表达自身视觉内容。
- 容器通过 `addChild/removeChild/refreshLayout/rearrangeGrid` 等模式组织内部布局。

这一层是 UI 的“声明结构”，不是帧级引擎总线。

### 5. Render Primitives / Assets 层

职责：

- 提供独立、可复用的绘制原语。
- 典型对象包括：九宫格纹理、文本样式、图标集、纯色矩形、边框、阴影。
- 这些能力最好由 `BaseUIRender` 聚合调度，但自身保持数据对象或轻量工具职责。

---

## BaseUIRender 应具备的核心功能

`BaseUIRender` 建议具备以下功能分区：

### A. Render Session / Frame Entry

它应该有一个非常清晰的“每帧渲染入口”概念：

- 接收 `Screen` 传入的当前帧参数
- 调用根节点渲染
- 在必要时做状态清理与安全收尾

原因：目前 `BaseUIElement.render(...)` 已经很强，但它缺少一个**更上层的统一 Screen/帧语义**。`BaseUIRender` 正适合承担这个角色。

### A2. Plugin Bridge / Reconciler Entry（后续）

应为插件化热加载预留统一入口（当前不实现）：

- 接收插件侧链式 DSL 构建出的 UI 描述
- 将新描述与当前运行树做差异化应用（reconcile）
- 与 dirty-sign 协同，避免整树全量重建

原因：这是后续演进方向，当前先把边界留对，避免未来改动时推翻主干设计。

### B. Render Context 封装

建议预留一类“渲染上下文”概念，但在当前阶段保持轻量，至少包含：

- `GuiGraphics`
- partial tick / delta
- 鼠标位置
- 屏幕尺寸 / UI 缩放
- 当前主题/皮肤
- 调试标志
- 将来可能加入的层级信息、批处理缓存、统计信息

原因：当前组件直接依赖 Minecraft 对象是合理的，但随着功能增多，若没有统一上下文，组件会越来越难以协作和扩展。

### C. Theme / Skin 入口

建议把主题系统作为 `BaseUIRender` 的**可选未来入口**，而不是当前主职责；这样可以避免在项目早期把平台层做得过重。

应支持的能力：

- 组件状态到视觉资源的映射（normal / hover / pressed / disabled）
- 统一颜色、留白、边框、字号、圆角、阴影定义
- 将来的主题切换或资源包适配

### D. Primitive Drawing Services

建议把以下绘制能力视为 `BaseUIRender` 可统一访问的原语服务，但它们应保持“辅助层”定位：

- 九宫格绘制
- 文本绘制
- 图标绘制
- 纯色矩形/边框/高亮框
- 调试辅助层绘制

这样组件只表达“我要画什么”，而不是每个组件都独立管理同一套绘制细节。

### E. Debug / Inspection 能力

建议从一开始就预留调试接口：

- 显示组件边界
- 显示裁剪区域
- 显示焦点元素
- 显示当前 press target
- 显示布局结果与绝对坐标

这对 UI 框架非常关键，因为布局与输入问题在没有可视化时极难定位。

### F. Optional Future: Extraction / Batching

当前项目体量还不需要过早实现复杂批处理，但文档上应保留方向：

- 未来可以把“组件决定画什么”和“真正提交绘制命令”分离
- 这样便于性能统计、缓存和统一排序

这属于 forward-looking 能力，不应在当前阶段抢占设计中心。

### G. Plugin Lifecycle Safety（后续）

热加载路径需要明确的生命周期与失败兜底（当前只做文档预留）：

- onLoad / onStart / onStop / onReload hooks
- parse/构建失败时回滚到旧插件与旧 UI 树
- 卸载时释放所有注册动作、监听器、缓存对象引用

原因：Java 插件热更新的主要风险不是“能不能重载”，而是“重载后旧类和旧状态是否真正可回收”。

---

## 应预留的接口与扩展点

以下接口不一定现在就实现，但应在框架上明确留口：

### 1. 根节点渲染入口

用于让 Screen 或外部系统把某个 root element 交给 `BaseUIRender` 驱动。

### 2. 渲染上下文接口

用于让组件或绘制原语访问统一上下文，而不是无限扩散 `Minecraft.getInstance()` 风格访问。

### 3. 主题提供者接口（可选）

用于未来支持：

- 默认主题
- 自定义主题
- 不同组件族的视觉策略

### 4. 调试绘制接口（推荐预留）

用于挂载额外调试层，不污染正式渲染逻辑。

### 5. Render Primitive Provider（可选）

用于统一管理九宫格、文本、图标、基础图形等绘制服务。

### 6. 生命周期接口

建议在文档上预留：

- `initialize`
- `resize`
- `tick`
- `render`
- `dispose/reset`

特别是 `reset/dispose` 很重要，因为当前 `BaseUIElement` 已经存在全局状态重置需求；在 Screen 切换时应明确触发状态释放。

### 7. Plugin Runtime Host API（后续）

用于定义插件与宿主之间的稳定契约：

- 插件注册与动作注册入口
- 状态读取/提交入口（SSOT）
- UI 构建入口（链式 DSL -> UI 描述）
- 热重载事务接口（prepare/commit/rollback）

### 8. State Migration API（后续）

用于热加载时保留交互上下文：

- 文本输入值、光标位置、选区
- 焦点目标 ID
- 滚动位置与局部组件状态
- 失败时恢复旧状态快照

---

## 不建议让 BaseUIRender 承担的职责

为了避免未来失控，建议明确排除以下职责：

- 不负责组件业务逻辑
- 不负责组件树本身的数据结构管理
- 不负责取代 `BaseUIElement` 的事件冒泡机制
- 不负责布局算法本身（例如 Grid / ScrollView 的内部排版）
- 不负责直接存储按钮、滑块等控件的业务状态
- 不负责复制 `BaseUIElement` 已有的 render traversal / scissor / focus / press target 机制
- 不负责承担插件类加载细节（这些属于 Plugin Runtime 层）

否则 `BaseUIRender` 会迅速退化成另一个“全知全能 God Object”。

---

## 建议优先级

如果后续真的进入实现阶段，建议优先顺序如下：

1. 先定义 Java 链式 UI 表达边界（declarative-in-code）与现有 `BaseUIElement` 的映射规则
2. 再定义 `BaseUIRender` 的核心职责：Screen 桥接、根节点驱动、状态 reset
3. 再定义轻量 RenderContext 与跨组件共享渲染工具
4. 之后决定 Theme / Primitive Provider / Debug Overlay 扩展接口
5. 最后考虑高级能力（缓存、脏区刷新、批处理优化）
6. 插件化热更新（类加载器隔离、状态迁移、回滚）放入下一阶段

这样能最大程度复用当前已经成型的 `BaseUIElement` 体系，同时避免一开始就过度设计。

---

## 当前阶段已确认决策（M1）

### 决策 1：链式 DSL 覆盖范围

- DSL 当前只覆盖：**layout + style**。
- DSL 当前不直接承载业务 event 逻辑。

这样可避免把“视觉描述”与“业务交互”耦在同一层。

### 决策 2：event 通过装饰器抽离

- event 层采用装饰器/行为包装方式注入。
- 组件树保持基础输入分发能力，但业务动作绑定不写进 DSL 主体。

这样可以让 UI 结构表达保持稳定，也便于未来替换业务逻辑而不动布局样式定义。

### 决策 3：DSL -> Tree 使用增量 reconcile

- 采用增量 reconcile，而非每次全量重建 UI 树。
- 目标是减少不必要树变更与重排，提升交互连续性和渲染稳定性。

注意：增量策略依赖稳定节点身份（identity）与差异比较规则，后续需要补文档明确。

### 决策 4：dirty-sign 先做组件级

- 当前只做组件级 dirty 标记。
- 暂不引入离屏缓存/FBO/复杂缓存系统。

该策略符合启动期目标：先把正确性与架构边界打稳，再做性能深水区优化。
