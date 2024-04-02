package edu.illinois.cs.cs125.jeed.core

import io.kotest.core.spec.style.StringSpec
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.modules.ModuleBuilder
import org.jetbrains.kotlin.cli.common.modules.ModuleChunk
import org.jetbrains.kotlin.cli.jvm.compiler.configureFromArgs
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import javax.tools.JavaFileManager

open class First
class Second : First() {}

class TestMixedKompile : StringSpec({
    "should compile simple mixed sources" {
        val javaSource = Source(
            mapOf(
                "First.java" to """public class First {}"""",
            )
        )
        val kotlinSource = Source(
            mapOf(
                "Second.kt" to """class Second : First()""",
            )
        )
        val module = ModuleBuilder(JvmProtoBufUtil.DEFAULT_MODULE_NAME, ".", "java-production")
        val arguments = K2JVMCompilerArguments()
        arguments.freeArgs = listOf("First.java", "Second.kt")
        module.configureFromArgs(arguments)
        println(ModuleChunk(listOf(module)).modules)


        val fileManager = JeedFileManager(standardFileManager)
        val virtualFile = fileManager.toVirtualFile() as SimpleVirtualFile
        virtualFile.addChild(SimpleVirtualFile("First.java", contents = "public class First {}".toByteArray(), up = virtualFile))
        virtualFile.addChild(SimpleVirtualFile("Second.kt", contents = "class Second: First()".toByteArray(), up = virtualFile))
        val (classLoader, messageCollector) = kompileToFileManager(KompilationArguments(), kotlinSource, parentVirtualFile = virtualFile, javaSource = javaSource)
    }
})
