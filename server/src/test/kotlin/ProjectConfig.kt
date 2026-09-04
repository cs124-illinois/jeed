package io.kotest.provided

import edu.illinois.cs.cs125.jeed.core.configureJeedLogging
import io.kotest.core.config.AbstractProjectConfig
import java.util.logging.Level

object ProjectConfig : AbstractProjectConfig() {
    override suspend fun beforeProject() {
        // Mirrors the levels the old logback-test.xml set.
        configureJeedLogging(
            jeedLevel = Level.FINE,
            rootLevel = Level.INFO,
            loggerLevels = mapOf("ktor.test" to Level.WARNING),
        )
    }
}
