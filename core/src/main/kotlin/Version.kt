package edu.illinois.cs.cs125.jeed.core

import java.util.Properties

val VERSION: String = Properties().also {
    it.load((object {}).javaClass.getResourceAsStream("/edu.illinois.cs.cs125.jeed.version"))
}.getProperty("version")
