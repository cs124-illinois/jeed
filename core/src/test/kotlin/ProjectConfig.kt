package io.kotest.provided

import com.beyondgrader.resourceagent.Agent
import io.kotest.core.config.AbstractProjectConfig

object ProjectConfig : AbstractProjectConfig() {
    override suspend fun beforeProject() {
        Agent.activate(
            countLines = false,
            notifyWarmups = false,
            redirectFiles = false,
        )
    }
}
