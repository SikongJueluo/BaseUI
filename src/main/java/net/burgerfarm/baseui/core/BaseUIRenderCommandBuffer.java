package net.burgerfarm.baseui.core;

import java.util.Arrays;

/**
 * 渲染命令缓冲区
 * <p>
 * 这是一个扁平化的批量渲染命令存储结构，用于收集一帧内所有UI元素的渲染指令。
 * 采用预分配数组而非动态集合，避免频繁GC；使用插入排序实现层级和顺序排序。
 * <p>
 * 设计特点：
 * <ul>
 *   <li>预分配固定容量数组，动态扩容</li>
 *   <li>分离存储每个属性，便于批量访问</li>
 *   <li>使用就地进行插入排序，避免额外内存</li>
 * </ul>
 */
final class BaseUIRenderCommandBuffer {
    // ==================== 存储数组 ====================
    // 所有数组初始容量为64，超出后自动扩容（2倍）

    /** UI元素数组 */
    private BaseUIElement<?>[] elements = new BaseUIElement[64];
    /** 全局Z值，用于深度排序 */
    private int[] globalZ = new int[64];
    /** 稳定访问顺序，用于同Z值时的二级排序 */
    private int[] stableOrder = new int[64];
    /** 绝对X坐标（屏幕坐标） */
    private int[] absX = new int[64];
    /** 绝对Y坐标（屏幕坐标） */
    private int[] absY = new int[64];
    /** 相对于元素自身的鼠标X坐标 */
    private int[] localMouseX = new int[64];
    /** 相对于元素自身的鼠标Y坐标 */
    private int[] localMouseY = new int[64];
    /** 最终透明度（已乘以父级透明度） */
    private float[] alpha = new float[64];

    /** 裁剪区域左边界 */
    private int[] clipL = new int[64];
    /** 裁剪区域上边界 */
    private int[] clipT = new int[64];
    /** 裁剪区域右边界 */
    private int[] clipR = new int[64];
    /** 裁剪区域下边界 */
    private int[] clipB = new int[64];
    /** 是否启用裁剪 */
    private boolean[] hasClip = new boolean[64];

    /** 渲染顺序数组，存储排序后的索引 */
    private int[] renderOrder = new int[64];

    // ==================== 状态变量 ====================

    /** 当前缓冲区中元素数量 */
    private int size = 0;
    /** 递增计数器，用于生成稳定顺序 */
    private int orderCounter = 0;

    // ==================== 公共方法 ====================

    /**
     * 开始收集渲染命令
     * <p>
     * 每帧渲染前调用，重置缓冲区和计数器。
     */
    void begin() {
        // 防止内存泄漏：清理废弃的元素引用
        for (int i = 0; i < size; i++) {
            elements[i] = null;
        }
        size = 0;
        orderCounter = 0;
    }

    /**
     * 添加渲染命令到缓冲区
     *
     * @param element     UI元素引用
     * @param z           全局Z值
     * @param x           绝对X坐标
     * @param y           绝对Y坐标
     * @param lmx         相对于元素的鼠标X坐标
     * @param lmy         相对于元素的鼠标Y坐标
     * @param finalAlpha  最终透明度
     * @param hasClipRect 是否启用裁剪
     * @param clipLVal    裁剪左边界
     * @param clipTVal    裁剪上边界
     * @param clipRVal    裁剪右边界
     * @param clipBVal    裁剪下边界
     * @return 添加元素的索引位置
     */
    int add(
        BaseUIElement<?> element,
        int z,
        int x,
        int y,
        int lmx,
        int lmy,
        float finalAlpha,
        boolean hasClipRect,
        int clipLVal,
        int clipTVal,
        int clipRVal,
        int clipBVal
    ) {
        // 容量不足时自动扩容
        ensureCapacity(size + 1);
        int index = size++;

        // 存储元素基本信息
        elements[index] = element;
        globalZ[index] = z;
        stableOrder[index] = orderCounter++;
        absX[index] = x;
        absY[index] = y;
        localMouseX[index] = lmx;
        localMouseY[index] = lmy;
        alpha[index] = finalAlpha;

        // 存储裁剪区域信息
        hasClip[index] = hasClipRect;
        clipL[index] = clipLVal;
        clipT[index] = clipTVal;
        clipR[index] = clipRVal;
        clipB[index] = clipBVal;

        // 初始渲染顺序为插入顺序
        renderOrder[index] = index;
        return index;
    }

    /**
     * 按层级和访问顺序排序
     * <p>
     * 使用插入排序算法：
     * <ul>
     *   <li>优先按Z值升序排序</li>
     *   <li>Z值相同时按稳定访问顺序排序</li>
     * </ul>
     * 选择插入排序的原因：数据基本有序时效率高，且原地排序无需额外内存。
     */
    void sortByLayerAndVisitOrder() {
        for (int i = 1; i < size; i++) {
            int idx = renderOrder[i];
            int j = i - 1;
            // 找到正确位置并移动元素
            while (j >= 0 && compare(renderOrder[j], idx) > 0) {
                renderOrder[j + 1] = renderOrder[j];
                j--;
            }
            renderOrder[j + 1] = idx;
        }
    }

    /**
     * 获取缓冲区中元素数量
     *
     * @return 元素数量
     */
    int size() {
        return size;
    }

    // ==================== 访问器方法 ====================
    // 以下方法用于从缓冲区中检索特定索引位置的数据

    /**
     * 获取排序后的原始索引
     *
     * @param orderedPosition 排序后的位置
     * @return 原始缓冲区索引
     */
    int orderedIndexAt(int orderedPosition) {
        return renderOrder[orderedPosition];
    }

    /**
     * 获取指定索引的UI元素
     */
    BaseUIElement<?> elementAt(int index) {
        return elements[index];
    }

    /**
     * 获取指定索引的全局Z值
     */
    int globalZAt(int index) {
        return globalZ[index];
    }

    /**
     * 获取指定索引的绝对X坐标
     */
    int absXAt(int index) {
        return absX[index];
    }

    /**
     * 获取指定索引的绝对Y坐标
     */
    int absYAt(int index) {
        return absY[index];
    }

    /**
     * 获取指定索引的相对于元素的鼠标X坐标
     */
    int localMouseXAt(int index) {
        return localMouseX[index];
    }

    /**
     * 获取指定索引的相对于元素的鼠标Y坐标
     */
    int localMouseYAt(int index) {
        return localMouseY[index];
    }

    /**
     * 获取指定索引的最终透明度
     */
    float alphaAt(int index) {
        return alpha[index];
    }

    /**
     * 判断指定索引是否启用裁剪
     */
    boolean hasClipAt(int index) {
        return hasClip[index];
    }

    /**
     * 获取指定索引的裁剪左边界
     */
    int clipLAt(int index) {
        return clipL[index];
    }

    /**
     * 获取指定索引的裁剪上边界
     */
    int clipTAt(int index) {
        return clipT[index];
    }

    /**
     * 获取指定索引的裁剪右边界
     */
    int clipRAt(int index) {
        return clipR[index];
    }

    /**
     * 获取指定索引的裁剪下边界
     */
    int clipBAt(int index) {
        return clipB[index];
    }

    // ==================== 私有方法 ====================

    /**
     * 比较两个渲染命令的优先级
     * <p>
     * 排序规则：
     * <ol>
     *   <li>Z值小的排在前面（层级低）</li>
     *   <li>Z值相同时，先访问的排在前面</li>
     * </ol>
     *
     * @param left  左侧命令索引
     * @param right 右侧命令索引
     * @return 负数表示left优先于right，正数表示right优先于left
     */
    private int compare(int left, int right) {
        int zCmp = Integer.compare(globalZ[left], globalZ[right]);
        // Z值不同时直接返回Z比较结果
        if (zCmp != 0) {
            return zCmp;
        }
        // Z值相同时按稳定顺序排序
        return Integer.compare(stableOrder[left], stableOrder[right]);
    }

    /**
     * 确保容量足够容纳新元素
     * <p>
     * 扩容策略：取所需容量和当前容量2倍的最大值，避免频繁扩容。
     * 使用 Arrays.copyOf 进行数组复制。
     *
     * @param needed 所需最小容量
     */
    private void ensureCapacity(int needed) {
        // 容量足够时直接返回
        if (needed <= elements.length) {
            return;
        }
        // 计算新容量：至少翻倍
        int newCap = Math.max(needed, elements.length * 2);
        // 复制所有数组到新容量
        elements = Arrays.copyOf(elements, newCap);
        globalZ = Arrays.copyOf(globalZ, newCap);
        stableOrder = Arrays.copyOf(stableOrder, newCap);
        absX = Arrays.copyOf(absX, newCap);
        absY = Arrays.copyOf(absY, newCap);
        localMouseX = Arrays.copyOf(localMouseX, newCap);
        localMouseY = Arrays.copyOf(localMouseY, newCap);
        alpha = Arrays.copyOf(alpha, newCap);
        clipL = Arrays.copyOf(clipL, newCap);
        clipT = Arrays.copyOf(clipT, newCap);
        clipR = Arrays.copyOf(clipR, newCap);
        clipB = Arrays.copyOf(clipB, newCap);
        hasClip = Arrays.copyOf(hasClip, newCap);
        renderOrder = Arrays.copyOf(renderOrder, newCap);
    }
}
