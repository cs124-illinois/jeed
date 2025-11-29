@file:Suppress("MatchingDeclarationName")

package edu.illinois.cs.cs125.jeed.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

const val JEED_DEFAULT_DISK_CACHE_SIZE_MB = 1024L

@Suppress("TooGenericExceptionCaught")
val diskCacheSizeMB = try {
    System.getenv("JEED_DISK_CACHE_SIZE")?.toLong() ?: JEED_DEFAULT_DISK_CACHE_SIZE_MB
} catch (_: Exception) {
    logger.warn("Bad value for JEED_DISK_CACHE_SIZE")
    JEED_DEFAULT_DISK_CACHE_SIZE_MB
}

const val JEED_DEFAULT_USE_DISK_CACHE = false

@Suppress("TooGenericExceptionCaught")
var useDiskCache = try {
    System.getenv("JEED_USE_DISK_CACHE")?.toBoolean() ?: JEED_DEFAULT_USE_DISK_CACHE
} catch (_: Exception) {
    logger.warn("Bad value for JEED_USE_DISK_CACHE")
    JEED_DEFAULT_USE_DISK_CACHE
}

val diskCacheDir: Path = try {
    System.getenv("JEED_DISK_CACHE_DIR")?.let { Path.of(it) }
        ?: Path.of(System.getProperty("java.io.tmpdir"), "jeed-cache")
} catch (_: Exception) {
    logger.warn("Bad value for JEED_DISK_CACHE_DIR")
    Path.of(System.getProperty("java.io.tmpdir"), "jeed-cache")
}

const val JEED_DEFAULT_DISK_CACHE_LOW_WATER_MARK = 0.75

@Suppress("TooGenericExceptionCaught")
val diskCacheLowWaterMark = try {
    System.getenv("JEED_DISK_CACHE_LOW_WATER_MARK")?.toDouble() ?: JEED_DEFAULT_DISK_CACHE_LOW_WATER_MARK
} catch (_: Exception) {
    logger.warn("Bad value for JEED_DISK_CACHE_LOW_WATER_MARK")
    JEED_DEFAULT_DISK_CACHE_LOW_WATER_MARK
}

/**
 * Manages disk-based compilation cache with LRU eviction.
 * Tracks cache size in memory to avoid expensive filesystem scans.
 * Uses a low water mark strategy: when evicting, evict down to the configured
 * percentage of capacity (default 75%) to avoid triggering eviction on every
 * subsequent write. Eviction runs in a background coroutine to avoid blocking writes.
 */
class DiskCompilationCache(
    private val cacheDir: Path = diskCacheDir,
    private val maxSizeBytes: Long = diskCacheSizeMB * MB_TO_BYTES,
    private val lowWaterMarkRatio: Double = diskCacheLowWaterMark,
) {
    private val currentSizeBytes = AtomicLong(0)
    private val lowWaterMarkBytes: Long = (maxSizeBytes * lowWaterMarkRatio).toLong()
    private val evictionInProgress = AtomicBoolean(false)
    private val evictionScope = CoroutineScope(Dispatchers.IO)

    init {
        Files.createDirectories(cacheDir)
        // Calculate initial cache size on startup
        currentSizeBytes.set(computeCurrentSize())
    }

    private fun getCachePath(key: String): Path = cacheDir.resolve("$key.cache")

    /**
     * Computes the current size of all cache files.
     * Only called on initialization and optionally for periodic correction.
     */
    private fun computeCurrentSize(): Long = try {
        Files.list(cacheDir).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".cache") }
                .mapToLong { Files.size(it) }
                .sum()
        }
    } catch (e: Exception) {
        logger.warn("Failed to compute cache size: ${e.message}")
        0L
    }

    /**
     * Retrieves a cached compilation result from disk.
     */
    fun get(key: String): CachedCompilationResults? {
        val path = getCachePath(key)
        val result = DiskCacheSerializer.read(path, key)

        // Update access time for LRU
        if (result != null) {
            try {
                Files.setLastModifiedTime(path, java.nio.file.attribute.FileTime.from(Instant.now()))
            } catch (e: Exception) {
                logger.warn("Failed to update access time for cache entry: ${e.message}")
            }
        }

        return result
    }

    /**
     * Stores a compilation result to disk.
     * Triggers background eviction if cache exceeds limit, but does not block.
     */
    fun put(key: String, results: CachedCompilationResults) {
        try {
            val path = getCachePath(key)
            val existingSize = if (Files.exists(path)) Files.size(path) else 0L

            DiskCacheSerializer.write(path, key, results)

            val newSize = Files.size(path)
            currentSizeBytes.addAndGet(newSize - existingSize)

            // Launch background eviction if needed (non-blocking)
            if (currentSizeBytes.get() > maxSizeBytes && evictionInProgress.compareAndSet(false, true)) {
                evictionScope.launch {
                    try {
                        evictIfNeeded()
                    } finally {
                        evictionInProgress.set(false)
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to write disk cache entry: ${e.message}")
        }
    }

    /**
     * Evicts least recently used entries if cache size exceeds limit.
     * Updates the in-memory size tracker as files are deleted.
     * Evicts down to the low water mark (default 75% of capacity) to avoid
     * needing to evict on every subsequent write.
     * Runs in a background coroutine to avoid blocking writes.
     */
    private fun evictIfNeeded() {
        try {
            val cacheFiles = Files.list(cacheDir).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".cache") }
                    .toList()
            }

            // Sort by last access time (oldest first)
            val sortedFiles = cacheFiles.sortedBy {
                Files.getLastModifiedTime(it).toMillis()
            }

            // Evict down to low water mark to create breathing room
            for (file in sortedFiles) {
                if (currentSizeBytes.get() <= lowWaterMarkBytes) {
                    break
                }
                val fileSize = Files.size(file)
                if (Files.deleteIfExists(file)) {
                    currentSizeBytes.addAndGet(-fileSize)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to evict cache entries: ${e.message}")
        }
    }

    /**
     * Clears all entries from the disk cache.
     */
    fun clear() {
        try {
            Files.list(cacheDir).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".cache") }
                    .forEach { Files.deleteIfExists(it) }
            }
            currentSizeBytes.set(0)
        } catch (e: Exception) {
            logger.warn("Failed to clear disk cache: ${e.message}")
        }
    }

    /**
     * Returns the current size of the disk cache in bytes.
     * Uses the in-memory tracked size for efficiency.
     */
    fun size(): Long = currentSizeBytes.get()
}

/**
 * Serialization/deserialization for disk-based compilation cache.
 */
object DiskCacheSerializer {
    private const val CACHE_VERSION = 1

    /**
     * Serializes a CachedCompilationResults to disk.
     */
    fun write(path: Path, key: String, results: CachedCompilationResults) {
        val tempFile = Files.createTempFile(path.parent, ".cache-", ".tmp")
        try {
            DataOutputStream(FileOutputStream(tempFile.toFile())).use { output ->
                // Write version for future compatibility
                output.writeInt(CACHE_VERSION)

                // Write cache key
                output.writeUTF(key)

                // Write compilation timestamp
                output.writeLong(results.compiled.toEpochMilli())

                // Write compiler name
                output.writeUTF(results.compilerName)

                // Write compilation messages
                output.writeInt(results.messages.size)
                results.messages.forEach { message ->
                    output.writeUTF(message.kind)
                    output.writeUTF(message.message)
                    output.writeBoolean(message.location != null)
                    message.location?.let { location ->
                        output.writeUTF(location.source)
                        output.writeInt(location.line)
                        output.writeInt(location.column)
                    }
                }

                // Write file manager contents (bytecode)
                // Only serialize classes defined in this compilation, not parent classes
                val classFiles = results.fileManager.classFiles
                output.writeInt(classFiles.size)
                classFiles.forEach { (path, fileObj) ->
                    output.writeUTF(path)
                    val bytecode = fileObj.openInputStream().readAllBytes()
                    output.writeInt(bytecode.size)
                    output.write(bytecode)
                }

                // Write compilation arguments if present
                output.writeBoolean(results.compilationArguments != null)
                results.compilationArguments?.let { args ->
                    output.writeInt(args.hashCode())
                }

                // Write kompilation arguments if present
                output.writeBoolean(results.kompilationArguments != null)
                results.kompilationArguments?.let { args ->
                    output.writeInt(args.hashCode())
                }

                // Write failed compilation errors if present
                output.writeBoolean(results.failedCompilationErrors != null)
                results.failedCompilationErrors?.let { errors ->
                    output.writeInt(errors.size)
                    errors.forEach { error ->
                        output.writeUTF(error.message)
                        output.writeBoolean(error.location != null)
                        error.location?.let { location ->
                            output.writeUTF(location.source)
                            output.writeInt(location.line)
                            output.writeInt(location.column)
                        }
                    }
                }
            }

            // Atomic move to final location
            Files.move(tempFile, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            Files.deleteIfExists(tempFile)
            throw e
        }
    }

    /**
     * Deserializes a CachedCompilationResults from disk.
     * Returns null if the file doesn't exist, is corrupted, or has an incompatible version.
     */
    fun read(path: Path, expectedKey: String): CachedCompilationResults? {
        if (!Files.exists(path)) {
            return null
        }

        return try {
            DataInputStream(FileInputStream(path.toFile())).use { input ->
                // Read and verify version
                val version = input.readInt()
                if (version != CACHE_VERSION) {
                    return null
                }

                // Read and verify cache key
                val key = input.readUTF()
                if (key != expectedKey) {
                    return null
                }

                // Read compilation timestamp
                val compiled = Instant.ofEpochMilli(input.readLong())

                // Read compiler name
                val compilerName = input.readUTF()

                // Read compilation messages
                val messageCount = input.readInt()
                val messages = (0 until messageCount).map {
                    val kind = input.readUTF()
                    val message = input.readUTF()
                    val hasLocation = input.readBoolean()
                    val location = if (hasLocation) {
                        SourceLocation(
                            input.readUTF(),
                            input.readInt(),
                            input.readInt(),
                        )
                    } else {
                        null
                    }
                    CompilationMessage(kind, location, message)
                }

                // Read file manager contents (bytecode)
                val classFileCount = input.readInt()
                val classFiles = mutableMapOf<String, ByteArray>()
                repeat(classFileCount) {
                    val path = input.readUTF()
                    val size = input.readInt()
                    val bytecode = ByteArray(size)
                    input.readFully(bytecode)
                    classFiles[path] = bytecode
                }

                // Create a new JeedFileManager and populate it with the bytecode
                val fileManager = JeedFileManager(standardFileManager)
                classFiles.forEach { (path, bytecode) ->
                    fileManager.addClass(path, bytecode)
                }

                // Read compilation arguments
                val hasCompilationArguments = input.readBoolean()
                val compilationArgumentsHash = if (hasCompilationArguments) {
                    input.readInt()
                } else {
                    null
                }

                // Read kompilation arguments
                val hasKompilationArguments = input.readBoolean()
                val kompilationArgumentsHash = if (hasKompilationArguments) {
                    input.readInt()
                } else {
                    null
                }

                // Read failed compilation errors if present
                val hasFailedCompilationErrors = input.readBoolean()
                val failedCompilationErrors = if (hasFailedCompilationErrors) {
                    val errorCount = input.readInt()
                    (0 until errorCount).map {
                        val message = input.readUTF()
                        val hasLocation = input.readBoolean()
                        val location = if (hasLocation) {
                            SourceLocation(
                                input.readUTF(),
                                input.readInt(),
                                input.readInt(),
                            )
                        } else {
                            null
                        }
                        CompilationError(location, message)
                    }
                } else {
                    null
                }

                // Note: We can't fully reconstruct the compilation/kompilation arguments from disk,
                // so we store null and rely on the cache key verification to ensure correctness.
                // The hash codes are stored for debugging purposes.
                CachedCompilationResults(
                    compiled = compiled,
                    messages = messages,
                    fileManager = fileManager,
                    compilerName = compilerName,
                    compilationArguments = null,
                    kompilationArguments = null,
                    failedCompilationErrors = failedCompilationErrors,
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to read disk cache entry: ${e.message}")
            null
        }
    }

    /**
     * Deletes a cache entry from disk.
     */
    fun delete(path: Path) {
        Files.deleteIfExists(path)
    }
}
