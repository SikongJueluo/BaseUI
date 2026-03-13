# Spec: BaseUIRenderBridge 统一能力规范（Framework / Interface / Runtime）

## Status

Draft

## Goal

将 `BaseUIRenderBridge` 相关能力在**单一 capability 文件夹**内统一管理，消除跨文件夹重复定义，形成清晰且可追踪的三层规范：

- Framework（框架定位与边界）
- Interface（接口契约）
- Runtime（运行时执行与容错）

## Scope Note

当前里程碑覆盖：

- `BaseUIRenderBridge` 的帧级协调定位与职责边界
- `BaseUIRenderBridge` 最小接口契约与上下文契约
- `renderFrame` 运行时流水线、异常隔离与熔断行为

当前里程碑不覆盖（MUST 作为 TODO/后续阶段能力）：

- 主题/皮肤系统完整实现
- 插件化热更新实现
- DSL -> Tree 完整 reconcile 实现
- 缓存/批处理/复杂渲染优化落地

## Why

### 为什么 BaseUIRenderBridge 必须定义为帧级协调层

- 现有 `BaseUIElement` 已承担树结构、布局、事件分发与递归渲染。
- 若 `BaseUIRenderBridge` 再承担组件职责，会造成职责重叠并引发架构失焦。

### 为什么要坚持 retained tree 连续性

- 现有组件/容器已围绕 retained 模式形成稳定演进路径。
- 保持连续性可以降低迁移成本，避免对现有行为模型造成破坏。

### 为什么必须强调全局 UI 状态安全释放

- focus / press / scissor 等状态跨 Screen 生命周期易产生残留风险。
- 明确 reset/dispose 时机是防止 zombie 状态与跨屏污染的关键。

### 为什么主题/原语/调试只预留边界

- 这些能力确实是长期演进必需，但当前阶段优先级低于核心桥接稳定性。
- 先预留接口边界，可避免后续扩展时推翻主干设计。

### 为什么 DSL 当前仅聚焦 layout/style

- 结构与样式语义稳定，适合先收敛成规范化表达。
- 业务事件变化快，过早并入 DSL 会导致语义膨胀与耦合。

### 为什么 DSL->Tree 采用增量 reconcile 方向

- 全量重建会破坏交互连续性（焦点、滚动、按压状态）。
- 增量更新更利于稳定性与后续性能优化路径。

### 为什么性能优化能力必须后置

- 启动期首要目标是职责正确性与执行语义稳定，而非复杂性能工程。
- 先组件级 dirty 标记，再逐步引入缓存/批处理，可显著降低返工风险。

### 为什么需要最小接口契约集

- 帧入口、输入转发、生命周期、状态收尾是渲染协调层的最小可用面。
- 先收敛最小契约可保证实现边界可验证、可测试。

### 为什么接口必须声明职责防线

- 若契约语义不设防线，后续实现极易吞并 `BaseUIElement` 和容器职责。
- 在接口层提前约束，可降低“God Object”演化风险。

### 为什么 RenderContext 必须独立成契约

- 帧级共享信息若分散传递，会造成重复拼装与上下文不一致。
- 独立上下文契约能保证跨模块访问一致性与可演进性。

### 为什么未来能力必须以 TODO 标记

- 主题、插件、reconcile、缓存等属于后续阶段，必须与当前验收目标解耦。
- TODO 显式标注可避免评审时发生范围漂移。

### 为什么 runtime 需要固定流水线

- preflight -> render -> post-step 的固定顺序是可预测行为与问题定位的前提。
- 无序执行会放大状态不一致与异常定位成本。

### 为什么单帧异常必须隔离

- UI 渲染是持续循环，单帧失败不应导致整体渲染中断。
- 异常隔离可提升可用性，并保留后续恢复机会。

### 为什么 preflight 失败要熔断并自清理

- preflight 失败通常表示关键前置条件被破坏，继续渲染会扩大错误面。
- 熔断 + 清理 + 持续拒绝可确保系统进入可控失败状态，避免反复损坏。

## Requirements

### Requirement: [Framework] BaseUIRenderBridge 必须是帧级渲染协调层

`BaseUIRenderBridge` MUST 被定义为客户端 UI 的帧级渲染协调层与 Screen/Forge 桥接边界，而不是新的组件基类。

#### Scenario: 渲染入口分工

- **Given** `BaseUIElement` 已承担组件树递归渲染与事件分发
- **When** 框架定义 `BaseUIRenderBridge` 职责
- **Then** `BaseUIRenderBridge` 应组织 Screen 到根节点的渲染/输入桥接与生命周期协调
- **And** 不应重复承载布局树、子节点管理、焦点树遍历等 `BaseUIElement` 职责

### Requirement: [Framework] 框架必须保留 retained UI tree 连续性

框架 MUST 以现有 retained tree 为中心继续演进。

#### Scenario: 结构保持连续性

- **Given** `BaseUIElement` 已提供 children/layout/focus/press/scissor/render recursion
- **When** 设计 `BaseUIRenderBridge`
- **Then** `BaseUIRenderBridge` 应作为 UI 树外层协调者
- **And** 不得破坏现有组件/容器演进路径

### Requirement: [Framework] 框架必须考虑全局 UI 状态安全释放

框架 MUST 在 Screen 切换、关闭或根节点失效时考虑释放 `BaseUIElement` 持有的全局 UI 状态。

#### Scenario: 焦点与按压状态跨界残留

- **WHEN** Screen 被关闭、切换或重建
- **THEN** 必须定义明确 reset/dispose 时机
- **AND** 避免 zombie focus、残留 press target、异常 scissor 状态

### Requirement: [Framework] 框架应预留主题/原语/调试扩展边界

框架 SHOULD 为主题入口、渲染原语聚合与调试检查能力预留扩展边界，但 MUST NOT 作为本期交付前置条件。

#### Scenario: 渐进扩展

- **WHEN** 当前阶段评审架构边界
- **THEN** 应可识别主题/原语/调试扩展入口
- **AND** 不要求其完整实现

### Requirement: [Framework] 链式 DSL 边界必须保持阶段性收敛

当前阶段 DSL MUST 以 layout/style 表达为主；业务动作绑定 MUST NOT 成为 DSL 主体能力。

#### Scenario: 启动期语义边界

- **WHEN** 定义 DSL 语义范围
- **THEN** 覆盖 layout/style
- **AND** 业务事件通过行为层/装饰器注入

### Requirement: [Framework] DSL 到 Tree 映射应采用增量 reconcile 方向

框架 SHOULD 采用增量 reconcile，将 DSL 描述变化按差异应用到 retained tree，而非每次全量重建。

#### Scenario: 结构更新

- **WHEN** 运行时 UI 根据状态变化更新结构或样式
- **THEN** 应优先复用未变化节点并最小化树改动
- **AND** 需定义 identity 与差异比较规则

### Requirement: [Framework] 性能优化能力应后置且不主导当前设计

框架 SHOULD 在架构上允许缓存、批处理、脏区刷新等优化；当前阶段 MUST NOT 把这些能力作为主轴。

#### Scenario: 启动期性能策略

- **WHEN** 制定本期性能策略
- **THEN** 先采用组件级 dirty 标记
- **AND** 高复杂度优化作为后续阶段扩展

### Requirement: [Interface] BaseUIRenderBridge 必须提供最小接口契约集

系统 MUST 为 `BaseUIRenderBridge` 定义最小接口契约集，至少覆盖帧渲染入口、输入转发入口、生命周期钩子与状态收尾入口；当前阶段 MUST 在保留契约的同时提供受控运行时实现。

#### Scenario: 接口与实现共同交付

- **WHEN** 团队推进 BaseUIRenderBridge 当前里程碑
- **THEN** 交付物必须包含接口定义、文档注释与对应运行时实现
- **AND** 实现必须遵守接口职责边界

### Requirement: [Interface] BaseUIRenderBridge 接口必须声明职责防线

`BaseUIRenderBridge` 接口 MUST 明确不接管 `BaseUIElement` 及布局容器既有职责，包括树递归渲染、布局算法、事件冒泡、焦点树与 press target 管理。

#### Scenario: 防止职责吞并

- **WHEN** 设计或扩展 BaseUIRenderBridge 接口
- **THEN** 接口语义只能描述帧级协调与桥接行为
- **AND** 不得新增直接替代 UI 树职责的方法语义

### Requirement: [Interface] 渲染上下文必须作为独立契约存在

系统 MUST 为渲染上下文定义独立契约，用于承载当前帧共享信息（如 `GuiGraphics`、鼠标坐标、partial tick、屏幕尺寸、调试标志），避免分散访问。

#### Scenario: 上下文统一

- **WHEN** 组件或渲染服务需要读取帧信息
- **THEN** 应通过统一上下文接口访问
- **AND** 不得要求模块重复拼装同类上下文数据

### Requirement: [Interface] 未来能力必须以 TODO 扩展点标记

对于当前阶段不实现但未来需要的能力（主题/皮肤入口、渲染原语聚合、插件桥接、reconcile、缓存与批处理优化），接口文档 MUST 使用 TODO 标记保留扩展点。

#### Scenario: 阶段性边界清晰

- **WHEN** 契约涉及未来能力
- **THEN** 必须以 TODO 形式显式标识为后续工作
- **AND** 不得将 TODO 项纳入本期验收目标

### Requirement: [Runtime] BaseUIRenderBridge runtime 必须提供确定帧流水线

系统 MUST 提供 `BaseUIRenderBridge` 运行时实现，并在 `renderFrame` 中按固定顺序执行：前置检查、主渲染、帧后处理。

#### Scenario: 固定渲染顺序

- **WHEN** 渲染器处于可用状态并收到 `renderFrame(context)`
- **THEN** 必须先执行 preflight，再执行根节点渲染，最后执行帧后收尾

### Requirement: [Runtime] 渲染异常必须被隔离并记录

在 `renderFrame` 主渲染过程中，运行时异常 MUST 被捕获并记录日志，系统 MUST 继续后续帧处理，不得因单帧异常导致渲染循环整体中断。

#### Scenario: 单帧异常容错

- **WHEN** 某一帧渲染抛出运行时异常
- **THEN** 实现必须记录错误并结束当前帧
- **AND** 下一帧渲染调用仍可继续执行

### Requirement: [Runtime] preflight 失败必须触发永久熔断与自清理

当前置检查失败时，系统 MUST 阻断当前帧渲染并进入不可恢复状态；之后所有渲染请求 MUST 被拒绝。实现 MUST 触发自清理并输出日志。

#### Scenario: preflight 失败后停止渲染

- **WHEN** preflight 检查返回失败
- **THEN** 当前帧不得进入主渲染步骤
- **AND** 渲染器必须执行清理并记录失败日志
- **AND** 后续 `renderFrame` 调用必须被持续拒绝
