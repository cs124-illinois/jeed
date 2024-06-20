@file:Suppress("SpellCheckingInspection")

package edu.illinois.cs.cs125.jeed.server

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import edu.illinois.cs.cs125.jeed.core.MoreCacheStats
import edu.illinois.cs.cs125.jeed.core.compilationCache
import edu.illinois.cs.cs125.jeed.core.compilationCacheSizeMB
import edu.illinois.cs.cs125.jeed.core.server.Task
import edu.illinois.cs.cs125.jeed.core.useCompilationCache
import java.net.InetAddress
import java.time.Instant
import edu.illinois.cs.cs125.jeed.core.systemCompilerName as COMPILER_NAME
import edu.illinois.cs.cs125.jeed.core.systemKompilerVersion as KOMPILER_VERSION
import edu.illinois.cs.cs125.jeed.core.version as JEED_VERSION

@JsonClass(generateAdapter = true)
@Suppress("MemberVisibilityCanBePrivate", "LongParameterList", "unused")
class Status(
    val tasks: Set<Task> = Task.entries.toSet(),
    val started: Instant = Instant.now(),
    val hostname: String = InetAddress.getLocalHost().hostName,
    var lastRequest: Instant? = null,
    val versions: Versions = Versions(JEED_VERSION, VERSION, COMPILER_NAME, KOMPILER_VERSION),
    val counts: Counts = Counts(),
    val cache: Cache = Cache(),
    val resources: Resources = Resources(),
) {
    @JsonClass(generateAdapter = true)
    data class Versions(val jeed: String, val server: String, val compiler: String, val kompiler: String)

    @JsonClass(generateAdapter = true)
    data class Counts(var submitted: Int = 0, var completed: Int = 0, var saved: Int = 0)

    @JsonClass(generateAdapter = true)
    data class Resources(
        val processors: Int = Runtime.getRuntime().availableProcessors(),
        val totalMemory: Long = Runtime.getRuntime().totalMemory() / 1024 / 1024,
        var freeMemory: Long = Runtime.getRuntime().freeMemory() / 1024 / 1024,
    )

    @JsonClass(generateAdapter = true)
    data class Cache(
        val inUse: Boolean = useCompilationCache,
        val sizeInMB: Long = compilationCacheSizeMB,
        var hits: Int = 0,
        var misses: Int = 0,
        var hitRate: Double = 0.0,
        var evictionCount: Long = 0,
        var averageLoadPenalty: Double = 0.0,
    )

    fun update(): Status {
        compilationCache.stats().also {
            cache.hits = MoreCacheStats.hits
            cache.misses = MoreCacheStats.misses
            cache.hitRate = it.hitRate()
            cache.evictionCount = it.evictionCount()
            cache.averageLoadPenalty = it.averageLoadPenalty()
        }
        resources.freeMemory = Runtime.getRuntime().freeMemory() / 1024 / 1024
        return this
    }

    fun toJson(): String = statusAdapter.indent("  ").toJson(this)

    companion object {
        private val statusAdapter: JsonAdapter<Status> = moshi.adapter(Status::class.java)
        fun from(response: String?): Status {
            check(response != null) { "can't deserialize null string" }
            return statusAdapter.fromJson(response) ?: error("failed to deserialize status")
        }
    }
}
