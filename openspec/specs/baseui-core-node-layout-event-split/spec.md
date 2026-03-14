## ADDED Requirements

### Requirement: BaseUIElement MUST 仅承担节点与最小行为职责
`BaseUIElement` MUST 仅包含节点基础数据（位置/尺寸/可见性/Z/父子关系）与生命周期回调，不得继续承载完整布局主循环与完整事件分发主流程。

#### Scenario: 节点职责收敛
- **Given** 现有 BaseUIElement 同时包含布局、渲染、事件、全局状态访问
- **When** 完成核心重构
- **Then** BaseUIElement 只保留节点与回调职责
- **Why** 单一职责可降低维护与回归成本

### Requirement: 系统 MUST 提供独立 UILayoutEngine 并统一 dirty 传播
系统 MUST 提供独立布局引擎处理锚点计算、dirty 标记传播、防重入更新，并为节点树提供可重复验证的布局结果。

#### Scenario: 布局引擎统一更新
- **WHEN** 任一节点尺寸、锚点或偏移发生变化
- **THEN** 布局引擎必须统一驱动 dirty 传播与布局更新
- **THEN** 更新结果必须可用于同帧渲染与事件命中
- **Why** 布局逻辑集中后可独立测试与优化

### Requirement: 系统 MUST 提供独立 UIEventDispatcher 并保持既有事件语义
系统 MUST 提供独立事件分发器处理鼠标与键盘路由；其行为 MUST 与现有语义一致（逆序命中、焦点、按压目标、拖拽与滚轮处理）。

#### Scenario: 事件行为保持不变
- **WHEN** 用户执行点击、释放、拖拽、滚轮、键盘输入
- **THEN** 事件消费顺序与焦点/按压语义必须与重构前一致
- **THEN** 不得引入跨帧状态泄漏
- **Why** 重构不应破坏上层交互契约

### Requirement: 指针命中 MUST 优先使用当帧渲染快照并提供降级路径
指针命中（点击/滚轮）MUST 优先基于当帧渲染快照（绝对坐标、裁剪、排序）进行；当快照不可用时 MUST 降级到树遍历命中，且行为可预测。

#### Scenario: 快照优先命中
- **WHEN** 本帧已完成渲染命令收集并存在可用快照
- **THEN** 点击与滚轮命中必须按快照顺序计算
- **THEN** 命中顺序必须与视觉顺序一致
- **Why** 统一数据来源可避免视觉与交互错位

#### Scenario: 无快照降级
- **WHEN** 当前无可用快照（如首帧/异常恢复）
- **THEN** 系统必须降级为树遍历命中
- **THEN** 不得抛出异常或进入不可交互状态
- **Why** 保证系统鲁棒性与可恢复性

### Requirement: 渲染路径 MUST 保持 Vulkan-safe 抽象与低 GC 特性
渲染路径 MUST 继续使用 Mojang 抽象（`GuiGraphics`、`RenderSystem`）与官方裁剪接口（`enableScissor/disableScissor`），并 MUST NOT 引入 `GL11` 直接调用；命令缓冲 MUST 保持 SoA 与复用策略以降低 GC。

#### Scenario: Vulkan-safe 约束
- **WHEN** 执行任意 UI 渲染与裁剪逻辑
- **THEN** 必须只使用 `GuiGraphics`/`RenderSystem` 抽象
- **THEN** 不得出现 `org.lwjgl.opengl.GL11` 调用
- **Why** 保障未来渲染后端兼容性

#### Scenario: 低分配渲染循环
- **WHEN** render 高频执行（高 FPS）
- **THEN** 关键路径不得因命令对象频繁创建导致明显 GC 抖动
- **THEN** 命令缓冲必须通过数组或复用容器管理
- **Why** 避免 UI 卡顿峰值

### Requirement: BaseUIElement MUST 去除 CRTP 自引用泛型
`BaseUIElement<T extends BaseUIElement<T>>` MUST 被移除，节点 API MUST 采用可读性更高的非 CRTP 形式，并保持主流调用路径可迁移。

#### Scenario: API 可读性提升
- **WHEN** 开发者阅读或继承 BaseUIElement
- **THEN** 不应再出现 self 强转与自引用泛型签名
- **THEN** 常见 setter 调用应可平滑迁移
- **Why** 减少泛型噪音与认知负担
