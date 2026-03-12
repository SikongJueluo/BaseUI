# Spec: BaseUIRender 框架边界与能力要求

## Status

Draft

## Goal

为 `src/main/java/net/burgerfarm/baseui/Client/Render/BaseUIRender.java` 定义清晰的高层框架定位，使其成为 BaseUI 客户端 UI 渲染体系中的统一入口与扩展边界，并与现有 `BaseUIElement` 树、布局容器、组件实现形成稳定分工。

## Scope Note

当前里程碑仅覆盖：

- Java 链式声明式 UI 表达（declarative-in-code）方向的架构边界
- `BaseUIRender` 与现有 retained tree 的职责划分

当前里程碑不覆盖：

- 插件化热加载实现
- 类加载器隔离实现
- 运行时状态迁移与回滚实现

上述内容可作为后续阶段扩展能力。
## Requirements

### Requirement 1: BaseUIRender 必须是帧级渲染协调层

`BaseUIRender` MUST 被定义为客户端 UI 的帧级渲染协调层与 Screen/Forge 桥接边界，而不是新的组件基类。

#### Scenario: 渲染入口分工

- **Given** 当前仓库中 `BaseUIElement` 已负责组件树递归渲染与事件分发
- **When** 框架为 `BaseUIRender` 定义职责
- **Then** `BaseUIRender` 应负责组织 `Screen` 到根节点的渲染/输入桥接、resize/close 等生命周期协调，以及状态收尾
- **And** 不应重复承载布局树、子节点管理、焦点树遍历等已属于 `BaseUIElement` 的职责

### Requirement 2: 框架必须保留保留式 UI 树结构

框架 MUST 以现有保留式 UI 树为中心继续演进。

#### Scenario: 结构保持连续性

- **Given** `BaseUIElement` 已提供 anchor layout、children、focus、press target、scissor stack、render recursion
- **When** 设计 `BaseUIRender`
- **Then** `BaseUIRender` 应作为该树的外层协调者
- **And** `BaseUIButton`、`BaseUISlider`、`BaseUIScrollView`、`BaseUIGrid` 等现有组件/容器模式应继续成立

### Requirement 3: 框架必须预留统一渲染上下文

框架 MUST 预留统一的 Render Context 概念，用于向组件与渲染服务传递当前帧信息，但其初始形态应保持轻量。

#### Scenario: 上下文统一访问

- **Given** 组件绘制需要访问 `GuiGraphics`、鼠标位置、partial tick、屏幕尺寸、主题、调试标志等信息
- **When** 框架扩展复杂度上升
- **Then** 这些信息应通过统一上下文表达
- **And** 不应长期依赖分散、重复、难追踪的访问方式

### Requirement 4: 框架必须预留主题/皮肤入口

框架 SHOULD 为主题、皮肤或视觉资源映射保留统一入口，但该入口不应成为当前阶段的中心职责。

#### Scenario: 组件视觉状态统一管理

- **Given** 按钮、滑块等组件都存在 normal / hover / pressed / disabled 等视觉状态
- **When** 视觉系统继续演进
- **Then** 框架应允许这些状态未来映射到集中管理的颜色、九宫格、文本样式、图标或边框策略
- **And** 不应要求每个组件永久独立维护完整视觉资源体系

### Requirement 5: 框架必须提供渲染原语聚合边界

框架 SHOULD 为渲染原语提供统一聚合边界。

#### Scenario: 原语能力扩展

- **Given** `BaseUINineSliceTexture` 已经体现出独立绘制原语角色
- **When** 框架引入文本、图标、矩形、边框、调试层等能力
- **Then** 它们应能被 `BaseUIRender` 统一组织或访问
- **And** 保持组件层表达“绘制意图”，而不是复制相同低层细节

### Requirement 6: 框架必须预留调试与检查接口

框架 SHOULD 提供调试可视化与检查能力的挂载点。

#### Scenario: 调试布局与输入问题

- **Given** UI 框架天然存在命中测试、裁剪、焦点、绝对坐标、布局错位等排查需求
- **When** 需要诊断问题
- **Then** 框架应支持展示边界框、裁剪框、焦点对象、按压目标或其他调试信息
- **And** 这些调试能力应能与正式渲染逻辑解耦

### Requirement 7: BaseUIRender 不得吞并布局与事件系统职责

`BaseUIRender` MUST NOT 吞并已经属于 `BaseUIElement` 或布局容器的职责。

#### Scenario: 职责边界保护

- **Given** `BaseUIGrid`、`BaseUIScrollView` 已经承担容器级排版与滚动逻辑
- **When** 设计 `BaseUIRender`
- **Then** `BaseUIRender` 不应直接接管这些布局算法
- **And** 不应重新实现组件树级事件冒泡、焦点切换、press target 管理
- **And** 不应复制 `BaseUIElement` 已有的 render traversal、scissor stack 与 alpha 传递逻辑
- **And** 应保持“渲染服务层”而非“全局超级对象”定位

### Requirement 8: 框架应预留未来性能优化入口，但不强制当前实现

框架 SHOULD 在架构上允许未来引入绘制命令抽取、缓存、批处理或脏区刷新，但 MUST NOT 让这些前瞻能力主导当前设计。

#### Scenario: 渐进式演进

- **Given** 当前项目尚处早期阶段，现有组件体系优先级高于高级渲染优化
- **When** 定义 `BaseUIRender`
- **Then** 文档上应允许未来扩展到 extraction/execution、render command batching 或 cache
- **But** 当前职责边界仍应以简单、清晰、可复用为优先

### Requirement 9: 框架必须考虑全局 UI 状态的安全释放

框架 MUST 在 Screen 切换、关闭或根节点失效时考虑释放 `BaseUIElement` 持有的全局 UI 状态。

#### Scenario: 焦点与按压状态跨界残留

- **Given** `BaseUIElement` 当前使用全局静态状态维护 focus、press target 与 scissor stack
- **When** Screen 被关闭、切换或重建
- **Then** 框架应定义明确的 reset/dispose 时机
- **And** 避免出现 zombie focus、残留 press target 或异常 scissor 状态

### Requirement 10: 框架必须支持链式声明式 UI 表达边界

框架 MUST 支持“declarative-in-code”风格的 UI 构建边界，使上层可以用链式组合方式描述 UI，而底层仍由 retained tree 承载。

#### Scenario: 链式 API 与 retained tree 对接

- **Given** 团队计划采用类似 GPUI 的链式调用风格表达“前端”
- **When** 定义本阶段架构
- **Then** 应明确链式表达层与 `BaseUIElement` 树之间的映射边界
- **And** 不应要求当前阶段引入 XML 或外部标记语言解析流程

### Requirement 11: 插件化热加载能力作为后续阶段扩展

插件化热加载能力 SHOULD 被文档化为后续阶段扩展点，但 MUST NOT 绑定为当前里程碑交付要求。

#### Scenario: 分阶段推进

- **Given** 项目当前处于启动期
- **When** 制定架构文档
- **Then** 应保留插件运行时、类加载器隔离、状态迁移、回滚等接口方向
- **But** 当前阶段验证目标应集中在核心 UI 表达边界与渲染职责稳定性

### Requirement 12: 链式 DSL 在当前阶段仅覆盖 layout/style

当前阶段 DSL MUST 以布局与样式表达为主，不将业务事件绑定作为 DSL 主体能力。

#### Scenario: 启动期语义边界

- **Given** 团队当前采用链式声明式 UI 表达
- **When** 定义 DSL 语义边界
- **Then** DSL 应至少覆盖布局结构与样式配置
- **And** 业务动作绑定不应成为 DSL 核心语义

### Requirement 13: 事件逻辑应通过装饰器/行为层注入

框架 MUST 支持通过装饰器或行为层为组件注入事件逻辑，以实现结构与逻辑解耦。

#### Scenario: 逻辑抽离

- **Given** 组件树仍负责基础输入分发
- **When** 业务侧需要绑定具体动作
- **Then** 应通过装饰器/行为包装接入动作逻辑
- **And** 不要求把业务逻辑直接编码进 DSL 结构描述

### Requirement 14: DSL->Tree 映射采用增量 reconcile

框架 SHOULD 采用增量 reconcile 策略，将 DSL 描述变化按差异应用到 retained tree，而非每次全量重建。

#### Scenario: 结构更新

- **Given** 运行中 UI 需要根据状态变化调整结构或样式
- **When** 执行 DSL 到 UI 树映射
- **Then** 框架应优先复用未变化节点并最小化树改动
- **And** 应明确节点 identity 与差异比较规则，以保证更新可预测

### Requirement 15: dirty-sign 当前仅做组件级标记

当前阶段 dirty-sign MUST 聚焦组件级标记机制，MUST NOT 绑定复杂缓存/FBO 机制作为前置条件。

#### Scenario: 启动期性能策略

- **Given** 项目尚处启动期
- **When** 设计刷新与重绘策略
- **Then** 应先用组件级 dirty 标记控制最小重绘范围
- **And** 离屏缓存、多级缓存或复杂批处理可在后续阶段再引入

### Requirement: BaseUIRender 接口定义阶段必须只交付契约
在 `baseui-render-framework` 能力下，当前阶段 MUST 将 `BaseUIRender` 相关交付限制为接口契约定义与注释说明，不得落地具体实现。

#### Scenario: 里程碑交付边界
- **WHEN** 本阶段完成 BaseUIRender 交付
- **THEN** 输出应为接口与契约文档
- **AND** 不应包含具体渲染、输入、生命周期处理的实现代码

### Requirement: 未来扩展项必须显式 TODO 标注
在该能力下，凡属于未来阶段的内容（主题系统、原语 provider、插件桥接、reconcile、批处理/缓存优化）MUST 在接口层通过 TODO 明确标注为后续工作。

#### Scenario: 未来能力不提前实现
- **WHEN** 文档或接口提及未来扩展能力
- **THEN** 应使用 TODO 标记其阶段属性
- **AND** 保持当前阶段聚焦在职责边界与契约稳定性

