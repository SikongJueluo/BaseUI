# baseui-render-interface-contracts Specification

## Purpose
TBD - created by archiving change define-baseui-render-interfaces. Update Purpose after archive.
## Requirements
### Requirement: BaseUIRender 必须提供最小接口契约集
系统 MUST 为 `BaseUIRender` 定义最小接口契约集，至少覆盖帧渲染入口、输入转发入口、生命周期钩子与状态收尾入口；本阶段仅定义接口，不包含实现。

#### Scenario: 接口优先落地
- **WHEN** 团队在当前里程碑推进 BaseUIRender
- **THEN** 交付物必须是接口定义与文档注释
- **AND** 不得在同一里程碑引入具体运行时实现逻辑

### Requirement: BaseUIRender 接口必须声明职责防线
`BaseUIRender` 接口 MUST 明确不接管 `BaseUIElement` 及布局容器已有职责，包括树递归渲染、布局算法、事件冒泡、焦点树与 press target 管理。

#### Scenario: 防止职责吞并
- **WHEN** 设计或扩展 BaseUIRender 接口
- **THEN** 接口语义只能描述帧级协调与桥接行为
- **AND** 不得新增直接替代 `BaseUIElement` 树职责的方法语义

### Requirement: 渲染上下文接口必须作为独立契约存在
系统 MUST 为渲染上下文定义独立接口契约，用于承载当前帧共享信息（如 `GuiGraphics`、鼠标坐标、partial tick、屏幕尺寸、调试标志），避免分散访问。

#### Scenario: 上下文统一
- **WHEN** 组件或渲染服务需要读取帧信息
- **THEN** 应通过统一上下文接口访问
- **AND** 不得要求每个模块自行拼装或重复传递同类上下文数据

### Requirement: 未来能力必须以 TODO 扩展点标记
对于当前阶段不实现但未来需要的能力（主题/皮肤入口、渲染原语聚合、插件桥接、reconcile、缓存与批处理优化），接口文档 MUST 使用 TODO 标记保留扩展点。

#### Scenario: 阶段性边界清晰
- **WHEN** 变更中涉及未来能力
- **THEN** 必须以 TODO 形式在契约中显式标识为后续工作
- **AND** 不得把 TODO 项当作当前里程碑实现目标

