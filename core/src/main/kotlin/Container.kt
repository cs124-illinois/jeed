@file:Suppress("MatchingDeclarationName")

package edu.illinois.cs.cs125.jeed.core

import com.squareup.moshi.JsonClass
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import edu.illinois.cs.cs125.jeed.core.version as JEED_VERSION

private val CONTAINER_TMP_DIR = System.getenv("JEED_CONTAINER_TMP_DIR")

@Suppress("TooGenericExceptionCaught")
private val MAX_CONCURRENT_CONTAINERS = try {
    System.getenv("JEED_MAX_CONCURRENT_CONTAINERS").toInt()
} catch (e: Exception) {
    Runtime.getRuntime().availableProcessors()
}
private val containerSemaphore = Semaphore(MAX_CONCURRENT_CONTAINERS)

@JsonClass(generateAdapter = true)
data class ContainerExecutionArguments(
    var klass: String? = null,
    var method: String? = null,
    val image: String = DEFAULT_IMAGE,
    val tmpDir: String? = CONTAINER_TMP_DIR,
    val timeout: Long = DEFAULT_TIMEOUT,
    val maxOutputLines: Int = Sandbox.ExecutionArguments.DEFAULT_MAX_OUTPUT_LINES,
    val containerArguments: String =
        """--network="none"""",
) {
    companion object {
        @Suppress("SpellCheckingInspection")
        val DEFAULT_IMAGE = "cs124/jeed-containerrunner:$JEED_VERSION"
        const val DEFAULT_TIMEOUT = 2000L
    }
}

@JsonClass(generateAdapter = true)
data class ContainerExecutionResults(
    val klass: String,
    val method: String,
    val exitCode: Int?,
    val timeout: Boolean,
    val outputLines: List<Sandbox.TaskResults.OutputLine>,
    val interval: Interval,
    val executionInterval: Interval,
    val truncatedLines: Int,
) {
    val completed: Boolean
        get() {
            return exitCode != null && exitCode == 0 && !timeout
        }
    val output: String
        get() {
            return outputLines.sortedBy { it.timestamp }.joinToString(separator = "\n") { it.line }
        }
}

@Suppress("LongMethod")
suspend fun CompiledSource.cexecute(
    executionArguments: ContainerExecutionArguments = ContainerExecutionArguments(),
): ContainerExecutionResults {
    val started = Instant.now()

    val defaultKlass = when (this.source.type) {
        Source.SourceType.JAVA -> "Main"
        Source.SourceType.KOTLIN -> "MainKt"
        else -> error("Can't cexecute mixed sources")
    }

    // Check that we can load the class before we start the container
    // Note that this check is different than what is used to load the method by the actual container runner,
    // but should be similar enough
    val methodToRun = classLoader.findClassMethod(
        executionArguments.klass,
        executionArguments.method,
        defaultKlass,
        SourceExecutionArguments.DEFAULT_METHOD,
    )
    executionArguments.klass = executionArguments.klass ?: methodToRun.declaringClass.simpleName
    executionArguments.method = executionArguments.method ?: methodToRun.getQualifiedName()

    val tempRoot = when {
        executionArguments.tmpDir != null -> File(executionArguments.tmpDir)
        CONTAINER_TMP_DIR != null -> File(CONTAINER_TMP_DIR)
        else -> null
    }

    return withTempDir(tempRoot) { tempDir ->
        eject(tempDir)

        val containerMethodName = executionArguments.method!!.split("(")[0]

        val dockerName = UUID.randomUUID().toString()
        val actualCommand = "docker run " +
            "--name $dockerName " +
            "-v $tempDir:/jeed/ " +
            executionArguments.containerArguments +
            " ${executionArguments.image} " +
            "-- run ${executionArguments.klass!!} $containerMethodName"

        @Suppress("SpreadOperator")
        val processBuilder = ProcessBuilder(*listOf("/bin/sh", "-c", actualCommand).toTypedArray()).directory(tempDir)

        containerSemaphore.withPermit {
            val process = processBuilder.start()
            val stdoutLines = StreamGobbler(
                Sandbox.TaskResults.OutputLine.Console.STDOUT,
                process.inputStream,
                executionArguments.maxOutputLines,
            )
            val stderrLines = StreamGobbler(
                Sandbox.TaskResults.OutputLine.Console.STDERR,
                process.errorStream,
                executionArguments.maxOutputLines,
            )
            val stderrThread = Thread(stdoutLines)
            val stdoutThread = Thread(stderrLines)
            stderrThread.start()
            stdoutThread.start()

            var executionStartedAt = ""
            while (executionStartedAt.isBlank()) {
                executionStartedAt =
                    """docker inspect $dockerName -f {{.State.StartedAt}}""".runCommand().trim()
            }
            val executionStarted = Instant.parse(executionStartedAt)

            val timeout = !process.waitFor(executionArguments.timeout, TimeUnit.MILLISECONDS)
            if (timeout) {
                """docker kill $dockerName""".runCommand()
                """docker wait $dockerName""".runCommand()
            }
            val executionEnded =
                Instant.parse("""docker inspect $dockerName -f {{.State.FinishedAt}}""".runCommand().trim())
            """docker rm $dockerName""".runCommand()

            stderrThread.join()
            stdoutThread.join()

            var truncatedLines = stdoutLines.truncatedLines + stderrLines.truncatedLines
            val outputLines = listOf(stdoutLines.commandOutputLines, stderrLines.commandOutputLines)
                .flatten()
                .sortedBy { it.timestamp }
                .filter {
                    !it.line.startsWith("OpenJDK 64-Bit Server VM warning:")
                }
                .also {
                    if (it.size > executionArguments.maxOutputLines) {
                        truncatedLines += it.size - executionArguments.maxOutputLines
                    }
                }
                .take(executionArguments.maxOutputLines)

            ContainerExecutionResults(
                executionArguments.klass!!,
                executionArguments.method!!,
                process.exitValue(),
                timeout,
                outputLines,
                Interval(started, Instant.now()),
                Interval(executionStarted, executionEnded),
                truncatedLines,
            )
        }
    }
}

class StreamGobbler(
    private val console: Sandbox.TaskResults.OutputLine.Console,
    private val inputStream: InputStream,
    private val maxOutputLines: Int,
) : Runnable {
    val commandOutputLines: MutableList<Sandbox.TaskResults.OutputLine> = mutableListOf()
    var truncatedLines = 0

    override fun run() {
        BufferedReader(InputStreamReader(inputStream)).lines().forEach { line ->
            if (line != null) {
                if (commandOutputLines.size < maxOutputLines) {
                    commandOutputLines.add(Sandbox.TaskResults.OutputLine(console, line, Instant.now()))
                } else {
                    truncatedLines++
                }
            }
        }
    }
}

suspend fun <T> withTempDir(root: File? = null, f: suspend (directory: File) -> T): T {
    @Suppress("DEPRECATION", "SpellCheckingInspection")
    val directory = createTempDir("containerrunner", null, root)
    return try {
        f(directory)
    } finally {
        check(directory.deleteRecursively())
    }
}

fun File.isInside(directory: File): Boolean = normalize().startsWith(directory.normalize())

fun CompiledSource.eject(directory: File) {
    require(directory.isDirectory) { "Must eject into a directory" }
    require(directory.exists()) { "Directory to eject into must exist" }
    require(directory.listFiles()?.isEmpty() ?: false) { "Directory to eject into must be empty" }

    fileManager.allClassFiles.forEach { (path, fileObject) ->
        val destination = File(directory, path)
        check(!destination.exists()) { "Duplicate file found during ejection: $path" }
        check(destination.isInside(directory)) { "Attempt to write file outside of destination directory" }
        destination.parentFile.mkdirs()
        destination.writeBytes(fileObject.openInputStream().readAllBytes())
        check(destination.exists()) { "File not written during ejection" }
        check(destination.length() > 0) { "Empty file written during ejection" }
    }
}

@Suppress("SpreadOperator")
fun String.runCommand() = ProcessBuilder(*split("\\s".toRegex()).toTypedArray())
    .redirectOutput(ProcessBuilder.Redirect.PIPE)
    .redirectError(ProcessBuilder.Redirect.PIPE)
    .start().let {
        it.waitFor()
        it.inputStream.bufferedReader().readText()
    }
