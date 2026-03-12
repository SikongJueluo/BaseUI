# BaseUIRender 框架决策原因

## 结论

`BaseUIRender` 应被定义为 **客户端 Screen/Forge 桥接层 + UI 渲染协调边界（render service boundary）**，而不是新的控件基类，也不是把所有能力塞进去的 God Object。

并且在当前项目阶段：

- UI 表达方向采用 **Java 链式声明式风格（declarative-in-code）**。
- 插件化热加载属于**后续阶段**，当前不作为实现目标。

---

## 为什么不是“再造一个核心基类”

从当前代码可以直接看出：

- `BaseUIElement` 已经非常明确地承担了核心树结构职责：
  - anchor layout
  - children 管理
  - z-order
  - focus / press target
  - render recursion
  - mouse / keyboard routing
  - scissor stack
- `BaseUIGrid` 与 `BaseUIScrollView` 已经把“布局容器”和“复合组件”这两类角色做出了区分。
- `BaseUIButton` 与 `BaseUISlider` 已经体现出控件通过 `drawSelf` + 输入回调表达行为的模式。

如果此时把 `BaseUIRender` 再设计成另一个“全能根类”，会出现两个直接问题：

1. **职责重复**：会和 `BaseUIElement` 的 render / event / state 机制发生重叠。
2. **演进混乱**：以后很难判断新增能力该放进 element、component、layout 还是 render。

因此，最合理的做法是：**承认当前 UI 树已经是核心，BaseUIRender 只做其上层协调与 Screen 桥接。**

---

## 为什么选择“保留式 UI 树 + 渲染协调层”

这是当前仓库天然长出来的方向，而不是外部强加。

### 代码信号

- `BaseUIElement.render(...)` 采用树递归与局部坐标体系，这天然是 retained-mode。
- `BaseUIScrollView` 通过内容容器与 scrollbar 组合表达结构，而不是每帧即时拼装。
- `BaseUIGrid` 通过内部重排维持布局结构，依然是 retained layout。

### 外部模式验证

外部参考也支持这个方向：

- Minecraft `GuiGraphics` 提供的是面向上下文的绘制入口，适合由外层协调者统一控制状态。
- LibGDX Scene2D 的经验说明：组件树、布局、输入、渲染入口分层之后，框架会更稳定。
- 现代 Minecraft/Fabric 渲染资料强调 extraction/execution 分离，这也更像是在 retained tree 之上增加渲染协调层，而不是否定 retained tree 本身。

因此，本项目最自然的路线不是转向即时模式 UI，而是：

```text
Retained UI Tree
    +
Render Orchestrator
    +
Render Primitives / Theme System
```

并在表达层采用链式组合 API（类似 GPUI 的开发体验），而不是再引入一层 XML 解析主链路。

---

## 为什么 BaseUIRender 应负责“Screen/帧桥接”而不是“组件”

当前代码里，组件已经知道如何画自己；但系统还缺一个“谁来把 Minecraft Screen 生命周期稳定地接到这棵 UI 树上”的角色。

这正是 `BaseUIRender` 最有价值的位置。

它适合集中承接这些能力：

- Screen 到 root 的渲染驱动
- 输入事件 forwarding
- resize / open / close 的生命周期协调
- 状态 reset/dispose 时机控制
- 少量跨组件共享的渲染工具边界
- 可选的调试/主题/原语扩展入口

这些能力如果都继续散落在组件里，会导致：

- 组件越来越重
- 相同逻辑重复出现
- 调试与主题无法统一
- 后续性能优化难以下手

同理，如果当前阶段把主要精力放到插件热更新实现，也会分散核心边界收敛，导致首版架构不稳。

---

## 为什么要保留主题与渲染原语接口（但不把它们放到第一优先级）

当前 `BaseUIButton`、`BaseUISlider` 已经出现这些趋势：

- 组件状态很多（hover / pressed / disabled / focused）
- 九宫格贴图已经进入控件绘制流程
- 文本颜色、fallback 绘制、音效反馈逐渐积累

如果不尽早在架构上留出主题/原语入口，后果会是：

- 每个控件都单独维护一套视觉逻辑
- 一旦要统一皮肤或风格，需要逐个组件回收逻辑
- 九宫格、文本、边框、阴影等绘制行为无法形成公共标准

因此文档层面必须先明确：

- 组件负责表达状态和意图
- `BaseUIRender` 负责为未来聚合通用视觉能力留边界
- 原语对象负责具体低层绘制单位

---

## 为什么调试接口要在最初就预留

UI 框架的问题往往不是“有没有功能”，而是“出了错能不能定位”。

当前代码已经包含大量高级行为：

- scissor stack
- absolute position
- focus / press target
- scroll culling
- nested render recursion

这些一旦出问题，没有可视化调试会非常痛苦。提前把调试能力纳入 `BaseUIRender` 的边界，是为了降低未来维护成本，而不是为了让设计看起来更完整。

---

## 为什么必须强调全局状态 reset

Oracle 的审查指出了一个当前代码中非常现实的边界风险：`BaseUIElement` 把 focus、press target、scissor stack 放在静态全局状态里。

这本身不是错误，但它意味着：

- 当前设计天然更适合“一次只有一个活跃 UI 根树”
- Screen 切换或关闭时必须定义清晰 reset 时机
- 否则很容易产生 zombie focus、残留按压目标、裁剪状态污染

因此，`BaseUIRender` 作为 Screen 桥接层，除了 render/input forwarding 之外，还应在文档上明确承担**状态安全收尾的入口责任**。

---

## 为什么不建议现在就把高级优化做成中心设计

外部资料确实显示 extraction/execution、批处理、缓存是合理方向，但当前仓库阶段更重要的是：

- 确认职责边界
- 统一文档语言
- 为未来扩展留口

如果现在把设计重心放在：

- render command buffer
- GPU batching
- multi-pass caching
- fine-grained dirty region

很容易把架构提前复杂化，反而妨碍组件体系继续稳定生长。

所以本次决策是：

- **架构上承认未来优化方向**
- **当前文档上不把它作为主轴**

这是一种“先把边界定对，再逐步增强”的策略。

---

## 为什么插件化热加载要后置到下一阶段

虽然插件化 Java 热更新是合理方向，但它涉及高风险基础设施：

- 类加载器隔离与依赖边界
- 生命周期钩子与注册表清理
- 交互状态迁移与回滚事务
- 内存与资源回收可验证性

这些问题都明显重于“当前是否能正确表达和渲染 UI”。

因此分阶段策略是：

1. 先稳定链式 UI 表达与 retained tree 映射
2. 再稳定 BaseUIRender 的桥接职责与状态收尾
3. 最后引入插件热更新运行时

这样既不否定长期目标，也能避免启动期被基础设施复杂度拖垮。

---

## 为什么当前把 DSL 限定在 layout/style

这是一次有意的边界收敛：

- layout/style 是“结构与视觉”问题，适合 DSL 稳定表达。
- event/业务动作是“行为与领域”问题，变化频率更高。

把两者直接绑死在 DSL 会导致：

- DSL 语义膨胀过快
- 业务改动频繁牵动 UI 描述层
- 框架早期难以保持清晰分层

因此当前阶段先让 DSL 专注结构与样式，是更稳的起步方式。

---

## 为什么事件层采用装饰器

装饰器/行为层的价值在于“后绑定”：

- UI 树先保持结构可读性
- 业务动作再按场景附着
- 替换逻辑时不必重写布局与样式定义

这与当前仓库既有模式并不冲突：`BaseUIElement` 继续负责输入分发骨架，装饰器负责把分发落到具体业务动作。

---

## 为什么选择增量 reconcile 而不是全量重建

在 retained tree 上，全量重建的代价不止是性能：

- 焦点、按压、滚动位置更容易抖动
- 布局重排与状态回填复杂度上升
- 交互连续性更差

增量 reconcile 的意义是：

- 尽可能复用已有节点
- 只更新变化分支
- 为后续 dirty-sign 与性能优化提供稳定地基

代价是必须定义好 identity 与 diff 规则，但这属于一次性边界成本，值得在早期承担。

---

## 为什么 dirty-sign 当前只做组件级

这是“正确性优先”的策略：

- 组件级 dirty 能快速验证刷新边界是否正确
- 不会引入 FBO/缓存生命周期管理复杂度
- 能先把渲染职责、事件语义、reconcile 路径跑通

等这些基础稳定后，再上缓存层，风险和返工都更低。

---

## 最终建议摘要

```text
BaseUIRender
  = Screen/Forge bridge + frame-level renderer boundary
  = root driving + lifecycle safety + optional extension entry
  ≠ component base class
  ≠ layout engine
  ≠ event tree owner
```

换句话说：

`BaseUIElement` 是“UI 树核心”，`BaseUIRender` 是“把 Minecraft Screen 稳定接到这棵树上，并安全地驱动它的人”。
