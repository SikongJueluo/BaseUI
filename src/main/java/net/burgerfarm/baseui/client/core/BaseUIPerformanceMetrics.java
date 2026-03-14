package net.burgerfarm.baseui.client.core;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Performance metrics collector for BaseUI rendering pipeline.
 * Tracks render command counts, allocation hotspots, and exception/aborted frames.
 * <p>
 * Provides detailed observability for performance debugging and optimization.
 * Thread-safe for single-threaded rendering loop usage.
 */
public class BaseUIPerformanceMetrics {
    private static final Logger LOGGER = LogUtils.getLogger();

    // ==========================================
    // Per-Frame Counters
    // ==========================================
    private long totalRenderCommands = 0;
    private long frameCount = 0;
    private long exceptionFrameCount = 0;
    private long abortedFrameCount = 0;

    // ==========================================
    // Allocation Tracking (Hotspot Data)
    // ==========================================
    private long totalAllocationEvents = 0;
    private long peakFrameAllocationCount = 0;
    private long lastFrameAllocationCount = 0;

    // ==========================================
    // State & Configuration
    // ==========================================
    private boolean debugMode = false;
    private long lastResetTime = System.nanoTime();

    public BaseUIPerformanceMetrics() {
    }

    /**
     * Enable debug mode for verbose logging of metrics.
     */
    public void setDebugMode(boolean enabled) {
        this.debugMode = enabled;
    }

    /**
     * Record render command collection for current frame.
     * Called after each render command buffer is populated.
     *
     * @param commandCount Number of render commands collected in this frame
     */
    public synchronized void recordRenderCommands(int commandCount) {
        totalRenderCommands += commandCount;
        frameCount++;
        
        if (debugMode) {
            LOGGER.debug("Frame #{}: {} render commands collected", frameCount, commandCount);
        }
    }

    /**
     * Record an exception/error during frame rendering.
     * Call this when renderFrame() catches an exception.
     *
     * @param stage     Name of the rendering stage (e.g., "preflight", "render", "finalize")
     * @param exception The exception that was caught (nullable for logging)
     */
    public synchronized void recordFrameException(String stage, Exception exception) {
        exceptionFrameCount++;
        frameCount++;
        
        if (exception != null) {
            LOGGER.warn("Frame exception at stage '{}': {}", stage, exception.getMessage());
        } else {
            LOGGER.warn("Frame exception at stage '{}'", stage);
        }
        
        if (debugMode) {
            LOGGER.debug("Exception frame count: {}", exceptionFrameCount);
        }
    }

    /**
     * Record an aborted frame (early exit without full rendering).
     * Call when preflight checks fail or other abort conditions occur.
     *
     * @param reason Human-readable reason for abort
     */
    public synchronized void recordFrameAbort(String reason) {
        abortedFrameCount++;
        frameCount++;
        
        if (debugMode) {
            LOGGER.debug("Frame aborted: {} (total aborts: {})", reason, abortedFrameCount);
        }
    }

    /**
     * Record allocation hotspot activity for current frame.
     * Useful for tracking per-frame allocation patterns.
     *
     * @param allocationCount Estimated number of allocations in this frame
     */
    public synchronized void recordFrameAllocations(long allocationCount) {
        lastFrameAllocationCount = allocationCount;
        totalAllocationEvents += allocationCount;
        
        if (allocationCount > peakFrameAllocationCount) {
            peakFrameAllocationCount = allocationCount;
            if (debugMode) {
                LOGGER.debug("New peak allocation count: {}", peakFrameAllocationCount);
            }
        }
    }

    /**
     * Reset all metrics to zero.
     */
    public synchronized void reset() {
        totalRenderCommands = 0;
        frameCount = 0;
        exceptionFrameCount = 0;
        abortedFrameCount = 0;
        totalAllocationEvents = 0;
        peakFrameAllocationCount = 0;
        lastFrameAllocationCount = 0;
        lastResetTime = System.nanoTime();
    }

    // ==========================================
    // Getters for Metrics
    // ==========================================

    public synchronized long getTotalRenderCommands() {
        return totalRenderCommands;
    }

    public synchronized long getFrameCount() {
        return frameCount;
    }

    public synchronized long getExceptionFrameCount() {
        return exceptionFrameCount;
    }

    public synchronized long getAbortedFrameCount() {
        return abortedFrameCount;
    }

    public synchronized long getTotalAllocationEvents() {
        return totalAllocationEvents;
    }

    public synchronized long getPeakFrameAllocationCount() {
        return peakFrameAllocationCount;
    }

    public synchronized long getLastFrameAllocationCount() {
        return lastFrameAllocationCount;
    }

    /**
     * Get average render commands per frame.
     *
     * @return Average command count, or 0 if no frames recorded
     */
    public synchronized double getAverageRenderCommandsPerFrame() {
        if (frameCount == 0) return 0.0;
        return (double) totalRenderCommands / frameCount;
    }

    /**
     * Get exception frame rate as percentage.
     *
     * @return Percentage of frames with exceptions (0-100), or 0 if no frames
     */
    public synchronized double getExceptionFrameRate() {
        if (frameCount == 0) return 0.0;
        return (double) (exceptionFrameCount * 100) / frameCount;
    }

    /**
     * Get aborted frame rate as percentage.
     *
     * @return Percentage of frames aborted, or 0 if no frames
     */
    public synchronized double getAbortedFrameRate() {
        if (frameCount == 0) return 0.0;
        return (double) (abortedFrameCount * 100) / frameCount;
    }

    /**
     * Get time elapsed since last reset in milliseconds.
     */
    public synchronized long getTimeSinceResetMs() {
        return (System.nanoTime() - lastResetTime) / 1_000_000;
    }

    /**
     * Print a comprehensive metrics summary to the logger.
     * Useful for debugging performance issues.
     */
    public synchronized void printMetricsSummary() {
        double avgCmds = getAverageRenderCommandsPerFrame();
        double exceptionRate = getExceptionFrameRate();
        double abortRate = getAbortedFrameRate();
        long elapsedMs = getTimeSinceResetMs();

        LOGGER.info("=== BaseUI Performance Metrics Summary ===");
        LOGGER.info("  Total Frames: {}", frameCount);
        LOGGER.info("  Elapsed Time: {} ms", elapsedMs);
        LOGGER.info("  Total Render Commands: {}", totalRenderCommands);
        LOGGER.info("  Avg Commands/Frame: {}", String.format("%.2f", avgCmds));
        LOGGER.info("  Exception Frames: {} ({:.2f}%)", exceptionFrameCount, exceptionRate);
        LOGGER.info("  Aborted Frames: {} ({:.2f}%)", abortedFrameCount, abortRate);
        LOGGER.info("  Total Allocation Events: {}", totalAllocationEvents);
        LOGGER.info("  Peak Frame Allocations: {}", peakFrameAllocationCount);
        LOGGER.info("  Last Frame Allocations: {}", lastFrameAllocationCount);
        LOGGER.info("==========================================");
    }

    @Override
    public String toString() {
        double avgCmds = getAverageRenderCommandsPerFrame();
        double exceptionRate = getExceptionFrameRate();
        double abortRate = getAbortedFrameRate();
        return String.format(
            "BaseUIPerformanceMetrics{frames=%d, totalCmds=%d, avgCmds=%.2f, exceptions=%d (%.2f%%), aborts=%d (%.2f%%), allocations=%d}",
            frameCount, totalRenderCommands, avgCmds, exceptionFrameCount, exceptionRate, abortedFrameCount, abortRate, totalAllocationEvents
        );
    }
}
