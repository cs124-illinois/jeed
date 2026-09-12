package edu.illinois.cs.cs125.jeed.core

import java.io.PrintWriter
import java.io.StringWriter
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.logging.Formatter
import java.util.logging.Level
import java.util.logging.LogManager
import java.util.logging.LogRecord
import java.util.logging.Logger
import java.util.logging.StreamHandler

const val JEED_LOGGER_NAME = "edu.illinois.cs.cs125.jeed"

private const val LOGGER_NAME_TARGET_LENGTH = 36
private const val LEVEL_WIDTH = 5

/**
 * Formats records the way the logback pattern Jeed used to ship did, so that moving off logback
 * doesn't change what shows up in the logs:
 *
 * `%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n`
 */
class JeedLogFormatter : Formatter() {
    private val timestamp: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    override fun format(record: LogRecord): String = buildString {
        append(timestamp.format(record.instant))
        append(" [")
        // Records carry a thread id rather than a name, and there is no supported way to go from
        // one to the other. The handler below publishes on whichever thread logged, so the current
        // thread is the one that produced this record.
        append(Thread.currentThread().name)
        append("] ")
        append(record.level.toSlf4jName().padEnd(LEVEL_WIDTH))
        append(' ')
        append(abbreviateLoggerName(record.loggerName ?: ""))
        append(" - ")
        append(formatMessage(record))
        append(System.lineSeparator())
        record.thrown?.let { thrown ->
            append(
                StringWriter().also { written ->
                    PrintWriter(written).use { thrown.printStackTrace(it) }
                },
            )
        }
    }
}

/**
 * java.util.logging names its levels differently from SLF4J, and slf4j-jdk14 maps ERROR onto
 * SEVERE, WARN onto WARNING, DEBUG onto FINE and TRACE onto FINEST on the way in. Undo that so the
 * logs keep reading the way they did.
 */
private fun Level.toSlf4jName(): String = when {
    intValue() >= Level.SEVERE.intValue() -> "ERROR"

    intValue() >= Level.WARNING.intValue() -> "WARN"

    // CONFIG has no SLF4J equivalent; SLF4JBridgeHandler treats it as INFO, and FINER as DEBUG.
    intValue() >= Level.CONFIG.intValue() -> "INFO"

    intValue() >= Level.FINER.intValue() -> "DEBUG"

    else -> "TRACE"
}

/**
 * Shortens leading package segments to a single character, left to right, until the name fits, the
 * way logback's `%logger{36}` does. The last segment is the class name and is never shortened, so
 * a long enough name still overflows rather than becoming unreadable.
 */
private fun abbreviateLoggerName(name: String): String {
    if (name.length <= LOGGER_NAME_TARGET_LENGTH) {
        return name
    }
    val segments = name.split('.').toMutableList()
    if (segments.size == 1) {
        return name
    }
    var length = name.length
    for (i in 0 until segments.size - 1) {
        if (length <= LOGGER_NAME_TARGET_LENGTH) {
            break
        }
        val original = segments[i].length
        if (original <= 1) {
            continue
        }
        segments[i] = segments[i].take(1)
        length -= original - 1
    }
    return segments.joinToString(".")
}

/**
 * Writes to stdout, where logback's console appender wrote. java.util.logging's own ConsoleHandler
 * uses stderr, which would send every ordinary log line down the error stream.
 */
private class JeedStdoutHandler : StreamHandler(System.out, JeedLogFormatter()) {
    init {
        level = Level.ALL
    }

    // StreamHandler only flushes when its buffer fills, which loses whatever was pending if the
    // process exits. logback flushed per event.
    override fun publish(record: LogRecord) {
        super.publish(record)
        flush()
    }
}

/**
 * Parses a level by its SLF4J name -- `TRACE`, `DEBUG`, `INFO`, `WARN` or `ERROR`, the names that
 * actually appear in the logs -- or by its java.util.logging one, `ALL` and `OFF` included. Case
 * and surrounding whitespace don't matter.
 *
 * Returns null for anything else, so that a caller reading a level out of a configuration setting
 * can complain about a typo rather than silently falling back to a default.
 */
fun parseLogLevel(name: String): Level? {
    val cleaned = name.trim().uppercase()
    return when (cleaned) {
        "ERROR" -> Level.SEVERE

        "WARN" -> Level.WARNING

        "DEBUG" -> Level.FINE

        "TRACE" -> Level.FINEST

        // INFO, the JUL names, and the integer values Level.parse also accepts.
        else -> try {
            Level.parse(cleaned)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

// java.util.logging holds loggers weakly, so a logger whose level was set and then dropped can be
// collected and come back at its default. Hold on to the ones configured here.
private val configuredLoggers = mutableListOf<Logger>()

/**
 * Installs Jeed's console logging, replacing whatever java.util.logging picked up by default.
 *
 * Consumers embedding core are free to skip this and configure java.util.logging however they
 * like; [JeedLogFormatter] is public so that the format stays available either way.
 */
@Synchronized
fun configureJeedLogging(
    jeedLevel: Level = Level.INFO,
    rootLevel: Level = Level.WARNING,
    loggerLevels: Map<String, Level> = emptyMap(),
) {
    LogManager.getLogManager().reset()
    configuredLoggers.clear()

    val root = Logger.getLogger("")
    root.level = rootLevel
    root.addHandler(JeedStdoutHandler())
    configuredLoggers += root

    configuredLoggers += Logger.getLogger(JEED_LOGGER_NAME).also { it.level = jeedLevel }
    loggerLevels.forEach { (name, level) ->
        configuredLoggers += Logger.getLogger(name).also { it.level = level }
    }
}
