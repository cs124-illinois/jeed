package edu.illinois.cs.cs125.jeed.containerrunner

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import edu.illinois.cs.cs125.jeed.core.VERSION
import io.github.classgraph.ClassGraph
import mu.KotlinLogging
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.system.exitProcess

@Suppress("UNUSED")
private val logger = KotlinLogging.logger {}

class MethodNotFoundException(method: String) : Exception(method)

fun findMethod(klass: String, method: String) = Class.forName(klass).declaredMethods.find {
    it.name == method &&
        Modifier.isStatic(it.modifiers) &&
        Modifier.isPublic(it.modifiers) &&
        (
            it.parameterTypes.isEmpty() ||
                (it.parameterTypes.size == 1 && it.parameterTypes[0].canonicalName == "java.lang.String[]")
            )
} ?: throw MethodNotFoundException(method)

fun Method.run() {
    if (parameterTypes.isEmpty()) {
        this.invoke(null)
    } else {
        this.invoke(null, null)
    }
}

fun Throwable.cleanStackTrace(): String {
    val originalStackTrace = StringWriter().also {
        this.printStackTrace(PrintWriter(it))
    }.toString().lines().toMutableList()
    val firstLine = originalStackTrace.removeAt(0)
    val betterStackTrace = mutableListOf(firstLine)
    for (line in originalStackTrace) {
        if (line.trim()
                .startsWith("""at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)""")
        ) {
            break
        }
        betterStackTrace.add(line)
    }
    return betterStackTrace.joinToString(separator = "\n")
}

class Run : CliktCommand() {
    private val klass: String by argument().default("")
    private val method: String by argument().default("")

    override fun help(context: Context) = "Load and run METHOD from KLASS"

    override fun run() {
        @Suppress("TooGenericExceptionCaught")
        try {
            findMethod(klass, method).run()
        } catch (e: ClassNotFoundException) {
            echo("Class ${e.message} not found")
            exitProcess(1)
        } catch (e: MethodNotFoundException) {
            echo("Method ${e.message} not found")
            exitProcess(1)
        } catch (e: Throwable) {
            System.err.println((e.cause ?: e).cleanStackTrace())
            exitProcess(1)
        }
    }
}

class Version : CliktCommand() {
    override fun help(context: Context) = "print version and exit"
    override fun run() {
        echo(VERSION)
    }
}

class List : CliktCommand() {
    override fun help(context: Context) = "list available classes"
    override fun run() {
        ClassGraph().enableClassInfo().scan().allClasses.forEach { echo(it.name) }
    }
}

@Suppress("UNUSED", "UtilityClassWithPublicConstructor")
class TestClass {
    companion object {
        @JvmStatic
        fun testing() {
            println("Success")
        }

        @JvmStatic
        @Suppress("TooGenericExceptionThrown")
        fun throws(): Unit = throw Exception("Failed")
    }
}

class Test : CliktCommand() {
    override fun help(context: Context) = "load test class"
    override fun run() {
        try {
            findMethod("edu.illinois.cs.cs125.jeed.containerrunner.TestClass", "testing").run()
        } catch (e: ClassNotFoundException) {
            echo("Class ${e.message} not found")
            exitProcess(1)
        } catch (e: MethodNotFoundException) {
            echo("Method ${e.message} not found")
            exitProcess(1)
        }
    }
}

class ContainerRunner : CliktCommand() {
    override fun run() = Unit
}

fun main(args: Array<String>) {
    System.setProperty("slf4j.internal.verbosity", "WARN")
    ContainerRunner()
        .subcommands(Version(), Run(), List(), Test())
        .main(args)
}
