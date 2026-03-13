# Spec: BaseUIScreen 封装契约

## Status

Draft

## Goal

定义 `BaseUIScreen`（位于 `src/main/java/net/burgerfarm/baseui/Client/Screens`）的统一封装契约，使调用方只需提供 `UIElement` 根构建输入，即可获得内置渲染、完整生命周期桥接与安全失败兜底能力。

## Scope Note

当前里程碑覆盖：

- `BaseUIScreen` 类职责与 API 契约
- `Screen` 与 `BaseUIRender` 的桥接流程
- 输入转发、生命周期映射、默认行为与可配置项
- 失败兜底（fallback error overlay）与可关闭保障

当前里程碑不覆盖（MUST 作为 TODO/后续阶段能力）：

- 主题/皮肤系统
- DSL -> Tree 增量 reconcile 实现
- 缓存/批处理优化
- 插件化热更新

## Why

### 为什么是 `BaseUIScreen` 且放在 `Client/Screens`

- 与现有 `ExamplePlaceholderScreen` 所在层次一致，降低调用方发现成本。
- 让“Screen 壳职责”在包结构上可读，避免被误用为通用渲染服务或组件基类。

### 为什么根节点输入只用 `Supplier<BaseUIElement<?>>`

- 保证每次打开屏幕都能按需构建新根树，降低旧状态残留风险。
- 与后续可能的上下文注入（尺寸、配置、依赖）更兼容，避免复用旧实例导致生命周期错位。

### 为什么“内置渲染 + 可选自定义注入”

- 默认路径要简单，满足“开箱即用”。
- 高级调用方仍需要扩展点（调试、实验渲染策略、A/B 方案），因此保留可选注入。

### 为什么生命周期映射固定为 init/resize/onClose+dispose

- `Screen` 生命周期与 `BaseUIRender` 生命周期一一对应，固定映射可减少行为歧义。
- 关闭阶段显式 `dispose` 可降低全局状态残留（focus/press/scissor）风险。

### 为什么输入必须全量转发

- BaseUI 组件树包含鼠标、键盘、字符输入等完整交互语义。
- 只转发部分事件会导致交互行为不一致（例如输入框、拖拽、快捷键）。

### 为什么默认 `isPauseScreen=false` 但可配置

- 非暂停更符合大多数 HUD/轻交互场景；同时保留配置满足库存/模态场景。

### 为什么背景默认开启且可配置

- 默认开启可与 vanilla 屏幕视觉习惯对齐，降低首次接入成本。
- 可配置便于无背景/自定义背景场景。

### 为什么 `debugEnabled` 必须进 options/context

- 调试能力需要帧级透传，且应避免散落到全局静态开关。
- 上下文化后更利于后续调试叠加层扩展。

### 为什么异常策略是 fallback overlay 且可关闭

- 仅日志不足以向用户表达故障状态；直接崩屏会影响可用性。
- fallback overlay 能提供可见故障反馈，同时保留退出路径，避免“卡死在坏屏幕”。

### 为什么 API 同时提供 `open(rootFactory)` 与 `open(rootFactory, options)`

- 简洁入口覆盖 80% 场景，高级入口承接策略配置与注入。
- 降低学习曲线，同时避免把高级参数硬塞进基础路径。

### 为什么必须强调 client thread 打开

- `setScreen` 等客户端 UI 操作线程敏感，线程约束可降低偶发 race 与不稳定问题。

### 为什么高级能力明确后置

- 主题/reconcile/缓存/插件热更新属于高复杂度能力，当前里程碑应优先收敛核心 Screen 封装边界。
- 先稳住可用接口，再逐步扩展，能显著降低返工概率。

## Requirements

### Requirement: BaseUIScreen 必须位于约定包路径并作为统一入口

系统 MUST 提供 `BaseUIScreen`，并将其放置于 `Client/Screens` 层，作为 BaseUI 屏幕封装的统一入口类型。

#### Scenario: 类型与层次一致

- **WHEN** 团队引入 Screen 封装
- **THEN** 类型名必须为 `BaseUIScreen`
- **AND** 包层级必须位于 `Client/Screens`

### Requirement: 根节点输入必须采用 Supplier 工厂模式

`BaseUIScreen` MUST 仅接受 `Supplier<BaseUIElement<?>>` 作为根节点输入来源，用于在打开屏幕时构建根节点。

#### Scenario: 根节点按需构建

- **WHEN** 调用方请求打开 `BaseUIScreen`
- **THEN** 调用方必须提供 `Supplier<BaseUIElement<?>>`
- **AND** 运行时不得要求调用方直接传入已构建根节点实例作为唯一入口

### Requirement: BaseUIScreen 必须内置渲染器且允许可选自定义注入

系统 MUST 默认由 `BaseUIScreen` 内部持有并驱动内置 `BaseUIRender`；系统 MUST 同时允许高级调用方通过可选配置注入自定义渲染实现。

#### Scenario: 默认路径

- **WHEN** 调用方未提供自定义渲染器
- **THEN** `BaseUIScreen` 必须创建并驱动内置 `BaseUIRender`

#### Scenario: 高级自定义路径

- **WHEN** 调用方通过 options 提供自定义渲染器注入能力
- **THEN** `BaseUIScreen` 应使用该注入实现
- **AND** 仍须遵守既有 `BaseUIRender` 职责边界

### Requirement: 生命周期映射必须完整且固定

`BaseUIScreen` MUST 提供固定生命周期映射：`init -> render.initialize`、`resize -> render.resize`、`onClose/removed -> render.onClose + dispose`。

#### Scenario: 打开初始化

- **WHEN** 屏幕完成初始化
- **THEN** 必须调用渲染器 `initialize`

#### Scenario: 屏幕尺寸变化

- **WHEN** 屏幕发生 resize
- **THEN** 必须调用渲染器 `resize`

#### Scenario: 屏幕关闭与释放

- **WHEN** 屏幕关闭或被移除
- **THEN** 必须执行 `render.onClose`
- **AND** 必须执行 `dispose` 以完成状态收尾

### Requirement: 输入事件必须全量转发到渲染协调层

`BaseUIScreen` MUST 将鼠标与键盘相关输入全量转发给渲染协调层，包括 mouse moved/click/release/drag/scroll、key pressed/released、char typed。

#### Scenario: 全输入链路可用

- **WHEN** 运行时接收任一支持输入事件
- **THEN** 该事件必须通过统一桥接转发给渲染协调层

### Requirement: 默认暂停行为必须为 false 且可配置

`BaseUIScreen` MUST 以 `isPauseScreen=false` 作为默认行为，并 SHOULD 支持通过 options 显式开启暂停模式。

#### Scenario: 默认非暂停

- **WHEN** 调用方未配置暂停策略
- **THEN** `isPauseScreen` 必须返回 `false`

#### Scenario: 显式启用暂停

- **WHEN** 调用方在 options 启用暂停
- **THEN** `isPauseScreen` 必须按配置返回暂停行为

### Requirement: 背景绘制必须默认开启且可配置

`BaseUIScreen` MUST 默认开启背景绘制（调用 `renderBackground`）；系统 SHOULD 支持通过 options 关闭或替换默认背景行为。

#### Scenario: 默认背景

- **WHEN** 调用方不传背景策略
- **THEN** 屏幕必须执行默认背景绘制

#### Scenario: 配置覆盖背景

- **WHEN** 调用方在 options 提供背景策略开关
- **THEN** 屏幕应遵循该策略

### Requirement: debugEnabled 必须进入 options 与渲染上下文

系统 MUST 在 `BaseUIScreen` 的 options 中支持 `debugEnabled`，并 MUST 将该标志写入每帧 `BaseUIRenderContext`。

#### Scenario: 调试标志透传

- **WHEN** 调用方启用 debug
- **THEN** `BaseUIRenderContext.debugEnabled` 必须为 true

### Requirement: 异常时必须提供 fallback error overlay 且保持可关闭

在根节点构建或渲染阶段发生运行时异常时，系统 MUST 提供 fallback error overlay，并 MUST 保证用户仍可安全关闭屏幕。

#### Scenario: 运行时异常兜底

- **WHEN** 根节点构建或帧渲染抛出异常
- **THEN** 屏幕必须显示兜底错误覆盖层
- **AND** 用户必须仍可通过常规方式关闭该屏幕

### Requirement: 用户 API 必须提供简洁入口与高级入口

`BaseUIScreen` MUST 同时提供：

- 简洁入口：`open(rootFactory)`
- 高级入口：`open(rootFactory, options)`

其中 `rootFactory` 类型 MUST 为 `Supplier<BaseUIElement<?>>`。

#### Scenario: API 分层

- **WHEN** 调用方仅需默认行为
- **THEN** 可使用 `open(rootFactory)`
- **AND** 当调用方需要自定义行为时可使用 `open(rootFactory, options)`

### Requirement: 打开流程必须遵守客户端线程约束

`BaseUIScreen` 打开流程 MUST 在 client thread 上执行。

#### Scenario: 线程安全打开

- **WHEN** 调用方触发 `open(...)`
- **THEN** 实现必须确保最终 `setScreen` 等打开动作在 client thread 执行

### Requirement: 高级能力必须显式维持为后续范围

当前 `BaseUIScreen` 规范 MUST 明确将主题系统、reconcile、缓存/批处理、插件热更新列为后续能力，不得作为本期交付前置条件。

#### Scenario: 里程碑边界稳定

- **WHEN** 本期评审 `BaseUIScreen` 交付范围
- **THEN** 不得要求实现上述高级能力
- **AND** 可通过 TODO 形式保留扩展接口语义
