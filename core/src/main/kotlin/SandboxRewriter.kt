package edu.illinois.cs.cs125.jeed.core

import edu.illinois.cs.cs125.jeed.core.Sandbox.RewriteBytecode
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ConstantDynamic
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.lang.invoke.LambdaMetafactory
import java.lang.reflect.Modifier
import kotlin.reflect.jvm.javaMethod

/*
 * The ASM machinery that Sandbox.RewriteBytecode.rewrite drives: the visitors that transform a class, and the
 * pre-inspection pass they need first. None of it touches a running task, which is what lets it live here rather
 * than in Sandbox.kt.
 *
 * RewriteBytecode itself stays nested in Sandbox. Its name and its methods' names are derived reflectively and
 * emitted into every class the sandbox rewrites, and cached in that form, so moving it would rename an interface
 * that already-transformed bytecode depends on.
 */

private const val SYNC_WRAPPER_STACK_ITEMS = 2

/** Transforms one class: rewrites each method, and bridges the synchronized ones through the sandbox's own locks. */
internal class SandboxingClassVisitor(
    private val unsafeExceptionClasses: Set<Class<*>>,
    private val blacklistedMethods: Set<Sandbox.MethodFilter>,
    private val preinspections: Map<VisitedMethod, MethodPreinspection>,
    private val context: RewritingContext,
    classWriter: ClassWriter,
) : ClassVisitor(Opcodes.ASM8, classWriter) {
    private var className: String? = null

    private val enclosedHandles = mutableMapOf<Handle, Handle>()

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?,
    ) {
        super.visit(version, access, name, signature, superName, interfaces)
        className = name
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor? = if (name == "finalize" && descriptor == "()V") {
        null // Drop the finalizer
    } else {
        val preinspection = preinspections[VisitedMethod(name, descriptor)]
            ?: error("missing pre-inspection for $name:$descriptor")
        val transformedMethodName = if (Modifier.isSynchronized(access)) {
            val transformedNameOfOriginal = "$name\$syncbody"
            val nonSynchronizedModifiers = access and Modifier.SYNCHRONIZED.inv()
            emitSynchronizedBridge(
                super.visitMethod(nonSynchronizedModifiers, name, descriptor, signature, exceptions),
                className ?: error("should have visited the class"),
                transformedNameOfOriginal,
                preinspection.parameters,
                access,
                descriptor,
            )
            transformedNameOfOriginal
        } else {
            name
        }
        val transformedModifiers = if (Modifier.isSynchronized(access)) {
            (
                access
                    and Modifier.PUBLIC.inv()
                    and Modifier.PROTECTED.inv()
                    and Modifier.SYNCHRONIZED.inv()
                ) or Modifier.PRIVATE
        } else {
            access
        }
        SandboxingMethodVisitor(
            className ?: error("should have visited the class"),
            unsafeExceptionClasses,
            blacklistedMethods,
            preinspection.badTryCatchBlockPositions,
            context,
            this::getEnclosedHandle,
            super.visitMethod(
                transformedModifiers,
                transformedMethodName,
                descriptor,
                signature,
                exceptions,
            ),
        )
    }

    private fun getEnclosedHandle(handle: Handle) = enclosedHandles.getOrPut(handle) {
        val handleType = Type.getType(handle.desc)
        val actualParameters = handleType.argumentTypes.toMutableList()
        var actualReturn = handleType.returnType
        when (handle.tag) {
            Opcodes.H_INVOKESPECIAL, Opcodes.H_INVOKEVIRTUAL, Opcodes.H_INVOKEINTERFACE -> {
                actualParameters.add(0, Type.getObjectType(handle.owner))
            }

            Opcodes.H_NEWINVOKESPECIAL -> {
                actualReturn = Type.getObjectType(handle.owner)
            }
        }
        val actualType = Type.getMethodType(actualReturn, *actualParameters.toTypedArray())
        val safeOwnerName = handle.owner.replace('/', '_').replace('$', '_')
        val safeMethodName = if (handle.name == "<init>") "NEW\$" else handle.name
        val wrapperName = "sandboxMH${handle.tag}\$$safeOwnerName\$$safeMethodName"
        val wrapperMv = super.visitMethod(
            Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC,
            wrapperName,
            actualType.descriptor,
            null,
            null,
        )
        wrapperMv.visitCode()
        wrapperMv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            RewriteBytecode.rewriterClassName,
            if (context == RewritingContext.RELOADED) RewriteBytecode.enclosureReloadedMethodName else RewriteBytecode.enclosureMethodName,
            RewriteBytecode.enclosureMethodsDescriptor,
            false,
        )
        wrapperMv.visitLdcInsn(handle)
        var parameterLocal = 0
        actualParameters.forEach {
            wrapperMv.visitIntInsn(it.getOpcode(Opcodes.ILOAD), parameterLocal)
            parameterLocal += it.size
        }
        wrapperMv.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "java/lang/invoke/MethodHandle",
            "invokeExact",
            actualType.descriptor,
            false,
        )
        wrapperMv.visitInsn(actualReturn.getOpcode(Opcodes.IRETURN))
        wrapperMv.visitMaxs(parameterLocal + 2, parameterLocal) // +2 in case of long return
        wrapperMv.visitEnd()
        Handle(
            Opcodes.H_INVOKESTATIC,
            className,
            wrapperName,
            actualType.descriptor,
            false,
        )
    }
}

@Suppress("LongParameterList", "ComplexMethod")
private fun emitSynchronizedBridge(
    template: MethodVisitor,
    className: String,
    bridgeTo: String,
    parameters: List<VisitedParameter>,
    modifiers: Int,
    descriptor: String,
) {
    val methodVisitor = MonitorIsolatingMethodVisitor(template)
    fun loadSelf() {
        if (Modifier.isStatic(modifiers)) {
            methodVisitor.visitLdcInsn(Type.getType("L$className;")) // the class object
        } else {
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0) // this
        }
    }
    parameters.forEach { methodVisitor.visitParameter(it.name, it.modifiers) }
    methodVisitor.visitCode()
    val callStartLabel = Label()
    val callEndLabel = Label()
    val finallyLabel = Label()
    methodVisitor.visitTryCatchBlock(callStartLabel, callEndLabel, finallyLabel, null) // try-finally
    loadSelf()
    methodVisitor.visitInsn(Opcodes.MONITORENTER) // will be transformed by MonitorIsolatingMethodVisitor
    var localIndex = 0
    if (!Modifier.isStatic(modifiers)) {
        loadSelf()
        localIndex++
    }
    Type.getArgumentTypes(descriptor).forEach {
        methodVisitor.visitVarInsn(it.getOpcode(Opcodes.ILOAD), localIndex)
        localIndex += it.size
    }
    methodVisitor.visitLabel(callStartLabel)
    methodVisitor.visitMethodInsn(
        if (Modifier.isStatic(modifiers)) Opcodes.INVOKESTATIC else Opcodes.INVOKESPECIAL,
        className,
        bridgeTo,
        descriptor,
        false,
    )
    methodVisitor.visitLabel(callEndLabel)
    loadSelf()
    methodVisitor.visitInsn(Opcodes.MONITOREXIT)
    methodVisitor.visitInsn(Type.getReturnType(descriptor).getOpcode(Opcodes.IRETURN))
    methodVisitor.visitLabel(finallyLabel)
    val onlyThrowableOnStack = arrayOf<Any>(classNameToPath(Throwable::class.java.name))
    methodVisitor.visitFrame(Opcodes.F_SAME1, 0, emptyArray(), 1, onlyThrowableOnStack)
    loadSelf()
    methodVisitor.visitInsn(Opcodes.MONITOREXIT)
    methodVisitor.visitInsn(Opcodes.ATHROW)
    val returnSize = Type.getReturnType(descriptor).size
    methodVisitor.visitMaxs(localIndex + returnSize + SYNC_WRAPPER_STACK_ITEMS, localIndex)
    methodVisitor.visitEnd()
}

internal open class MonitorIsolatingMethodVisitor(
    downstream: MethodVisitor,
) : MethodVisitor(Opcodes.ASM8, downstream) {
    override fun visitInsn(opcode: Int) {
        when (opcode) {
            Opcodes.MONITORENTER -> {
                super.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    RewriteBytecode.rewriterClassName,
                    RewriteBytecode::enterMonitor.javaMethod?.name ?: error("missing enter-monitor name"),
                    Type.getMethodDescriptor(RewriteBytecode::enterMonitor.javaMethod),
                    false,
                )
            }

            Opcodes.MONITOREXIT -> {
                super.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    RewriteBytecode.rewriterClassName,
                    RewriteBytecode::exitMonitor.javaMethod?.name ?: error("missing exit-monitor name"),
                    Type.getMethodDescriptor(RewriteBytecode::exitMonitor.javaMethod),
                    false,
                )
            }

            else -> super.visitInsn(opcode)
        }
    }
}

internal class SandboxingMethodVisitor(
    val containerClassName: String,
    val unsafeExceptionClasses: Set<Class<*>>,
    val blacklistedMethods: Set<Sandbox.MethodFilter>,
    val badTryCatchBlockPositions: Set<Int>,
    val rewritingContext: RewritingContext,
    val handleEncloser: (Handle) -> Handle,
    methodVisitor: MethodVisitor,
) : MonitorIsolatingMethodVisitor(methodVisitor) {
    private val labelsToRewrite: MutableSet<Label> = mutableSetOf()
    private var rewroteLabel = false
    private var currentTryCatchBlockPosition = -1

    override fun visitCode() {
        super.visitCode()
        super.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            RewriteBytecode.rewriterClassName,
            if (rewritingContext == RewritingContext.RELOADED) RewriteBytecode.enclosureReloadedMethodName else RewriteBytecode.enclosureMethodName,
            RewriteBytecode.enclosureMethodsDescriptor,
            false,
        )
    }

    override fun visitTryCatchBlock(start: Label, end: Label, handler: Label, type: String?) {
        currentTryCatchBlockPosition++
        if (start == handler) {
            /*
             * For unclear reasons, the Java compiler sometimes emits exception table entries that catch any
             * exception and transfer control to the inside of the same block. This produces an infinite loop
             * if an exception is thrown, e.g. by our checkException function. Since any exception during
             * non-sandboxed execution would also cause this infinite loop, the table entry must not serve any
             * purpose. Drop it to avoid the infinite loop.
             */
            return
        }
        val exceptionClass = type?.let {
            try {
                Class.forName(pathToClassName(type))
            } catch (_: ClassNotFoundException) {
                null
            }
        }
        if (exceptionClass == null) {
            labelsToRewrite.add(handler)
        } else {
            val needsRewrite = unsafeExceptionClasses
                .any { exceptionClass.isAssignableFrom(it) || it.isAssignableFrom(exceptionClass) }
            if (needsRewrite) {
                labelsToRewrite.add(handler)
            }
        }
        /*
         * For unclear reasons, the Java compiler *also* sometimes emits exception table entries that cover
         * a larger region than necessary and partially overlap the handler, especially with throw statements
         * inside try-finally blocks. These entries do have functional significance, but must have their
         * protected regions shortened to avoid an infinite loop.
         */
        val safeEnd = if (currentTryCatchBlockPosition in badTryCatchBlockPositions) handler else end
        super.visitTryCatchBlock(start, safeEnd, handler, type)
    }

    private var nextLabel: Label? = null
    override fun visitLabel(label: Label) {
        if (labelsToRewrite.contains(label)) {
            assert(nextLabel == null)
            nextLabel = label
        }
        super.visitLabel(label)
    }

    override fun visitFrame(
        type: Int,
        numLocal: Int,
        local: Array<out Any>?,
        numStack: Int,
        stack: Array<out Any>?,
    ) {
        super.visitFrame(type, numLocal, local, numStack, stack)
        if (nextLabel != null) {
            super.visitInsn(Opcodes.DUP)
            super.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                RewriteBytecode.rewriterClassName,
                RewriteBytecode.checkMethodName,
                RewriteBytecode.checkMethodDescription,
                false,
            )
            rewroteLabel = true
            labelsToRewrite.remove(nextLabel ?: error("nextLabel changed"))
            nextLabel = null
        }
    }

    private fun isBlacklistedMethod(ownerBinaryName: String, methodName: String): Boolean {
        val ownerClassName = binaryNameToClassName(ownerBinaryName)
        return blacklistedMethods.any {
            ownerClassName.startsWith(it.ownerClassPrefix) &&
                methodName.startsWith(it.methodPrefix) &&
                (rewritingContext == RewritingContext.UNTRUSTED || !it.allowInReload)
        }
    }

    private fun addForbiddenMethodTrap() {
        super.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            RewriteBytecode.rewriterClassName,
            RewriteBytecode::forbiddenMethod.javaMethod?.name ?: error("need forbidden method name"),
            Type.getMethodDescriptor(RewriteBytecode::forbiddenMethod.javaMethod),
            false,
        )
    }

    override fun visitMethodInsn(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean,
    ) {
        if (isBlacklistedMethod(owner, name)) {
            // Adding an extra call instead of replacing the call avoids the need for fiddly stack manipulation
            addForbiddenMethodTrap()
        }
        val rewriteTarget = if (!isInterface &&
            opcode == Opcodes.INVOKEVIRTUAL &&
            owner == classNameToPath(Any::class.java.name)
        ) {
            RewriteBytecode.syncNotifyMethods["$name:$descriptor"]
        } else {
            null
        }
        if (rewriteTarget != null) {
            super.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                RewriteBytecode.rewriterClassName,
                rewriteTarget.javaMethod?.name ?: error("missing notification method name"),
                Type.getMethodDescriptor(rewriteTarget.javaMethod),
                false,
            )
        } else if (rewritingContext == RewritingContext.RELOADED &&
            isIgnorableSetContextClassLoader(containerClassName, opcode, owner, name, descriptor)
        ) {
            super.visitInsn(Opcodes.POP2)
        } else {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
        }
    }

    private fun sandboxBootstrapArguments(args: Array<out Any?>): Array<Any?> = args.map {
        if (it is Handle && it.owner.contains('/')) { // Reference to library method
            handleEncloser(it)
        } else if (it is ConstantDynamic) {
            sandboxConstantDynamic(it)
        } else {
            it
        }
    }.toTypedArray()

    private fun sandboxConstantDynamic(condy: ConstantDynamic): ConstantDynamic = ConstantDynamic(
        condy.name,
        condy.descriptor,
        condy.bootstrapMethod,
        *sandboxBootstrapArguments(condy.bootstrapArguments),
    )

    private fun hasBlacklistedBootstrapRef(args: Array<out Any?>) = args.any {
        when (it) {
            is Handle -> isBlacklistedMethod(it.owner, it.name)
            is ConstantDynamic -> isConstantDynamicBlacklisted(it)
            else -> false
        }
    }

    private fun isConstantDynamicBlacklisted(condy: ConstantDynamic): Boolean = isBlacklistedMethod(condy.bootstrapMethod.owner, condy.bootstrapMethod.name) ||
        hasBlacklistedBootstrapRef(condy.bootstrapArguments)

    override fun visitInvokeDynamicInsn(
        name: String?,
        descriptor: String,
        bootstrapMethodHandle: Handle,
        vararg bootstrapMethodArguments: Any?,
    ) {
        val forbidden = isBlacklistedMethod(bootstrapMethodHandle.owner, bootstrapMethodHandle.name) ||
            hasBlacklistedBootstrapRef(bootstrapMethodArguments)
        if (forbidden) {
            addForbiddenMethodTrap()
        }
        val arguments = if (!forbidden && rewritingContext == RewritingContext.UNTRUSTED) {
            sandboxBootstrapArguments(bootstrapMethodArguments)
        } else {
            bootstrapMethodArguments
        }
        val newDesc = if (bootstrapMethodHandle.owner == Type.getInternalName(LambdaMetafactory::class.java)) {
            /*
             * LambdaMetafactory requires all bound parameter types to match exactly between the implementation
             * handle type and the factory type... except for the receiver type in the case of an instance
             * method being the implementation. The Java compiler takes advantage of this special case and
             * uses the specific receiver type for the factory type even when the method is inherited.
             * Unfortunately, enclosing the implementation handle in an H_INVOKESTATIC-kind handle disables the
             * special handling in LMF. The factory type must therefore be adjusted when an instance method
             * handle has been enclosed (adding 1 to the argument list as seen by ASM) and its receiver will be
             * bound (factory argument list is nonempty).
             */
            val originalHandle = bootstrapMethodArguments[1] as Handle
            val originalHandleType = Type.getType(originalHandle.desc)
            val sandboxedHandle = arguments[1] as Handle
            val sandboxedHandleType = Type.getType(sandboxedHandle.desc)
            val factoryType = Type.getType(descriptor)
            val factoryArgTypes = factoryType.argumentTypes
            if (originalHandleType.argumentTypes.size != sandboxedHandleType.argumentTypes.size &&
                // instance
                factoryArgTypes.isNotEmpty() // bound
            ) {
                factoryArgTypes[0] = sandboxedHandleType.argumentTypes[0]
                Type.getMethodDescriptor(factoryType.returnType, *factoryArgTypes)
            } else {
                descriptor
            }
        } else {
            descriptor
        }
        super.visitInvokeDynamicInsn(
            name,
            newDesc,
            bootstrapMethodHandle,
            *arguments,
        )
    }

    override fun visitLdcInsn(value: Any?) {
        if (value is ConstantDynamic && isConstantDynamicBlacklisted(value)) {
            addForbiddenMethodTrap()
        }
        val sandboxed = if (value is ConstantDynamic && rewritingContext == RewritingContext.UNTRUSTED) {
            sandboxConstantDynamic(value)
        } else {
            value
        }
        super.visitLdcInsn(sandboxed)
    }

    override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        // The DUP instruction for checkException calls makes the stack one item taller
        super.visitMaxs(maxStack + 1, maxLocals)
    }

    override fun visitEnd() {
        assert(labelsToRewrite.isEmpty()) { "failed to write all flagged labels" }
        super.visitEnd()
    }
}

internal data class VisitedMethod(val name: String, val descriptor: String)
internal data class VisitedParameter(val name: String?, val modifiers: Int)
internal data class MethodPreinspection(
    val badTryCatchBlockPositions: Set<Int>,
    val parameters: List<VisitedParameter>,
)

internal fun preInspectMethods(reader: ClassReader): Map<VisitedMethod, MethodPreinspection> {
    /*
     * ASM doesn't provide a way to get the bytecode positions of a try-catch block's labels while the code
     * is being visited, so we have to go through all the methods to figure out the positions of the labels
     * with respect to each other to determine which try-catch blocks are bad (i.e. will loop forever) before
     * doing the real visit in SandboxingMethodVisitor.
     */
    val methodVisitors = mutableMapOf<VisitedMethod, PreviewingMethodVisitor>()
    reader.accept(
        object : ClassVisitor(Opcodes.ASM8) {
            override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                exceptions: Array<out String>?,
            ): MethodVisitor = PreviewingMethodVisitor()
                .also { methodVisitors[VisitedMethod(name, descriptor)] = it }
        },
        0,
    )
    return methodVisitors.mapValues {
        MethodPreinspection(it.value.getBadTryCatchBlockPositions(), it.value.getParameters())
    }
}

private class PreviewingMethodVisitor : MethodVisitor(Opcodes.ASM8) {

    private val labelPositions = mutableMapOf<Label, Int>()
    private val tryCatchBlocks = mutableListOf<Triple<Label, Label, Label>>()
    private val parameters = mutableListOf<VisitedParameter>()

    override fun visitLabel(label: Label) {
        super.visitLabel(label)
        labelPositions[label] = labelPositions.size
    }

    override fun visitTryCatchBlock(start: Label, end: Label, handler: Label, type: String?) {
        super.visitTryCatchBlock(start, end, handler, type)
        tryCatchBlocks.add(Triple(start, end, handler))
    }

    override fun visitParameter(name: String?, access: Int) {
        super.visitParameter(name, access)
        parameters.add(VisitedParameter(name, access))
    }

    fun getBadTryCatchBlockPositions(): Set<Int> {
        // Called after this visitor has accepted the entire method, so all positioning information is ready
        val badPositions = mutableSetOf<Int>()
        tryCatchBlocks.forEachIndexed { i, (startLabel, endLabel, handlerLabel) ->
            val startPos = labelPositions[startLabel] ?: error("start $startLabel not visited")
            val endPos = labelPositions[endLabel] ?: error("end $endLabel not visited")
            val handlerPos = labelPositions[handlerLabel] ?: error("handler $handlerLabel not visited")
            if (handlerPos in startPos until endPos) badPositions.add(i)
        }
        return badPositions
    }

    fun getParameters(): List<VisitedParameter> = parameters
}
