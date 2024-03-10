package edu.illinois.cs.cs125.jeed.core

import kotlin.reflect.KCallable
import kotlin.reflect.KProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

private const val DEFAULT_EXECUTOR_NAME = "kotlinx.coroutines.DefaultExecutor"

private const val COROUTINE_SCHEDULER_NAME = "kotlinx.coroutines.scheduling.CoroutineScheduler"
private const val COROUTINE_SCHEDULER_WORKER_NAME = "kotlinx.coroutines.scheduling.CoroutineScheduler\$Worker"
private const val LOCK_FREE_TASK_QUEUE_NAME = "kotlinx.coroutines.internal.LockFreeTaskQueue"
private const val WORK_QUEUE_NAME = "kotlinx.coroutines.scheduling.WorkQueue"

private fun usesCoroutines(classLoader: Sandbox.SandboxedClassLoader): Boolean {
    return classLoader.loadedClasses.any { it.startsWith("kotlinx.coroutines.") }
}

internal fun coroutinesActive(classLoader: Sandbox.SandboxedClassLoader, threads: List<Thread>): Boolean {
    return try {
        coroutinesActiveInternal(classLoader, threads)
    } catch (_: Throwable) {
        false
    }
}

private fun coroutinesActiveInternal(classLoader: Sandbox.SandboxedClassLoader, threads: List<Thread>): Boolean {
    return defaultExecutorActive(classLoader) || workerQueuesActive(classLoader, threads)
}

private fun defaultExecutorActive(classLoader: Sandbox.SandboxedClassLoader): Boolean {
    val defaultExecutorClass = classLoader.reloadedClasses[DEFAULT_EXECUTOR_NAME] ?: return false
    val isEmptyPropGetter = defaultExecutorClass.kotlin.memberProperties
        .find { it.name == "isEmpty" }
        ?.also { it.isAccessible = true }
        ?.getter as? KCallable<*> ?: return false
    return Sandbox.allowingUnconstrainedInvocation {
        val defaultExecutor = defaultExecutorClass.kotlin.objectInstance
        isEmptyPropGetter.call(defaultExecutor) == false
    }
}

private fun workerQueuesActive(classLoader: Sandbox.SandboxedClassLoader, threads: List<Thread>): Boolean {
    val schedulerWorkerClass = classLoader.reloadedClasses[COROUTINE_SCHEDULER_WORKER_NAME] ?: return false
    val workerThreads = threads.filter { it.javaClass === schedulerWorkerClass }
    if (workerThreads.isEmpty()) return false

    val localQueueField = schedulerWorkerClass.getDeclaredField("localQueue").also { it.isAccessible = true }
    val workQueueClass = classLoader.reloadedClasses[WORK_QUEUE_NAME] ?: return false
    val queueSizeProp = workQueueClass.kotlin.memberProperties
        .find { it.name == "size" }
        ?.also { it.isAccessible = true } as? KProperty<*> ?: return false
    val localQueuesActive = Sandbox.allowingUnconstrainedInvocation {
        workerThreads.any {
            val localQueue = localQueueField.get(it)
            queueSizeProp.getter.call(localQueue) != 0
        }
    }
    if (localQueuesActive) return true

    val schedulerField = schedulerWorkerClass.getDeclaredField("this\$0").also { it.isAccessible = true }
    val scheduler = schedulerField.get(workerThreads[0]) ?: return false
    val schedulerClass = classLoader.reloadedClasses[COROUTINE_SCHEDULER_NAME] ?: return false
    val lockFreeQueueClass = classLoader.reloadedClasses[LOCK_FREE_TASK_QUEUE_NAME] ?: return false
    val lockFreeQueueEmptyProp = lockFreeQueueClass.kotlin.memberProperties
        .find { it.name == "isEmpty" }
        ?.also { it.isAccessible = true } as? KProperty<*> ?: return false
    fun schedulerQueueActive(fieldName: String): Boolean {
        val field = schedulerClass.getField(fieldName).also { it.isAccessible = true }
        val lockFreeQueue = field.get(scheduler)
        return lockFreeQueueEmptyProp.getter.call(lockFreeQueue) == false
    }
    return Sandbox.allowingUnconstrainedInvocation {
        schedulerQueueActive("globalCpuQueue") || schedulerQueueActive("globalBlockingQueue")
    }
}
