package edu.illinois.cs.cs125.jeed.core.sandbox

import edu.illinois.cs.cs125.jeed.core.ConfiguredSandboxPlugin
import edu.illinois.cs.cs125.jeed.core.RewritingContext
import edu.illinois.cs.cs125.jeed.core.Sandbox
import edu.illinois.cs.cs125.jeed.core.SandboxPlugin
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.SourceExecutionArguments
import edu.illinois.cs.cs125.jeed.core.compile
import edu.illinois.cs.cs125.jeed.core.execute
import edu.illinois.cs.cs125.jeed.core.fromSnippet
import edu.illinois.cs.cs125.jeed.core.haveCompleted
import edu.illinois.cs.cs125.jeed.core.haveOutput
import edu.illinois.cs.cs125.jeed.core.kompile
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

class TestPluginHooks :
    StringSpec({
        "should run each plugin hook" {
            val plugin = RecordingPlugin()
            val executionResult = Source.fromSnippet(
                """
System.out.println("Here");
            """.trim(),
            ).compile().execute(SourceExecutionArguments().addPlugin(plugin))

            executionResult should haveCompleted()
            val record = executionResult.pluginResult(plugin)
            record.startedInSandbox shouldBe true
            record.finished shouldBe true
            record.transformed.map { it.name } shouldContain "Main"
        }
        "should transform after the sandbox has rewritten a class" {
            val plugin = RecordingPlugin()
            val executionResult = Source.fromSnippet(
                """
System.out.println("Here");
            """.trim(),
            ).compile().execute(SourceExecutionArguments().addPlugin(plugin))

            executionResult should haveCompleted()
            val forMain = executionResult.pluginResult(plugin).transformed.filter { it.name == "Main" }
            // The enclosure check the sandbox adds to every method marks bytecode it has already rewritten
            forMain.filter { it.before }.map { it.sandboxed } shouldBe listOf(false)
            forMain.filter { !it.before }.map { it.sandboxed } shouldBe listOf(true)
        }
        "should let a plugin supply a system property to a task" {
            val snippet = Source.fromSnippet(
                """
System.out.println(System.getProperty("jeed.test.plugin"));
            """.trim(),
            ).compile()

            val withoutPlugin = snippet.execute()
            withoutPlugin shouldNot haveCompleted()
            withoutPlugin.permissionDenied shouldBe true

            val plugin = RecordingPlugin(systemProperties = mapOf("jeed.test.plugin" to "supplied"))
            val withPlugin = snippet.execute(SourceExecutionArguments().addPlugin(plugin))
            withPlugin should haveCompleted()
            withPlugin should haveOutput("supplied")
            withPlugin.permissionDenied shouldBe false
        }
        "should let a plugin require classes the whitelist would otherwise refuse" {
            val snippet = Source.fromSnippet(
                """
import java.util.List;
import java.util.ArrayList;

List list = new ArrayList<String>();
list.add("Here");
            """.trim(),
            ).compile()
            fun arguments() = SourceExecutionArguments(
                classLoaderConfiguration = Sandbox.ClassLoaderConfiguration(
                    whitelistedClasses = setOf("java.lang."),
                    blacklistedClasses = setOf(),
                ),
            )

            val withoutPlugin = snippet.execute(arguments())
            withoutPlugin shouldNot haveCompleted()
            withoutPlugin.permissionDenied shouldBe true

            val plugin = RecordingPlugin(
                required = setOf(java.util.List::class.java, java.util.ArrayList::class.java),
            )
            // Printing would need java.io.PrintStream too, which is the point: only what the plugin
            // requires is exempt from the whitelist
            val withPlugin = snippet.execute(arguments().addPlugin(plugin))
            withPlugin should haveCompleted()
        }
        "should transform reloaded classes only for a plugin that asks to" {
            val coroutines = Source(
                mapOf(
                    "Main.kt" to """
import kotlinx.coroutines.*

fun main() {
    runBlocking {
        delay(1)
        println("Here")
    }
}
                    """.trimIndent(),
                ),
            ).kompile()

            val incurious = RecordingPlugin()
            coroutines.execute(
                SourceExecutionArguments(timeout = 10000).addPlugin(incurious),
            ).also { executionResult ->
                executionResult should haveCompleted()
                executionResult.pluginResult(incurious)
                    .transformed.filter { it.context == RewritingContext.RELOADED }.shouldBeEmpty()
            }

            val curious = RecordingPlugin(transformsReloaded = true)
            coroutines.execute(
                SourceExecutionArguments(timeout = 10000).addPlugin(curious),
            ).also { executionResult ->
                executionResult should haveCompleted()
                executionResult.pluginResult(curious)
                    .transformed.filter { it.context == RewritingContext.RELOADED }.shouldNotBeEmpty()
            }
        }
    })

/**
 * A plugin that records which of its hooks the sandbox called, so that the extension points the published
 * SandboxPlugin interface offers are exercised by something in this repository.
 */
private class RecordingPlugin(
    private val systemProperties: Map<String, String> = mapOf(),
    private val required: Set<Class<*>> = setOf(),
    transformsReloaded: Boolean = false,
) : SandboxPlugin<Unit, RecordingPlugin.Record> {

    /** One call to [transformBeforeSandbox] or [transformAfterSandbox]. */
    class Transform(val name: String, val before: Boolean, val context: RewritingContext, val sandboxed: Boolean)

    class Record(val transformed: List<Transform>, val startedInSandbox: Boolean, val finished: Boolean)

    /** Per class loader, since that is what the sandbox transforms bytecode for. */
    class Transforms {
        val transformed = mutableListOf<Transform>()
    }

    /** Per execution. */
    class Working(val transforms: Transforms) {
        var startedInSandbox = false
        var finished = false
    }

    override val transformsReloadedClasses = transformsReloaded

    override val requiredClasses get() = required

    override fun createInstrumentationData(
        arguments: Unit,
        classLoaderConfiguration: Sandbox.ClassLoaderConfiguration,
        allPlugins: List<ConfiguredSandboxPlugin<*, *>>,
    ) = Transforms()

    override fun transformBeforeSandbox(
        bytecode: ByteArray,
        name: String,
        instrumentationData: Any?,
        context: RewritingContext,
    ) = bytecode.also { record(instrumentationData, name, before = true, context = context, bytecode = it) }

    override fun transformAfterSandbox(
        bytecode: ByteArray,
        name: String,
        instrumentationData: Any?,
        context: RewritingContext,
    ) = bytecode.also { record(instrumentationData, name, before = false, context = context, bytecode = it) }

    override fun createInitialData(instrumentationData: Any?, executionArguments: Sandbox.ExecutionArguments) = Working(instrumentationData as Transforms)

    override fun executionStartedInSandbox() {
        Sandbox.CurrentTask.getWorkingData<Working>(this).startedInSandbox = true
    }

    override fun executionFinished(workingData: Any?) {
        (workingData as Working).finished = true
    }

    override fun createFinalData(workingData: Any?) = (workingData as Working).let {
        Record(it.transforms.transformed.toList(), it.startedInSandbox, it.finished)
    }

    override fun getSystemProperty(property: String) = systemProperties[property]

    private fun record(
        instrumentationData: Any?,
        name: String,
        before: Boolean,
        context: RewritingContext,
        bytecode: ByteArray,
    ) {
        (instrumentationData as Transforms).transformed.add(
            Transform(name, before, context, bytecode.mentionsEnclosureCheck()),
        )
    }
}

/** Whether the sandbox has already added its enclosure check, which it does to every method it rewrites. */
private fun ByteArray.mentionsEnclosureCheck() = String(this, Charsets.ISO_8859_1).contains(Sandbox.RewriteBytecode.enclosureMethodName)
