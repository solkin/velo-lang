package vm

/**
 * VM Profiler for performance measurement and memory tracking.
 * 
 * Enable via system property: -Dvelo.profile=true
 * Or programmatically by passing enabled=true
 */
class VMProfiler(
    val enabled: Boolean = System.getProperty("velo.profile")?.toBoolean() ?: false
) {
    
    // Timing
    private var startTimeNs: Long = 0
    private var endTimeNs: Long = 0
    
    // Operation statistics
    private val opExecutionCount = HashMap<String, Long>()
    private val opExecutionTimeNs = HashMap<String, Long>()
    private var currentOpStartNs: Long = 0
    private var currentOpName: String? = null
    
    // Memory tracking
    private var initialJvmMemory: Long = 0
    private var peakJvmMemory: Long = 0
    
    // Heap tracking (set by VM)
    var heapStats: HeapStats? = null
    
    /**
     * Start profiling session
     */
    fun start() {
        if (!enabled) return
        
        startTimeNs = System.nanoTime()
        initialJvmMemory = getUsedJvmMemory()
        peakJvmMemory = initialJvmMemory
    }
    
    /**
     * End profiling session
     */
    fun stop() {
        if (!enabled) return
        
        endTimeNs = System.nanoTime()
    }
    
    /**
     * Mark operation start (call before exec)
     */
    fun beforeOp(op: Operation) {
        if (!enabled) return
        
        currentOpName = op.javaClass.simpleName
        currentOpStartNs = System.nanoTime()
    }
    
    /**
     * Mark operation end (call after exec)
     */
    fun afterOp() {
        if (!enabled) return
        
        val name = currentOpName ?: return
        val elapsedNs = System.nanoTime() - currentOpStartNs
        
        opExecutionCount[name] = (opExecutionCount[name] ?: 0) + 1
        opExecutionTimeNs[name] = (opExecutionTimeNs[name] ?: 0) + elapsedNs
        
        // Sample peak memory periodically (every 10000 ops to reduce overhead)
        val totalOps = opExecutionCount.values.sum()
        if (totalOps % 10000 == 0L) {
            val currentMemory = getUsedJvmMemory()
            if (currentMemory > peakJvmMemory) {
                peakJvmMemory = currentMemory
            }
        }
        
        currentOpName = null
    }
    
    /**
     * Print profiling report
     */
    fun printReport() {
        if (!enabled) return
        
        println()
        println("═══════════════════════════════════════════════════════════════")
        println("                      VM PROFILER REPORT")
        println("═══════════════════════════════════════════════════════════════")
        
        // Timing summary
        val totalTimeMs = (endTimeNs - startTimeNs) / 1_000_000.0
        val totalOps = opExecutionCount.values.sum()
        println()
        println("EXECUTION TIME")
        println("───────────────────────────────────────────────────────────────")
        println("  Total time:        %.3f ms".format(totalTimeMs))
        println("  Total operations:  $totalOps")
        if (totalOps > 0) {
            println("  Avg time/op:       %.3f µs".format(totalTimeMs * 1000 / totalOps))
            println("  Ops/second:        %.0f".format(totalOps / (totalTimeMs / 1000)))
        }
        
        // Memory summary
        println()
        println("JVM MEMORY")
        println("───────────────────────────────────────────────────────────────")
        val finalMemory = getUsedJvmMemory()
        println("  Initial:           ${formatBytes(initialJvmMemory)}")
        println("  Peak:              ${formatBytes(peakJvmMemory)}")
        println("  Final:             ${formatBytes(finalMemory)}")
        println("  Delta:             ${formatBytes(finalMemory - initialJvmMemory)}")
        
        // Heap summary
        heapStats?.let { stats ->
            println()
            println("VELO HEAP")
            println("───────────────────────────────────────────────────────────────")
            println("  Allocations:       ${stats.allocations}")
            println("  Deallocations:     ${stats.deallocations}")
            println("  Active objects:    ${stats.activeCount}")
            println("  Peak objects:      ${stats.peakCount}")
            if (stats.activeCount > 0) {
                println("  ⚠ POTENTIAL LEAK:  ${stats.activeCount} objects still in heap")
            }
        }
        
        // Top operations by time
        if (opExecutionTimeNs.isNotEmpty()) {
            println()
            println("TOP OPERATIONS BY TIME")
            println("───────────────────────────────────────────────────────────────")
            val sortedByTime = opExecutionTimeNs.entries
                .sortedByDescending { it.value }
                .take(15)
            
            println("  %-25s %12s %12s %12s".format("Operation", "Count", "Total(ms)", "Avg(µs)"))
            println("  " + "─".repeat(61))
            for ((name, timeNs) in sortedByTime) {
                val count = opExecutionCount[name] ?: 0
                val totalMs = timeNs / 1_000_000.0
                val avgUs = if (count > 0) timeNs / 1000.0 / count else 0.0
                println("  %-25s %12d %12.3f %12.3f".format(name, count, totalMs, avgUs))
            }
        }
        
        // Top operations by count
        if (opExecutionCount.isNotEmpty()) {
            println()
            println("TOP OPERATIONS BY COUNT")
            println("───────────────────────────────────────────────────────────────")
            val sortedByCount = opExecutionCount.entries
                .sortedByDescending { it.value }
                .take(10)
            
            println("  %-25s %12s %8s".format("Operation", "Count", "% Total"))
            println("  " + "─".repeat(45))
            for ((name, count) in sortedByCount) {
                val percent = if (totalOps > 0) count * 100.0 / totalOps else 0.0
                println("  %-25s %12d %7.2f%%".format(name, count, percent))
            }
        }
        
        println()
        println("═══════════════════════════════════════════════════════════════")
    }
    
    private fun getUsedJvmMemory(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }
    
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.2f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }
}

/**
 * Statistics from Heap for profiler
 */
data class HeapStats(
    val allocations: Long,
    val deallocations: Long,
    val activeCount: Long,
    val peakCount: Long
)

