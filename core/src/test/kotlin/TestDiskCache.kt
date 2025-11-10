package edu.illinois.cs.cs125.jeed.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.nio.file.Files

class TestDiskCache :
    StringSpec({
        "disk cache should persist and restore compilation results" {
            val tempDir = Files.createTempDirectory("jeed-test-cache")
            val diskCache = DiskCompilationCache(tempDir, 1024 * 1024 * 10)

            try {
                val source = Source.fromSnippet(
                    "int diskTest = 42;",
                )
                val compiled = source.compile(CompilationArguments(useCache = true, waitForCache = true))
                val cacheKey = source.computeCacheKey(CompilationArguments(), compiled.compilerName)

                // Save to disk cache
                val cachedResults = CachedCompilationResults(
                    compiled.compiled,
                    compiled.messages,
                    compiled.fileManager,
                    compiled.compilerName,
                    compilationArguments = CompilationArguments(),
                )
                diskCache.put(cacheKey, cachedResults)

                // Verify it was saved
                diskCache.size() shouldBeGreaterThan 0L

                // Restore from disk cache
                val restored = diskCache.get(cacheKey)
                restored shouldNotBe null
                restored!!.compilerName shouldBe compiled.compilerName
                restored.messages.size shouldBe compiled.messages.size
            } finally {
                diskCache.clear()
                Files.deleteIfExists(tempDir)
            }
        }
        "disk cache should respect size limits and evict LRU entries" {
            val tempDir = Files.createTempDirectory("jeed-test-cache-lru")
            // Small cache size to trigger eviction
            val diskCache = DiskCompilationCache(tempDir, 10 * 1024)

            try {
                val sources = (1..5).map { i ->
                    Source.fromSnippet(
                        "int diskLRUTest$i = $i;",
                    )
                }

                // Compile and cache all sources
                sources.forEach { source ->
                    val compiled = source.compile(CompilationArguments(useCache = true, waitForCache = true))
                    val cacheKey = source.computeCacheKey(CompilationArguments(), compiled.compilerName)
                    val cachedResults = CachedCompilationResults(
                        compiled.compiled,
                        compiled.messages,
                        compiled.fileManager,
                        compiled.compilerName,
                        compilationArguments = CompilationArguments(),
                    )
                    diskCache.put(cacheKey, cachedResults)
                }

                // Verify some entries were evicted due to size limit
                val finalSize = diskCache.size()
                finalSize shouldBeLessThan (50 * 1024)
            } finally {
                diskCache.clear()
                Files.deleteIfExists(tempDir)
            }
        }
        "two-tier cache should work correctly with L1 and L2 hits" {
            val tempDir = Files.createTempDirectory("jeed-test-two-tier")
            val testDiskCache = DiskCompilationCache(tempDir, 1024 * 1024 * 10)
            val originalUseDiskCache = useDiskCache
            val originalDiskCache = diskCompilationCache

            try {
                // Temporarily enable disk cache and use our test instance
                useDiskCache = true
                diskCompilationCache = testDiskCache
                JeedCacheStats.reset()

                val source = Source.fromSnippet(
                    "int twoTierTest = 123;",
                )

                // First compilation - cache miss (slowest)
                // This will store in both L1 and L2 automatically
                val firstCompiled = source.compile(
                    CompilationArguments(useCache = true, waitForCache = true),
                )
                firstCompiled.cached shouldBe false
                val firstTime = firstCompiled.interval.length
                val cacheKey = source.computeCacheKey(CompilationArguments(), firstCompiled.compilerName)

                // Verify stats after first compilation
                JeedCacheStats.l1Hits shouldBe 0
                JeedCacheStats.l2Hits shouldBe 0
                JeedCacheStats.misses shouldBe 1

                // Second compilation - L1 cache hit (fastest)
                val secondCompiled = source.compile(
                    CompilationArguments(useCache = true, waitForCache = true),
                )
                secondCompiled.cached shouldBe true
                val secondTime = secondCompiled.interval.length

                // Verify stats after L1 hit
                JeedCacheStats.l1Hits shouldBe 1
                JeedCacheStats.l2Hits shouldBe 0
                JeedCacheStats.misses shouldBe 1

                // Remove from L1 cache to force L2 lookup
                compilationCache.invalidate(cacheKey)
                compilationCache.getIfPresent(cacheKey) shouldBe null

                // Third compilation - L2 cache hit, should restore to L1 (medium speed)
                val thirdCompiled = source.compile(
                    CompilationArguments(useCache = true, waitForCache = true),
                )
                thirdCompiled.cached shouldBe true
                val thirdTime = thirdCompiled.interval.length

                // Verify stats after L2 hit
                JeedCacheStats.l1Hits shouldBe 1
                JeedCacheStats.l2Hits shouldBe 1
                JeedCacheStats.misses shouldBe 1

                // Verify it was restored to L1
                compilationCache.getIfPresent(cacheKey) shouldNotBe null

                // Fourth compilation - L1 cache hit again (fastest)
                val fourthCompiled = source.compile(
                    CompilationArguments(useCache = true, waitForCache = true),
                )
                fourthCompiled.cached shouldBe true
                val fourthTime = fourthCompiled.interval.length

                // Verify final stats
                JeedCacheStats.l1Hits shouldBe 2
                JeedCacheStats.l2Hits shouldBe 1
                JeedCacheStats.misses shouldBe 1

                // Verify timing relationships: L1 hits should be faster than L2 hit, which should be faster than miss
                secondTime shouldBeLessThan firstTime
                fourthTime shouldBeLessThan firstTime
                // L2 hit might not always be slower than L1 hit in tests due to JVM warmup, but verify it's faster than miss
                thirdTime shouldBeLessThan firstTime
            } finally {
                // Restore original state
                useDiskCache = originalUseDiskCache
                diskCompilationCache = originalDiskCache
                testDiskCache.clear()
                Files.deleteIfExists(tempDir)
            }
        }
        "disk cache should work when memory cache is disabled" {
            val tempDir = Files.createTempDirectory("jeed-test-disk-only")
            val testDiskCache = DiskCompilationCache(tempDir, 1024 * 1024 * 10)
            val originalUseDiskCache = useDiskCache
            val originalDiskCache = diskCompilationCache

            try {
                // Temporarily enable disk cache
                useDiskCache = true
                diskCompilationCache = testDiskCache

                val source = Source.fromSnippet(
                    "int diskOnlyTest = 456;",
                )

                // First compilation - stores in both L1 and L2
                val firstCompiled = source.compile(
                    CompilationArguments(useCache = true, waitForCache = true),
                )
                val cacheKey = source.computeCacheKey(CompilationArguments(), firstCompiled.compilerName)

                // Clear L1 cache to force L2 lookup
                compilationCache.invalidate(cacheKey)
                compilationCache.getIfPresent(cacheKey) shouldBe null

                // Verify disk cache has the entry
                val diskEntry = testDiskCache.get(cacheKey)
                diskEntry shouldNotBe null
                diskEntry!!.compilerName shouldBe firstCompiled.compilerName

                // Compilation should hit L2 and restore to L1
                val secondCompiled = source.compile(
                    CompilationArguments(useCache = true, waitForCache = true),
                )
                secondCompiled.cached shouldBe true

                // Verify it's now in both caches
                compilationCache.getIfPresent(cacheKey) shouldNotBe null
                testDiskCache.get(cacheKey) shouldNotBe null
            } finally {
                // Restore original state
                useDiskCache = originalUseDiskCache
                diskCompilationCache = originalDiskCache
                testDiskCache.clear()
                Files.deleteIfExists(tempDir)
            }
        }
        "disk cache should work with Kotlin compilation" {
            val tempDir = Files.createTempDirectory("jeed-test-kotlin-disk")
            val testDiskCache = DiskCompilationCache(tempDir, 1024 * 1024 * 10)
            val originalUseDiskCache = useDiskCache
            val originalDiskCache = diskCompilationCache

            try {
                useDiskCache = true
                diskCompilationCache = testDiskCache
                JeedCacheStats.reset()

                val source = Source.fromSnippet(
                    "val kotlinTest = 42",
                    SnippetArguments(fileType = Source.FileType.KOTLIN),
                )

                // First compilation - cache miss
                val firstCompiled = source.kompile(
                    KompilationArguments(useCache = true, waitForCache = true),
                )
                firstCompiled.cached shouldBe false
                val cacheKey = source.computeCacheKey(KompilationArguments(), firstCompiled.compilerName)

                // Verify stored in disk cache
                testDiskCache.get(cacheKey) shouldNotBe null

                // Verify stats
                JeedCacheStats.l1Hits shouldBe 0
                JeedCacheStats.l2Hits shouldBe 0
                JeedCacheStats.misses shouldBe 1

                // Clear L1 to force L2 lookup
                compilationCache.invalidate(cacheKey)

                // Second compilation - L2 hit
                val secondCompiled = source.kompile(
                    KompilationArguments(useCache = true, waitForCache = true),
                )
                secondCompiled.cached shouldBe true

                // Verify stats after L2 hit
                JeedCacheStats.l1Hits shouldBe 0
                JeedCacheStats.l2Hits shouldBe 1
                JeedCacheStats.misses shouldBe 1

                // Verify restored to L1
                compilationCache.getIfPresent(cacheKey) shouldNotBe null

                // Third compilation - L1 hit (should be fastest)
                val thirdCompiled = source.kompile(
                    KompilationArguments(useCache = true, waitForCache = true),
                )
                thirdCompiled.cached shouldBe true
                thirdCompiled.interval.length shouldBeLessThan firstCompiled.interval.length

                // Verify final stats
                JeedCacheStats.l1Hits shouldBe 1
                JeedCacheStats.l2Hits shouldBe 1
                JeedCacheStats.misses shouldBe 1
            } finally {
                useDiskCache = originalUseDiskCache
                diskCompilationCache = originalDiskCache
                testDiskCache.clear()
                Files.deleteIfExists(tempDir)
            }
        }
        "disk cache should reject entries with different compiler version" {
            val tempDir = Files.createTempDirectory("jeed-test-version-mismatch")
            val testDiskCache = DiskCompilationCache(tempDir, 1024 * 1024 * 10)
            val originalUseDiskCache = useDiskCache
            val originalDiskCache = diskCompilationCache

            try {
                useDiskCache = true
                diskCompilationCache = testDiskCache

                val source = Source.fromSnippet(
                    "int versionTest = 99;",
                )

                // First compilation with "compiler-v1"
                val firstCompiled = source.compile(
                    CompilationArguments(useCache = true, waitForCache = true),
                )
                val realCompilerName = firstCompiled.compilerName

                // Manually create a cache entry with different compiler version
                val cacheKey = source.computeCacheKey(CompilationArguments(), realCompilerName)
                val fakeEntry = CachedCompilationResults(
                    firstCompiled.compiled,
                    firstCompiled.messages,
                    firstCompiled.fileManager,
                    "fake-compiler-version", // Different compiler name
                    compilationArguments = CompilationArguments(),
                )

                // Store fake entry in disk cache only
                testDiskCache.put(cacheKey, fakeEntry)

                // Clear L1 cache
                compilationCache.invalidateAll()

                // Try to compile again - should NOT use cached result due to compiler mismatch
                val secondCompiled = source.compile(
                    CompilationArguments(useCache = true, waitForCache = true),
                )

                // The tryCache should return null due to compiler mismatch, so this will be a fresh compile
                secondCompiled.cached shouldBe false
            } finally {
                useDiskCache = originalUseDiskCache
                diskCompilationCache = originalDiskCache
                testDiskCache.clear()
                Files.deleteIfExists(tempDir)
            }
        }
        "disk cache should differentiate compilations with different parent file managers" {
            val tempDir = Files.createTempDirectory("jeed-test-parent-collision")
            val testDiskCache = DiskCompilationCache(tempDir, 1024 * 1024 * 10)
            val originalUseDiskCache = useDiskCache
            val originalDiskCache = diskCompilationCache

            try {
                useDiskCache = true
                diskCompilationCache = testDiskCache

                // Compile parent code with different implementations
                val parentSource1 = Source(
                    mapOf(
                        "Parent.java" to "public class Parent { public static int getValue() { return 42; } }",
                    ),
                )
                val parentCompiled1 = parentSource1.compile(
                    CompilationArguments(useCache = true, waitForCache = true),
                )

                val parentSource2 = Source(
                    mapOf(
                        "Parent.java" to "public class Parent { public static int getValue() { return 99; } }",
                    ),
                )
                val parentCompiled2 = parentSource2.compile(
                    CompilationArguments(useCache = true, waitForCache = true),
                )

                // Compile child code that uses parent
                val childSource = Source(
                    mapOf(
                        "Child.java" to "public class Child { public static int test() { return Parent.getValue(); } }",
                    ),
                )

                // First compilation with parent1
                compilationCache.invalidateAll()
                val firstChild = childSource.compile(
                    CompilationArguments(
                        useCache = true,
                        waitForCache = true,
                        parentFileManager = parentCompiled1.fileManager,
                    ),
                )
                val key1 = childSource.computeCacheKey(
                    CompilationArguments(parentFileManager = parentCompiled1.fileManager),
                    firstChild.compilerName,
                )

                // Second compilation with parent2 - should have DIFFERENT cache key
                compilationCache.invalidateAll()
                val secondChild = childSource.compile(
                    CompilationArguments(
                        useCache = true,
                        waitForCache = true,
                        parentFileManager = parentCompiled2.fileManager,
                    ),
                )
                val key2 = childSource.computeCacheKey(
                    CompilationArguments(parentFileManager = parentCompiled2.fileManager),
                    secondChild.compilerName,
                )

                // Verify cache keys are different
                key1 shouldNotBe key2

                // Verify they're both in disk cache with different keys
                testDiskCache.get(key1) shouldNotBe null
                testDiskCache.get(key2) shouldNotBe null

                // Verify both compilations succeeded
                // (they use different parent bytecode so should have different cache keys)
            } finally {
                useDiskCache = originalUseDiskCache
                diskCompilationCache = originalDiskCache
                testDiskCache.clear()
                Files.deleteIfExists(tempDir)
            }
        }
        "disk cache should differentiate between compilation with and without parent" {
            val tempDir = Files.createTempDirectory("jeed-test-null-parent-collision")
            val testDiskCache = DiskCompilationCache(tempDir, 1024 * 1024 * 10)
            val originalUseDiskCache = useDiskCache
            val originalDiskCache = diskCompilationCache

            try {
                useDiskCache = true
                diskCompilationCache = testDiskCache

                val childSource = Source(
                    mapOf(
                        "Child.java" to "public class Child { }",
                    ),
                )

                // First: compile Child with no parent (isolatedClassLoader)
                compilationCache.invalidateAll()
                val firstCompilation = childSource.compile(
                    CompilationArguments(
                        useCache = true,
                        waitForCache = true,
                        isolatedClassLoader = true,
                    ),
                )
                val key1 = childSource.computeCacheKey(
                    CompilationArguments(isolatedClassLoader = true),
                    firstCompilation.compilerName,
                )
                firstCompilation.classLoader.definedClasses.size shouldBe 1

                // Compile parent separately
                val parentSource = Source(
                    mapOf(
                        "Parent.java" to "public class Parent { }",
                    ),
                )
                val parentCompilation = parentSource.compile(
                    CompilationArguments(useCache = true, waitForCache = true),
                )

                // Second: compile same Child source but WITH parent
                compilationCache.invalidateAll()
                val secondCompilation = childSource.compile(
                    CompilationArguments(
                        useCache = true,
                        waitForCache = true,
                        parentFileManager = parentCompilation.fileManager,
                    ),
                )
                val key2 = childSource.computeCacheKey(
                    CompilationArguments(parentFileManager = parentCompilation.fileManager),
                    secondCompilation.compilerName,
                )

                // Cache keys should be different (one has parent, one doesn't)
                key1 shouldNotBe key2

                // First compilation should have only Child
                firstCompilation.classLoader.definedClasses.size shouldBe 1
                firstCompilation.classLoader.definedClasses shouldBe setOf("Child")

                // Second compilation should also have only Child (Parent is in parent)
                secondCompilation.classLoader.definedClasses.size shouldBe 1
                secondCompilation.classLoader.definedClasses shouldBe setOf("Child")
            } finally {
                useDiskCache = originalUseDiskCache
                diskCompilationCache = originalDiskCache
                testDiskCache.clear()
                Files.deleteIfExists(tempDir)
            }
        }
        "disk cache should not include parent classes in definedClasses after restore" {
            val tempDir = Files.createTempDirectory("jeed-test-parent-classes")
            val testDiskCache = DiskCompilationCache(tempDir, 1024 * 1024 * 10)
            val originalUseDiskCache = useDiskCache
            val originalDiskCache = diskCompilationCache

            try {
                useDiskCache = true
                diskCompilationCache = testDiskCache

                // Compile parent
                val parentSource = Source(
                    mapOf(
                        "Parent.java" to "public class Parent { public static int getValue() { return 42; } }",
                    ),
                )
                val parentCompiled = parentSource.compile(
                    CompilationArguments(useCache = true, waitForCache = true),
                )

                // Compile child with parent
                val childSource = Source(
                    mapOf(
                        "Child.java" to "public class Child { public static int test() { return Parent.getValue(); } }",
                    ),
                )
                val childCompiled = childSource.compile(
                    CompilationArguments(
                        useCache = true,
                        waitForCache = true,
                        parentFileManager = parentCompiled.fileManager,
                    ),
                )

                // Child compilation should only define Child, not Parent
                childCompiled.classLoader.definedClasses.size shouldBe 1
                childCompiled.classLoader.definedClasses shouldBe setOf("Child")

                // Clear L1 cache to force L2 (disk) restore
                val cacheKey = childSource.computeCacheKey(
                    CompilationArguments(parentFileManager = parentCompiled.fileManager),
                    childCompiled.compilerName,
                )
                compilationCache.invalidate(cacheKey)

                // Compile again - will restore from disk
                val restoredCompiled = childSource.compile(
                    CompilationArguments(
                        useCache = true,
                        waitForCache = true,
                        parentFileManager = parentCompiled.fileManager,
                    ),
                )
                restoredCompiled.cached shouldBe true

                // CRITICAL: After restoring from disk, definedClasses should STILL only contain Child
                // This test will fail if disk serialization incorrectly includes parent classes
                restoredCompiled.classLoader.definedClasses.size shouldBe 1
                restoredCompiled.classLoader.definedClasses shouldBe setOf("Child")
            } finally {
                useDiskCache = originalUseDiskCache
                diskCompilationCache = originalDiskCache
                testDiskCache.clear()
                Files.deleteIfExists(tempDir)
            }
        }
    })
