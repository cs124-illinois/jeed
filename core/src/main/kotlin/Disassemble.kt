package edu.illinois.cs.cs125.jeed.core

import kotlinx.serialization.Serializable
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.TraceClassVisitor
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.nio.charset.Charset

fun disassembleClass(bytecode: ByteArray): String {
    val classReader = ClassReader(bytecode)
    val output = ByteArrayOutputStream()
    val tracingVisitor = TraceClassVisitor(PrintWriter(output))
    classReader.accept(tracingVisitor, 0)
    return output.toString(Charset.defaultCharset())
}

@Throws(DisassembleFailed::class)
fun CompiledSource.disassemble(): DisassembleResults {
    try {
        val disassemblies = this.classLoader.bytecodeForClasses.mapValues { (_, bytecode) ->
            disassembleClass(bytecode)
        }
        return DisassembleResults(disassemblies)
    } catch (e: Exception) {
        throw DisassembleFailed(e)
    }
}

@Serializable
data class DisassembleResults(val disassemblies: Map<String, String>)

class DisassembleFailed(override val cause: Exception) : Exception(cause)

@Serializable
class DisassembleFailedResult(val message: String) {
    constructor(cause: Exception) : this(cause.message ?: cause.javaClass.name)
}
