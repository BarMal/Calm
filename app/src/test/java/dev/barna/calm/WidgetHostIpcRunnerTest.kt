package dev.barna.calm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class WidgetHostIpcRunnerTest {
    @Test
    fun runDefersActionToWorkerExecutor() {
        val worker = QueuedExecutorService()
        var ran = false

        WidgetHostIpcRunner(
            workerExecutor = worker,
            callbackExecutor = DirectExecutor,
            logFailure = { _, _ -> },
        ).run("start failed") {
            ran = true
        }

        assertFalse(ran)
        worker.runNext()

        assertTrue(ran)
    }

    @Test
    fun callReturnsResultThroughCallbackExecutor() {
        val worker = QueuedExecutorService()
        val callbacks = QueuedExecutor()
        val results = mutableListOf<Boolean>()

        WidgetHostIpcRunner(
            workerExecutor = worker,
            callbackExecutor = callbacks,
            logFailure = { _, _ -> },
        ).call("bind failed", defaultValue = false, action = { true }) {
            results.add(it)
        }

        worker.runNext()
        assertTrue(results.isEmpty())

        callbacks.runNext()
        assertEquals(listOf(true), results)
    }

    @Test
    fun callLogsFailureAndReturnsDefaultValue() {
        val worker = QueuedExecutorService()
        val callbacks = QueuedExecutor()
        val failures = mutableListOf<String>()
        val results = mutableListOf<Boolean>()

        WidgetHostIpcRunner(
            workerExecutor = worker,
            callbackExecutor = callbacks,
            logFailure = { message, _ -> failures.add(message) },
        ).call("bind failed", defaultValue = false, action = { throw RuntimeException("boom") }) {
            results.add(it)
        }

        worker.runNext()
        callbacks.runNext()

        assertEquals(listOf("bind failed"), failures)
        assertEquals(listOf(false), results)
    }

    @Test
    fun rejectedWorkerCallLogsAndReturnsDefaultValue() {
        val callbacks = QueuedExecutor()
        val failures = mutableListOf<String>()
        val results = mutableListOf<Boolean>()

        WidgetHostIpcRunner(
            workerExecutor = RejectingExecutorService(),
            callbackExecutor = callbacks,
            logFailure = { message, _ -> failures.add(message) },
        ).call("bind failed", defaultValue = false, action = { true }) {
            results.add(it)
        }

        callbacks.runNext()

        assertEquals(listOf("bind failed"), failures)
        assertEquals(listOf(false), results)
    }

    private object DirectExecutor : Executor {
        override fun execute(command: Runnable) = command.run()
    }

    private class QueuedExecutor : Executor {
        private val queue = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            queue.addLast(command)
        }

        fun runNext() {
            queue.removeFirst().run()
        }
    }

    private class QueuedExecutorService : AbstractExecutorService() {
        private val queue = ArrayDeque<Runnable>()
        private var shutdown = false

        override fun execute(command: Runnable) {
            if (shutdown) throw RejectedExecutionException("shut down")
            queue.addLast(command)
        }

        fun runNext() {
            queue.removeFirst().run()
        }

        override fun shutdown() {
            shutdown = true
        }

        override fun shutdownNow(): List<Runnable> {
            shutdown = true
            return emptyList()
        }

        override fun isShutdown() = shutdown
        override fun isTerminated() = shutdown
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }

    private class RejectingExecutorService : AbstractExecutorService() {
        override fun execute(command: Runnable) {
            throw RejectedExecutionException("shut down")
        }

        override fun shutdown() = Unit
        override fun shutdownNow(): List<Runnable> = emptyList()
        override fun isShutdown() = true
        override fun isTerminated() = true
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
}
