package edu.illinois.cs.cs125.jeed.core

import com.squareup.moshi.JsonClass
import java.io.ByteArrayInputStream
import java.io.FilePermission
import java.io.InputStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ReflectPermission
import java.net.SocketPermission
import java.security.Permission
import java.util.PropertyPermission

@JsonClass(generateAdapter = true)
@Suppress("LongParameterList")
class SourceExecutionArguments(
    var klass: String? = null,
    var method: String? = null,
    timeout: Long = DEFAULT_TIMEOUT,
    permissions: Set<Permission> = setOf(),
    maxExtraThreads: Int = DEFAULT_MAX_EXTRA_THREADS,
    maxOutputLines: Int = DEFAULT_MAX_OUTPUT_LINES,
    maxIOBytes: Int = DEFAULT_MAX_IO_BYTES,
    classLoaderConfiguration: Sandbox.ClassLoaderConfiguration = Sandbox.ClassLoaderConfiguration(),
    val dryRun: Boolean = false,
    waitForShutdown: Boolean = DEFAULT_WAIT_FOR_SHUTDOWN,
    returnTimeout: Int = DEFAULT_RETURN_TIMEOUT,
    @Transient
    var methodToRun: Method? = null,
    @Transient
    internal val plugins: MutableList<ConfiguredSandboxPlugin<*, *>> = mutableListOf(),
    systemInStream: InputStream? = null,
    permissionBlackList: Boolean = DEFAULT_PERMISSION_BLACKLIST,
    cpuTimeoutNS: Long = DEFAULT_CPU_TIMEOUT,
    pollIntervalMS: Long = DEFAULT_POLL_INTERVAL,
) : Sandbox.ExecutionArguments(
    timeout,
    when (permissionBlackList) {
        true -> permissions.union(Sandbox.BLACKLISTED_PERMISSIONS)
        false -> permissions.union(REQUIRED_PERMISSIONS)
    },
    maxExtraThreads,
    maxOutputLines,
    maxIOBytes,
    classLoaderConfiguration,
    waitForShutdown,
    returnTimeout,
    systemInStream = systemInStream,
    permissionBlacklist = permissionBlackList,
    cpuTimeoutNS = cpuTimeoutNS,
    pollIntervalMS = pollIntervalMS,
) {
    companion object {
        const val DEFAULT_KLASS = "Main"
        const val DEFAULT_METHOD = "main()"
        val REQUIRED_PERMISSIONS = setOf(
            // Why not?
            PropertyPermission("java.version", "read"),
            // Required by newer versions of Kotlin
            PropertyPermission("java.specification.version", "read"),
            PropertyPermission("kotlinx.coroutines.*", "read"),
            RuntimePermission("accessDeclaredMembers"),
            ReflectPermission("suppressAccessChecks"),
            // Required for Date to work
            RuntimePermission("localeServiceProvider"),
            // Not sure why this is required by Date, but it seems to be
            // ClassLoader enumeration is probably not unsafe...
            RuntimePermission("getClassLoader"),
            RuntimePermission("charsetProvider"),
        )
        val GENERALLY_UNSAFE_PERMISSIONS = setOf(
            FilePermission("<<ALL FILES>>", "read,write,execute,delete,readlink"),
            RuntimePermission("manageProcess"),
            RuntimePermission("writeFileDescriptor"),
            SocketPermission("*", "resolve,connect,listen,accept"),
        )
    }

    fun addPlugin(plugin: SandboxPlugin<Unit, *>): SourceExecutionArguments {
        plugins.add(ConfiguredSandboxPlugin(plugin, Unit))
        return this
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun <A : Any> addPlugin(plugin: SandboxPlugin<A, *>, arguments: A): SourceExecutionArguments {
        plugins.add(ConfiguredSandboxPlugin(plugin, arguments))
        return this
    }

    fun <A : Any> addPlugin(plugin: SandboxPluginWithDefaultArguments<A, *>): SourceExecutionArguments = addPlugin(plugin, plugin.createDefaultArguments())
}

class ExecutionFailed(
    val classNotFound: ClassMissingException? = null,
    val methodNotFound: MethodNotFoundException? = null,
) : Exception() {
    class ClassMissingException(@Suppress("unused") val klass: String, message: String?) : Exception(message)
    class MethodNotFoundException(@Suppress("unused") val method: String, message: String?) : Exception(message)

    constructor(classMissing: ClassMissingException) : this(classMissing, null)
    constructor(methodNotFound: MethodNotFoundException) : this(null, methodNotFound)
}

fun CompiledSource.updateExecutionArguments(
    executionArguments: SourceExecutionArguments,
    noFind: Boolean = false,
): SourceExecutionArguments {
    // Coroutines need some extra time and threads to run.
    if (this.source.type == Source.SourceType.KOTLIN && this.usesCoroutines()) {
        executionArguments.timeout = executionArguments.timeout.coerceAtLeast(KOTLIN_COROUTINE_MIN_TIMEOUT)
        executionArguments.maxExtraThreads =
            executionArguments.maxExtraThreads.coerceAtLeast(KOTLIN_COROUTINE_MIN_EXTRA_THREADS)
    }

    if (noFind) {
        return executionArguments
    }

    val defaultKlass = if (this.source is Snippet) {
        this.source.entryClassName
    } else {
        when (this.source.type) {
            Source.SourceType.JAVA -> "Main"
            Source.SourceType.KOTLIN -> "MainKt"
            else -> error("Must specify execution class for mixed sources")
        }
    }

    // Fail fast if the class or method don't exist
    executionArguments.methodToRun = classLoader.findClassMethod(
        executionArguments.klass,
        executionArguments.method,
        defaultKlass,
        SourceExecutionArguments.DEFAULT_METHOD,
    )
    executionArguments.klass = executionArguments.klass ?: executionArguments.methodToRun!!.declaringClass.simpleName
    executionArguments.method = executionArguments.method ?: executionArguments.methodToRun!!.getQualifiedName()
    return executionArguments
}

@Throws(ExecutionFailed::class)
@Suppress("ReturnCount")
suspend fun CompiledSource.execute(
    executionArguments: SourceExecutionArguments = SourceExecutionArguments(),
): Sandbox.TaskResults<out Any?> {
    val actualArguments = updateExecutionArguments(executionArguments)
    return Sandbox.execute(classLoader, actualArguments, actualArguments.plugins) sandbox@{ (classLoader) ->
        if (actualArguments.dryRun) {
            return@sandbox null
        }
        classLoader as Sandbox.SandboxedClassLoader
        @Suppress("SpreadOperator")
        try {
            val method = classLoader
                .loadClass(executionArguments.methodToRun!!.declaringClass.name)
                .getMethod(executionArguments.methodToRun!!.name, *executionArguments.methodToRun!!.parameterTypes)
            return@sandbox if (method.parameterTypes.isEmpty()) {
                method.invoke(null)
            } else {
                method.invoke(null, null)
            }
        } catch (e: InvocationTargetException) {
            throw (e.cause ?: e)
        }
    }
}

@Throws(ExecutionFailed::class)
@Suppress("ReturnCount")
suspend fun <T> CompiledSource.executeWith(
    executionArguments: SourceExecutionArguments = SourceExecutionArguments(),
    executor: (classLoader: Sandbox.SandboxedClassLoader) -> T,
): Sandbox.TaskResults<out T?> {
    val actualArguments = updateExecutionArguments(executionArguments, noFind = true)
    return Sandbox.execute(classLoader, actualArguments, actualArguments.plugins) sandbox@{ (classLoader) ->
        if (actualArguments.dryRun) {
            return@sandbox null
        }
        classLoader as Sandbox.SandboxedClassLoader
        @Suppress("SpreadOperator")
        try {
            return@sandbox executor(classLoader)
        } catch (e: InvocationTargetException) {
            throw (e.cause ?: e)
        }
    }
}

@Throws(ExecutionFailed::class)
@Suppress("ThrowsCount", "ComplexMethod")
fun ClassLoader.findClassMethod(
    klass: String? = null,
    name: String? = null,
    defaultKlass: String = SourceExecutionArguments.DEFAULT_KLASS,
    defaultMethod: String = SourceExecutionArguments.DEFAULT_METHOD,
): Method {
    this as Sandbox.EnumerableClassLoader
    val klassToLoad = if (klass == null && definedClasses.size == 1) {
        definedClasses.first()
    } else {
        klass ?: defaultKlass
    }
    try {
        val loadedKlass = loadClass(klassToLoad)
        val staticNoArgMethods = loadedKlass.declaredMethods.filter {
            Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers) && it.parameterTypes.isEmpty()
        }
        return if (name == null && staticNoArgMethods.size == 1) {
            staticNoArgMethods.first()
        } else {
            val nameToFind = name ?: defaultMethod
            loadedKlass.declaredMethods.filter {
                Modifier.isPublic(it.modifiers) &&
                    Modifier.isStatic(it.modifiers) &&
                    (
                        it.parameterTypes.isEmpty() ||
                            (it.parameterTypes.size == 1 && it.parameterTypes[0].canonicalName == "java.lang.String[]")
                        )
            }.find { method ->
                @Suppress("ComplexCondition")
                return@find if (method.getQualifiedName() == nameToFind) {
                    true
                } else {
                    method.name == "main" && (nameToFind == "main()" || nameToFind == "main(String[])")
                }
            } ?: throw ExecutionFailed.MethodNotFoundException(
                nameToFind,
                "Cannot locate public static no-argument method $name in $klassToLoad",
            )
        }
    } catch (methodNotFoundException: ExecutionFailed.MethodNotFoundException) {
        throw ExecutionFailed(methodNotFoundException)
    } catch (classNotFoundException: ClassNotFoundException) {
        throw ExecutionFailed(ExecutionFailed.ClassMissingException(klassToLoad, classNotFoundException.message))
    }
}

fun String.toSystemIn() = ByteArrayInputStream(toByteArray())
