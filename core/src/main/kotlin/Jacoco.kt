package edu.illinois.cs.cs125.jeed.core

import com.squareup.moshi.JsonClass
import org.jacoco.core.analysis.Analyzer
import org.jacoco.core.analysis.CoverageBuilder
import org.jacoco.core.analysis.IClassCoverage
import org.jacoco.core.analysis.ICounter
import org.jacoco.core.analysis.ILine
import org.jacoco.core.data.ExecutionDataStore
import org.jacoco.core.data.SessionInfoStore
import org.jacoco.core.instr.Instrumenter
import org.jacoco.core.runtime.IRuntime
import org.jacoco.core.runtime.RuntimeData
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

object Jacoco : SandboxPlugin<Unit, CoverageBuilder> {
    private val instrumenter = Instrumenter(IsolatedJacocoRuntime)

    override fun createInstrumentationData(
        arguments: Unit,
        classLoaderConfiguration: Sandbox.ClassLoaderConfiguration,
        allPlugins: List<ConfiguredSandboxPlugin<*, *>>,
    ): Any {
        return JacocoInstrumentationData()
    }

    override fun transformBeforeSandbox(
        bytecode: ByteArray,
        name: String,
        instrumentationData: Any?,
        context: RewritingContext,
    ): ByteArray {
        if (context != RewritingContext.UNTRUSTED) return bytecode
        instrumentationData as JacocoInstrumentationData
        instrumentationData.coverageClasses[name] = bytecode
        return instrumenter.instrument(bytecode, name)
    }

    override val requiredClasses: Set<Class<*>>
        get() = setOf(IsolatedJacocoRuntime.RuntimeDataAccessor::class.java)

    override fun createInitialData(instrumentationData: Any?, executionArguments: Sandbox.ExecutionArguments): Any {
        return JacocoWorkingData(instrumentationData as JacocoInstrumentationData)
    }

    override fun createFinalData(workingData: Any?): CoverageBuilder {
        workingData as JacocoWorkingData
        val executionData = ExecutionDataStore()
        workingData.runtimeData.collect(executionData, SessionInfoStore(), false)
        val coverageBuilder = CoverageBuilder()
        Analyzer(executionData, coverageBuilder).apply {
            try {
                for ((name, bytes) in workingData.instrumentationData.coverageClasses) {
                    analyzeClass(bytes, name)
                }
            } catch (_: Exception) {
            }
        }
        return coverageBuilder
    }
}

private class JacocoInstrumentationData(
    val coverageClasses: MutableMap<String, ByteArray> = mutableMapOf(),
)

private class JacocoWorkingData(
    val instrumentationData: JacocoInstrumentationData,
    val runtimeData: RuntimeData = RuntimeData(),
)

object IsolatedJacocoRuntime : IRuntime {
    private const val STACK_SIZE = 6

    override fun generateDataAccessor(classid: Long, classname: String?, probecount: Int, mv: MethodVisitor): Int {
        @Suppress("SpellCheckingInspection")
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            classNameToPath(RuntimeDataAccessor::class.java.name),
            "get",
            "()Ljava/lang/Object;",
            false,
        )
        RuntimeData.generateAccessCall(classid, classname, probecount, mv)
        return STACK_SIZE
    }

    override fun startup(data: RuntimeData?) {
        // Nothing to do - the data is owned by the sandbox task
    }

    override fun shutdown() {
        // Nothing to do - the data is owned by the sandbox task
    }

    object RuntimeDataAccessor {
        @JvmStatic
        fun get(): Any {
            val workingData: JacocoWorkingData = Sandbox.CurrentTask.getWorkingData(Jacoco)
            return workingData.runtimeData
        }
    }
}

enum class LineCoverage(val description: String) {
    EMPTY("empty"),
    NOT_COVERED("not covered"),
    PARTLY_COVERED("partly covered"),
    COVERED("fully covered"),
    IGNORED("ignored"),
}

private fun ILine.toLineCoverage() = when (status) {
    ICounter.EMPTY -> LineCoverage.EMPTY
    ICounter.NOT_COVERED -> LineCoverage.NOT_COVERED
    ICounter.PARTLY_COVERED -> LineCoverage.PARTLY_COVERED
    ICounter.FULLY_COVERED -> LineCoverage.COVERED
    else -> error("Invalid iLine status: $status")
}

typealias FileCoverage = Map<Int, LineCoverage>
typealias ClassCoverage = Map<Int, LineCoverage>

@JsonClass(generateAdapter = true)
data class CoverageResult(
    val byFile: Map<String, FileCoverage>,
    val byClass: Map<String, ClassCoverage>,
)

fun Source.processCoverage(
    coverage: CoverageBuilder,
): CoverageResult {
    val byFile = coverage.sourceFiles.filter {
        this.sources.keys.contains(it.name)
    }.associate { fileCoverage ->
        fileCoverage.name!! to (fileCoverage.firstLine..fileCoverage.lastLine).toList().map { lineNumber ->
            lineNumber to fileCoverage.getLine(lineNumber).toLineCoverage()
        }.mapNotNull { (lineNumber, coverage) ->
            try {
                Pair(mapLocation(fileCoverage.name!!, Location(lineNumber, 0)).line, coverage)
            } catch (_: Exception) {
                null
            }
        }.toMap()
    }
    val byClass = coverage.classes.associate { classCoverage ->
        classCoverage.name!! to (classCoverage.firstLine..classCoverage.lastLine).toList().map { lineNumber ->
            lineNumber to classCoverage.getLine(lineNumber).toLineCoverage()
        }.mapNotNull { (lineNumber, coverage) ->
            if (this is Snippet || this is TemplatedSource) {
                val filename = if (this is Snippet) {
                    SNIPPET_SOURCE
                } else {
                    this.sources.keys.also {
                        check(it.size == 1) { "No support for multi-source templates yet" }
                    }.first()
                }
                try {
                    Pair(mapLocation(filename, Location(lineNumber, 0)).line, coverage)
                } catch (_: Exception) {
                    null
                }
            } else {
                Pair(lineNumber, coverage)
            }
        }.toMap()
    }
    return CoverageResult(byFile, byClass)
}

fun IClassCoverage.allMissedLines() = (firstLine..lastLine).toList().filter {
    getLine(it).status == ICounter.NOT_COVERED || getLine(it).status == ICounter.PARTLY_COVERED
}

fun IClassCoverage.printLines() = (firstLine..lastLine).toList().forEach {
    println("$it: ${getLine(it).print()}")
}

private fun ILine.print() = when (status) {
    ICounter.EMPTY -> "empty"
    ICounter.NOT_COVERED -> "uncovered"
    ICounter.PARTLY_COVERED -> "partial"
    ICounter.FULLY_COVERED -> "fully"
    else -> error("Invalid line status: $status")
}
