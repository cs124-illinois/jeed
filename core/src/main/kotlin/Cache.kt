@file:Suppress("MatchingDeclarationName")

package edu.illinois.cs.cs125.jeed.core

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale

const val JEED_DEFAULT_COMPILATION_CACHE_SIZE_MB = 256L

@Suppress("TooGenericExceptionCaught")
val compilationCacheSizeMB = try {
    System.getenv("JEED_COMPILATION_CACHE_SIZE")?.toLong() ?: JEED_DEFAULT_COMPILATION_CACHE_SIZE_MB
} catch (_: Exception) {
    logger.warn("Bad value for JEED_COMPILATION_CACHE_SIZE")
    JEED_DEFAULT_COMPILATION_CACHE_SIZE_MB
}

const val JEED_DEFAULT_USE_CACHE = false

@Suppress("TooGenericExceptionCaught")
val useCompilationCache = try {
    System.getenv("JEED_USE_CACHE")?.toBoolean() ?: JEED_DEFAULT_USE_CACHE
} catch (_: Exception) {
    logger.warn("Bad value for JEED_USE_CACHE")
    JEED_DEFAULT_USE_CACHE
}

@Suppress("TooGenericExceptionCaught")
val logDiskCacheMisses = try {
    System.getenv("JEED_LOG_DISK_CACHE_MISSES")?.toBoolean() ?: false
} catch (_: Exception) {
    logger.warn("Bad value for JEED_LOG_DISK_CACHE_MISSES")
    false
}

const val MB_TO_BYTES = 1024 * 1024
val compilationCache: Cache<String, CachedCompilationResults> =
    Caffeine.newBuilder()
        .maximumWeight(compilationCacheSizeMB * MB_TO_BYTES)
        .weigher<String, CachedCompilationResults> { _, cachedCompilationResults ->
            cachedCompilationResults.fileManager.size
        }
        .recordStats()
        .build()

var diskCompilationCache: DiskCompilationCache = DiskCompilationCache()

class CachedCompilationResults(
    val compiled: Instant,
    val messages: List<CompilationMessage>,
    val fileManager: JeedFileManager,
    val compilerName: String,
    val compilationArguments: CompilationArguments? = null,
    val kompilationArguments: KompilationArguments? = null,
)

fun JeedFileManager.bytecodeHash(): String = MessageDigest.getInstance("MD5").let { digest ->
    allClassFiles.toSortedMap().forEach { (path, fileObj) ->
        digest.update(path.toByteArray())
        digest.update(fileObj.openInputStream().readAllBytes())
    }
    digest.digest()
}.joinToString(separator = "") {
    String.format(Locale.US, "%02x", it)
}

fun Source.computeCacheKey(
    compilationArguments: CompilationArguments,
    compilerName: String,
): String = MessageDigest.getInstance("SHA-256").let { digest ->
    // Source content
    digest.update(md5.toByteArray())

    // Compiler version
    digest.update(compilerName.toByteArray())

    // Parent bytecode if present
    val parent = compilationArguments.parentFileManager as? JeedFileManager
    parent?.let {
        digest.update(it.bytecodeHash().toByteArray())
    }

    // Compilation arguments
    digest.update(compilationArguments.hashCode().toString().toByteArray())

    digest.digest()
}.joinToString(separator = "") {
    String.format(Locale.US, "%02x", it)
}

fun Source.computeCacheKey(
    kompilationArguments: KompilationArguments,
    compilerName: String,
): String = MessageDigest.getInstance("SHA-256").let { digest ->
    // Source content
    digest.update(md5.toByteArray())

    // Compiler version
    digest.update(compilerName.toByteArray())

    // Parent bytecode if present
    kompilationArguments.parentFileManager?.let {
        digest.update(it.bytecodeHash().toByteArray())
    }

    // Kompilation arguments
    digest.update(kompilationArguments.hashCode().toString().toByteArray())

    digest.digest()
}.joinToString(separator = "") {
    String.format(Locale.US, "%02x", it)
}

object JeedCacheStats {
    var l1Hits: Int = 0
    var l2Hits: Int = 0
    var misses: Int = 0

    val totalHits: Int
        get() = l1Hits + l2Hits

    val diskCacheSizeBytes: Long
        get() = diskCompilationCache.size()

    fun reset() {
        l1Hits = 0
        l2Hits = 0
        misses = 0
    }
}

@Suppress("ReturnCount")
fun Source.tryCache(
    compilationArguments: CompilationArguments,
    started: Instant,
    compilerName: String,
): CompiledSource? {
    val useCache = compilationArguments.useCache ?: useCompilationCache
    if (!useCache) {
        return null
    }
    val cacheKey = computeCacheKey(compilationArguments, compilerName)

    // Try memory cache first
    var cachedResult = compilationCache.getIfPresent(cacheKey)
    val hitL1 = cachedResult != null

    // If not in memory, try disk cache
    if (cachedResult == null && useDiskCache) {
        cachedResult = diskCompilationCache.get(cacheKey)
        if (cachedResult != null) {
            // Restore to memory cache
            compilationCache.put(cacheKey, cachedResult)
            JeedCacheStats.l2Hits++
        }
    }

    if (cachedResult == null) {
        // Don't count as miss yet - only count if compilation succeeds and gets cached
        if (useDiskCache && logDiskCacheMisses) {
            val sourceInfo = sources.entries.joinToString("\n") { (path, content) ->
                "$path:\n$content"
            }
            val stackTrace = Thread.currentThread().stackTrace.drop(1).joinToString("\n") { "  at $it" }
            logger.warn("Disk cache miss: key=$cacheKey\n$sourceInfo\nStack trace:\n$stackTrace")
        }
        return null
    }
    if (hitL1) {
        JeedCacheStats.l1Hits++
    }

    // Verify compiler name matches
    if (cachedResult.compilerName != compilerName) {
        return null
    }

    // If compilationArguments is present (memory cache hit), verify it matches
    // If null (disk cache hit), cache key already verified correctness
    if (cachedResult.compilationArguments != null && cachedResult.compilationArguments != compilationArguments) {
        return null
    }
    val parentClassLoader =
        compilationArguments.parentClassLoader ?: ClassLoader.getSystemClassLoader()
    return CompiledSource(
        this,
        cachedResult.messages,
        cachedResult.compiled,
        Interval(started, Instant.now()),
        JeedClassLoader(cachedResult.fileManager, parentClassLoader),
        cachedResult.fileManager,
        compilerName,
        true,
    )
}

private val compilationScope = CoroutineScope(Dispatchers.IO)
fun CompiledSource.cache(compilationArguments: CompilationArguments) {
    val useCache = compilationArguments.useCache ?: useCompilationCache
    if (cached || !useCache) {
        return
    }

    // Count as cache miss - this only runs for successful compilations that weren't cached
    JeedCacheStats.misses++

    val cacheKey = source.computeCacheKey(compilationArguments, compilerName)
    val cachedResults = CachedCompilationResults(
        compiled,
        messages,
        fileManager,
        compilerName,
        compilationArguments = compilationArguments,
    )

    compilationScope.launch {
        // Write to memory cache
        compilationCache.put(cacheKey, cachedResults)

        // Write to disk cache if enabled
        if (useDiskCache) {
            diskCompilationCache.put(cacheKey, cachedResults)
        }
    }.also {
        if (compilationArguments.waitForCache) {
            runBlocking { it.join() }
        }
    }
}

@Suppress("ReturnCount")
fun Source.tryCache(
    kompilationArguments: KompilationArguments,
    started: Instant,
    compilerName: String,
): CompiledSource? {
    val useCache = kompilationArguments.useCache ?: useCompilationCache
    if (!useCache) {
        return null
    }
    val cacheKey = computeCacheKey(kompilationArguments, compilerName)

    // Try memory cache first
    var cachedResult = compilationCache.getIfPresent(cacheKey)
    val hitL1 = cachedResult != null

    // If not in memory, try disk cache
    if (cachedResult == null && useDiskCache) {
        cachedResult = diskCompilationCache.get(cacheKey)
        if (cachedResult != null) {
            // Restore to memory cache
            compilationCache.put(cacheKey, cachedResult)
            JeedCacheStats.l2Hits++
        }
    }

    if (cachedResult == null) {
        // Don't count as miss yet - only count if compilation succeeds and gets cached
        if (useDiskCache && logDiskCacheMisses) {
            val sourceInfo = sources.entries.joinToString("\n") { (path, content) ->
                "$path:\n$content"
            }
            val stackTrace = Thread.currentThread().stackTrace.drop(1).joinToString("\n") { "  at $it" }
            logger.warn("Disk cache miss: key=$cacheKey\n$sourceInfo\nStack trace:\n$stackTrace")
        }
        return null
    }
    if (hitL1) {
        JeedCacheStats.l1Hits++
    }

    // Verify compiler name matches
    if (cachedResult.compilerName != compilerName) {
        return null
    }

    // If kompilationArguments is present (memory cache hit), verify it matches
    // If null (disk cache hit), cache key already verified correctness
    if (cachedResult.kompilationArguments != null && cachedResult.kompilationArguments != kompilationArguments) {
        return null
    }
    return CompiledSource(
        this,
        cachedResult.messages,
        cachedResult.compiled,
        Interval(started, Instant.now()),
        JeedClassLoader(cachedResult.fileManager, kompilationArguments.parentClassLoader),
        cachedResult.fileManager,
        compilerName,
        true,
    )
}

fun CompiledSource.cache(kompilationArguments: KompilationArguments) {
    val useCache = kompilationArguments.useCache ?: useCompilationCache
    if (cached || !useCache) {
        return
    }

    // Count as cache miss - this only runs for successful compilations that weren't cached
    JeedCacheStats.misses++

    val cacheKey = source.computeCacheKey(kompilationArguments, compilerName)
    val cachedResults = CachedCompilationResults(
        compiled,
        messages,
        fileManager,
        compilerName,
        kompilationArguments = kompilationArguments,
    )

    compilationScope.launch {
        // Write to memory cache
        compilationCache.put(cacheKey, cachedResults)

        // Write to disk cache if enabled
        if (useDiskCache) {
            diskCompilationCache.put(cacheKey, cachedResults)
        }
    }.also {
        if (kompilationArguments.waitForCache) {
            runBlocking { it.join() }
        }
    }
}
