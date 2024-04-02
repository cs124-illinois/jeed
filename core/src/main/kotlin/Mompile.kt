package edu.illinois.cs.cs125.jeed.core

import java.time.Instant

private fun mompile(
    kompilationArguments: KompilationArguments,
    compilationArguments: CompilationArguments,
    source: Source,
    parentFileManager: JeedFileManager? = kompilationArguments.parentFileManager,
    parentClassLoader: ClassLoader? = kompilationArguments.parentClassLoader,
): CompiledSource {
    require(source.type == Source.SourceType.MIXED) { "Mixed compiler needs mixed sources" }

    val started = Instant.now()

    val (fileManager, kotlinMessages) = kompileToFileManager(
        kompilationArguments,
        source,
        parentFileManager = parentFileManager,
    )
    val (javaFileManager, javaMessages) = compileToFileManager(
        compilationArguments,
        source,
        parentFileManager = fileManager,
    )
    fileManager.merge(javaFileManager)

    val actualParentClassloader = if (compilationArguments.isolatedClassLoader) {
        IsolatingClassLoader(fileManager.classFiles.keys.map { pathToClassName(it) }.toSet())
    } else {
        parentClassLoader
    }

    return CompiledSource(
        source,
        kotlinMessages + javaMessages,
        started,
        Interval(started, Instant.now()),
        JeedClassLoader(fileManager, actualParentClassloader),
        fileManager,
    )
}

fun Source.mompile() = mompile(KompilationArguments(), CompilationArguments(), this)
