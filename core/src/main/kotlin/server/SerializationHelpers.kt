package edu.illinois.cs.cs125.jeed.core.server

import edu.illinois.cs.cs125.jeed.core.CompilationMessage
import edu.illinois.cs.cs125.jeed.core.CompiledSource
import edu.illinois.cs.cs125.jeed.core.ExecutionFailed
import edu.illinois.cs.cs125.jeed.core.Interval
import edu.illinois.cs.cs125.jeed.core.Sandbox
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.SourceExecutionArguments
import edu.illinois.cs.cs125.jeed.core.TemplatedSource
import edu.illinois.cs.cs125.jeed.core.getStackTraceForSource
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class ExecutionFailedResult(
    val classNotFound: String?,
    val methodNotFound: String?,
) {
    constructor(executionFailed: ExecutionFailed) : this(
        if (executionFailed.classNotFound != null) {
            executionFailed.classNotFound.klass
        } else {
            null
        },
        if (executionFailed.methodNotFound != null) {
            executionFailed.methodNotFound.method
        } else {
            null
        },
    )
}

@Serializable
data class CompiledSourceResult(
    val messages: List<CompilationMessage>,
    @Contextual val compiled: Instant,
    val interval: Interval,
    val compilerName: String,
    val cached: Boolean,
) {
    constructor(compiledSource: CompiledSource) : this(
        compiledSource.messages,
        compiledSource.compiled,
        compiledSource.interval,
        compiledSource.compilerName,
        compiledSource.cached,
    )
}

@Serializable
data class TemplatedSourceResult(
    val sources: Map<String, String>,
    val originalSources: Map<String, String>,
) {
    constructor(templatedSource: TemplatedSource) : this(templatedSource.sources, templatedSource.originalSources)
}

@Serializable
data class ThrownException(
    val klass: String,
    val message: String?,
    val stacktrace: String,
) {
    constructor(throwable: Throwable, source: Source) : this(
        throwable::class.java.typeName,
        throwable.message,
        throwable.getStackTraceForSource(source),
    )
}

@Serializable
data class SourceTaskResults(
    val klass: String,
    val method: String,
    val returned: String?,
    val threw: ThrownException?,
    val timeout: Boolean,
    val killReason: String?,
    val outputLines: List<Sandbox.TaskResults.OutputLine> = listOf(),
    val permissionRequests: List<Sandbox.TaskResults.PermissionRequest> = listOf(),
    val interval: Interval,
    val executionInterval: Interval,
    val truncatedLines: Int,
) {
    constructor(
        source: Source,
        taskResults: Sandbox.TaskResults<*>,
        sourceExecutionArguments: SourceExecutionArguments,
    ) : this(
        sourceExecutionArguments.klass ?: error("should have a klass name"),
        sourceExecutionArguments.method ?: error("Should have a method name"),
        taskResults.returned.toString(),
        if (taskResults.threw != null) {
            ThrownException(taskResults.threw, source)
        } else {
            null
        },
        taskResults.timeout,
        taskResults.killReason,
        taskResults.outputLines.toList(),
        taskResults.permissionRequests.toList(),
        taskResults.interval,
        taskResults.executionInterval,
        taskResults.truncatedLines,
    )
}
