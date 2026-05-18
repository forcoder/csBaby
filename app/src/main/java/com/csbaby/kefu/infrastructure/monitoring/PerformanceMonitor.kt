package com.csbaby.kefu.infrastructure.monitoring

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用性能监控器
 * 负责监控应用性能指标和崩溃报告
 */
@Singleton
class PerformanceMonitor @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "PerformanceMonitor"
        private const val SAMPLE_INTERVAL_MS = 5000L
    }

    // 性能指标
    private val performanceMetrics = ConcurrentHashMap<String, PerformanceMetric>()
    private val crashReports = CopyOnWriteArrayList<CrashReport>()
    private val startupTime = SystemClock.elapsedRealtime()

    // 监控状态
    private val isMonitoring = AtomicBoolean(false)
    private val monitorJob = SupervisorJob()
    private val monitorScope = CoroutineScope(monitorJob + Dispatchers.IO)

    /**
     * 开始监控
     */
    fun startMonitoring() {
        if (!isMonitoring.compareAndSet(false, true)) return

        Timber.d("开始性能监控")

        monitorScope.launch {
            while (isMonitoring.get()) {
                collectPerformanceMetrics()
                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }

    /**
     * 停止监控
     */
    fun stopMonitoring() {
        isMonitoring.set(false)
        monitorJob.cancel()
        Timber.d("停止性能监控")
    }
    
    /**
     * 记录方法执行时间
     */
    fun <T> measureExecutionTime(tag: String, block: () -> T): T {
        val startTime = SystemClock.elapsedRealtime()
        val result = block()
        val endTime = SystemClock.elapsedRealtime()
        val executionTime = endTime - startTime
        
        val metric = performanceMetrics.getOrPut(tag) { PerformanceMetric(tag) }
        metric.recordExecutionTime(executionTime)
        
        if (executionTime > 1000) {
            Timber.w("方法 $tag 执行时间较长: ${executionTime}ms")
        }
        
        return result
    }
    
    /**
     * 记录崩溃
     */
    fun recordCrash(throwable: Throwable, context: String) {
        val crashReport = CrashReport(
            timestamp = System.currentTimeMillis(),
            exceptionType = throwable.javaClass.simpleName,
            message = throwable.message ?: "No message",
            stackTrace = throwable.stackTraceToString(),
            context = context
        )

        crashReports.add(crashReport)
        Timber.e(throwable, "应用崩溃: $context")
    }
    
    /**
     * 收集性能指标
     */
    private fun collectPerformanceMetrics() {
        try {
            // 收集内存使用情况
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            
            val memoryMetric = performanceMetrics.getOrPut("memory") { PerformanceMetric("memory") }
            memoryMetric.recordValue((usedMemory / (1024 * 1024)).toLong())
            
            // 收集应用启动时间
            val uptime = SystemClock.elapsedRealtime() - startupTime
            val uptimeMetric = performanceMetrics.getOrPut("uptime") { PerformanceMetric("uptime") }
            uptimeMetric.recordValue(uptime / 1000)
            
        } catch (e: Exception) {
            Timber.e(e, "收集性能指标失败")
        }
    }
    
    /**
     * 获取性能报告
     */
    fun getPerformanceReport(): PerformanceReport {
        val metrics = performanceMetrics.values.toList()
        val report = PerformanceReport(
            timestamp = System.currentTimeMillis(),
            uptime = SystemClock.elapsedRealtime() - startupTime,
            metrics = metrics,
            crashCount = crashReports.size
        )
        
        Timber.d("生成性能报告: $report")
        return report
    }
    
    /**
     * 清除性能数据
     */
    fun clearMetrics() {
        performanceMetrics.clear()
        crashReports.clear()
    }

    /**
     * 关闭性能监控器，取消所有后台协程。
     * 应在应用退出时调用。
     */
    fun shutdown() {
        stopMonitoring()
    }
    
    /**
     * 性能指标
     */
    data class PerformanceMetric(
        val name: String,
        private val values: MutableList<Long> = mutableListOf(),
        private val totalTime: AtomicLong = AtomicLong(0),
        private val callCount: AtomicLong = AtomicLong(0)
    ) {
        fun recordExecutionTime(time: Long) {
            values.add(time)
            totalTime.addAndGet(time)
            callCount.incrementAndGet()
        }
        
        fun recordValue(value: Long) {
            values.add(value)
        }
        
        fun getAverage(): Double {
            if (values.isEmpty()) return 0.0
            return values.average()
        }
        
        fun getMax(): Long {
            return values.maxOrNull() ?: 0
        }
        
        fun getMin(): Long {
            return values.minOrNull() ?: 0
        }
        
        fun getCount(): Long {
            return callCount.get()
        }
        
        fun getValues(): List<Long> {
            return values.toList()
        }
    }
    
    /**
     * 崩溃报告
     */
    data class CrashReport(
        val timestamp: Long,
        val exceptionType: String,
        val message: String,
        val stackTrace: String,
        val context: String
    )
    
    /**
     * 性能报告
     */
    data class PerformanceReport(
        val timestamp: Long,
        val uptime: Long,
        val metrics: List<PerformanceMetric>,
        val crashCount: Int
    )
}