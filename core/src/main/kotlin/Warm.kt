package edu.illinois.cs.cs125.jeed.core

import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

private const val COROUTINE_INIT_TIMEOUT = 10000L

val isWindows = System.getProperty("os.name").lowercase().startsWith("windows")

@Suppress("MagicNumber", "SpellCheckingInspection")
suspend fun warm(indent: Int = 2, failLint: Boolean = true, quiet: Boolean = false) {
    Source.fromSnippet(
        """System.out.println("javac initialized");""",
        SnippetArguments(indent = indent),
    ).also {
        it.checkstyle(CheckstyleArguments(failOnError = failLint))
        it.complexity()
    }.compile().execute().output.also {
        if (!quiet) {
            logger.info(it)
        }
    }
    Source.fromSnippet(
        """println("kotlinc initialized")""",
        SnippetArguments(indent = indent, fileType = Source.FileType.KOTLIN),
    ).also {
        it.ktLint(KtLintArguments(failOnError = failLint))
    }.kompile().execute().output.also {
        if (!quiet) {
            logger.info(it)
        }
    }
    Source.fromSnippet(
        """import kotlinx.coroutines.*
          |GlobalScope.launch {
          |  delay(1)
             println("coroutine isolation initialized")
          }
        """.trimMargin(),
        SnippetArguments(indent = indent, fileType = Source.FileType.KOTLIN),
    ).kompile()
        .execute(SourceExecutionArguments(waitForShutdown = true, timeout = COROUTINE_INIT_TIMEOUT)).output.also {
            if (!quiet) {
                logger.info(it)
            }
        }
}

private var dockerEnabled: Boolean? = null

@Synchronized
fun checkDockerEnabled(shouldThrow: Boolean = false): Boolean {
    if (isWindows || System.getenv("JEED_DOCKER_DISABLED") != null) {
        dockerEnabled = false
        return false
    }
    if (dockerEnabled == true) {
        return dockerEnabled!!
    }
    return try {
        runBlocking {
            val haveContainerRunner =
                "docker image inspect ${ContainerExecutionArguments.DEFAULT_IMAGE} > /dev/null 2> /dev/null".let {
                    ProcessBuilder(listOf("/bin/sh", "-c", it)).start().let { dockerInspectCommand ->
                        dockerInspectCommand.waitFor(4, TimeUnit.SECONDS)
                        dockerInspectCommand.exitValue() == 0
                    }
                }

            if (!haveContainerRunner) {
                "docker pull -q ${ContainerExecutionArguments.DEFAULT_IMAGE}".let {
                    ProcessBuilder(listOf("/bin/sh", "-c", it))
                        .start().also { dockerPullCommand ->
                            dockerPullCommand.waitFor(60, TimeUnit.SECONDS)
                            val exitValue = dockerPullCommand.exitValue()
                            check(exitValue == 0) { "$it failed: $exitValue" }
                        }
                }
            }

            Source.fromSnippet(
                """System.out.println("javac initialized");""",
                SnippetArguments(indent = 2),
            ).compile().cexecute().also { cexecuteCommand ->
                check(cexecuteCommand.exitCode == 0)
            }
        }
        dockerEnabled = true
        true
    } catch (e: Exception) {
        dockerEnabled = false
        if (shouldThrow) {
            throw e
        }
        false
    }
}
