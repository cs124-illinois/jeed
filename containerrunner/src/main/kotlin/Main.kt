package edu.illinois.cs.cs125.jeed.containerrunner

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import io.github.classgraph.ClassGraph
import mu.KotlinLogging
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Properties
import kotlin.system.exitProcess

@Suppress("UNUSED")
private val logger = KotlinLogging.logger {}

val VERSION: String = Properties().also {
    it.load((object {}).javaClass.getResourceAsStream("/edu.illinois.cs.cs125.jeed.containerrunner.version"))
}.getProperty("version")

class MethodNotFoundException(method: String) : Exception(method)

fun findMethod(klass: String, method: String) = Class.forName(klass).declaredMethods.find {
    it.name == method && Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers) &&
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

class Run : CliktCommand(help = "Load and run METHOD from KLASS") {
    private val klass: String by argument().default("")
    private val method: String by argument().default("")

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

class Version : CliktCommand(help = "print version and exit") {
    override fun run() {
        echo(VERSION)
    }
}

class List : CliktCommand(help = "list available classes") {
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
        fun throws() {
            throw Exception("Failed")
        }
    }
}

class Test : CliktCommand(help = "load test class") {
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

fun main(args: Array<String>) = ContainerRunner()
    .subcommands(Version(), Run(), List(), Test())
    .main(args)
