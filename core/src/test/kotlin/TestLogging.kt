package edu.illinois.cs.cs125.jeed.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldMatch
import java.util.logging.Level
import java.util.logging.LogRecord

private fun format(level: Level, loggerName: String, message: String): String = JeedLogFormatter().format(
    LogRecord(level, message).also { it.loggerName = loggerName },
)

class TestLogging :
    StringSpec({
        // These pin the logback pattern Jeed shipped before moving to java.util.logging:
        // %d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
        "should format a record the way the logback pattern did" {
            format(Level.INFO, "edu.illinois.cs.cs125.jeed.server.Main", "Jeed server starting...")
                .trimEnd() shouldMatch
                """^\d{2}:\d{2}:\d{2}\.\d{3} \[[^]]+] INFO {2}e\.illinois\.cs\.cs125\.jeed\.server\.Main - Jeed server starting\.\.\.$"""
        }
        "should report SLF4J level names rather than java.util.logging ones" {
            // slf4j-jdk14 maps the SLF4J levels onto these on the way in, so they have to map back.
            mapOf(
                Level.SEVERE to "ERROR",
                Level.WARNING to "WARN",
                Level.INFO to "INFO",
                Level.CONFIG to "INFO",
                Level.FINE to "DEBUG",
                Level.FINER to "DEBUG",
                Level.FINEST to "TRACE",
            ).forEach { (level, expected) ->
                val line = format(level, "Test", "message")
                line.substringAfter("] ").substringBefore(" Test") shouldBe expected.padEnd(5)
            }
        }
        "should pad the level to five characters" {
            // %-5level: INFO and WARN gain a trailing space, ERROR and DEBUG already fill it.
            format(Level.INFO, "Test", "m").trimEnd() shouldMatch """.*] INFO {2}Test - m.*"""
            format(Level.SEVERE, "Test", "m").trimEnd() shouldMatch """.*] ERROR Test - m.*"""
            format(Level.FINE, "Test", "m").trimEnd() shouldMatch """.*] DEBUG Test - m.*"""
        }
        "should leave logger names that already fit alone" {
            format(Level.INFO, "a.b.C", "m").trimEnd() shouldMatch """.*] INFO {2}a\.b\.C - m.*"""

            // Exactly at the 36 character limit, so still untouched.
            val atLimit = "edu.illinois.cs.cs125.jeed.core.Sour"
            atLimit.length shouldBe 36
            format(Level.INFO, atLimit, "m").substringAfter("INFO  ").substringBefore(" - ") shouldBe atLimit
        }
        "should abbreviate leading segments left to right until the name fits" {
            // 38 characters: shortening only "edu" is enough to reach 36.
            val name = "edu.illinois.cs.cs125.jeed.server.Main"
            name.length shouldBe 38
            val abbreviated = format(Level.INFO, name, "m").substringAfter("INFO  ").substringBefore(" - ")
            abbreviated shouldBe "e.illinois.cs.cs125.jeed.server.Main"
            abbreviated.length shouldBe 36
        }
        "should never abbreviate the class name itself" {
            val abbreviated =
                format(Level.INFO, "com.example.deeply.nested.package.names.VeryLongClassNameIndeed", "m")
                    .substringAfter("INFO  ")
                    .substringBefore(" - ")
            abbreviated shouldEndWith "VeryLongClassNameIndeed"
            abbreviated shouldBe "c.e.d.n.p.n.VeryLongClassNameIndeed"
        }
        "should parse levels by their SLF4J names" {
            // Whatever parseLogLevel returns for a name has to format back to that same name, or a
            // level set by environment variable wouldn't match what shows up in the logs.
            listOf("ERROR", "WARN", "INFO", "DEBUG", "TRACE").forEach { name ->
                val level = parseLogLevel(name).shouldNotBeNull()
                format(level, "Test", "m").substringAfter("] ").substringBefore(" Test") shouldBe name.padEnd(5)
            }
        }
        "should parse levels by their java.util.logging names" {
            parseLogLevel("SEVERE") shouldBe Level.SEVERE
            parseLogLevel("WARNING") shouldBe Level.WARNING
            parseLogLevel("CONFIG") shouldBe Level.CONFIG
            parseLogLevel("FINER") shouldBe Level.FINER
            parseLogLevel("ALL") shouldBe Level.ALL
            parseLogLevel("OFF") shouldBe Level.OFF
        }
        "should ignore case and surrounding whitespace when parsing levels" {
            parseLogLevel("warn") shouldBe Level.WARNING
            parseLogLevel(" Info ") shouldBe Level.INFO
        }
        "should refuse to parse anything that is not a level" {
            parseLogLevel("quiet").shouldBeNull()
            parseLogLevel("").shouldBeNull()
            parseLogLevel("WARNN").shouldBeNull()
        }
        "should include a stack trace when the record carries one" {
            val record = LogRecord(Level.SEVERE, "it broke").also {
                it.loggerName = "Test"
                it.thrown = IllegalStateException("the cause")
            }
            val formatted = JeedLogFormatter().format(record)
            formatted shouldMatch """(?s).*] ERROR Test - it broke.*"""
            formatted shouldMatch """(?s).*IllegalStateException: the cause.*"""
        }
    })
