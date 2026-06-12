package dev.barna.calm

import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

class WidgetHostIpcRunner(
    private val workerExecutor: ExecutorService = Executors.newSingleThreadExecutor { command ->
        Thread(command, "CalmWidgetHostIpc").apply { isDaemon = true }
    },
    private val callbackExecutor: Executor,
    private val logFailure: (String, Throwable) -> Unit,
) {
    fun run(message: String, action: () -> Unit) {
        try {
            workerExecutor.execute {
                runCatching(action)
                    .onFailure { logFailure(message, it) }
            }
        } catch (rejected: RejectedExecutionException) {
            logFailure(message, rejected)
        }
    }

    fun <T> call(message: String, defaultValue: T, action: () -> T, callback: (T) -> Unit) {
        try {
            workerExecutor.execute {
                val result = runCatching(action)
                    .onFailure { logFailure(message, it) }
                    .getOrDefault(defaultValue)
                callbackExecutor.execute { callback(result) }
            }
        } catch (rejected: RejectedExecutionException) {
            logFailure(message, rejected)
            callbackExecutor.execute { callback(defaultValue) }
        }
    }

    fun shutdown() {
        workerExecutor.shutdownNow()
    }
}
