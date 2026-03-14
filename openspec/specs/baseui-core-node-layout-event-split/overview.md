# BaseUI 核心节点布局事件分离

## 概述

本规范定义了 BaseUI 核心的重构，将节点职责从布局和事件处理中分离出来。

## 目标

- 将 BaseUIElement 从布局和事件分发的职责中分离
- 引入 UILayoutEngine 实现统一的布局处理
- 引入 UIEventDispatcher 实现事件路由
- 保持用户交互的向后兼容性
- 确保 Vulkan-safe 的渲染抽象

## 状态

这是来自归档变更 `refactor-core-ui-node-layout-event-split` 的新增规范。
