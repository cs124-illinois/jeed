package edu.illinois.cs.cs125.jeed.core

import java.util.Properties

val VERSION = run {
    @Suppress("TooGenericExceptionCaught")
    try {
        val versionFile = object {}::class.java.getResource("/edu.illinois.cs.cs125.jeed.version")
        Properties().also { it.load(versionFile!!.openStream()) }["version"] as String
    } catch (_: Exception) {
        "unspecified"
    }
}
