package dev.ota.blocker

import android.util.Log

internal object Diagnostics {
    const val TAG = "HyperOtaBlocker"
    private val events = ArrayDeque<String>()

    @Synchronized
    fun record(message: String) {
        Log.i(TAG, message)
        if (events.size == 100) events.removeFirst()
        events.addLast("${java.time.Instant.now()}  $message")
    }

    @Synchronized
    fun snapshot(): String = events.joinToString("\n").ifBlank { "当前设置进程暂无事件。" }
}

internal inline fun <T> protect(label: String, fallback: T, block: () -> T): T {
    return try {
        block()
    } catch (error: Exception) {
        Diagnostics.record("$label · ${error.javaClass.simpleName}")
        fallback
    } catch (error: LinkageError) {
        Diagnostics.record("$label · ${error.javaClass.simpleName}")
        fallback
    }
}
