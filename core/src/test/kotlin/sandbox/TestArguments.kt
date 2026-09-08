package edu.illinois.cs.cs125.jeed.core.sandbox

import edu.illinois.cs.cs125.jeed.core.Sandbox
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

class TestArguments :
    StringSpec({
        "should reject a non-positive timeout" {
            shouldThrow<IllegalArgumentException> {
                Sandbox.ExecutionArguments(timeout = 0)
            }.message shouldStartWith "Invalid timeout"
        }
        "should reject a negative CPU timeout" {
            shouldThrow<IllegalArgumentException> {
                Sandbox.ExecutionArguments(cpuTimeoutNS = -1)
            }.message shouldStartWith "Invalid cpuTimeout"
        }
        "should reject a CPU timeout without a poll interval" {
            shouldThrow<IllegalArgumentException> {
                Sandbox.ExecutionArguments(cpuTimeoutNS = 1000L, pollIntervalMS = 0)
            }.message shouldContain "Must set pollInterval to use cpuTimeout"
        }
        "should reject a CPU timeout that outlasts the wall clock timeout" {
            shouldThrow<IllegalArgumentException> {
                Sandbox.ExecutionArguments(timeout = 10, cpuTimeoutNS = 10 * 1000L * 1000L)
            }.message shouldContain "CPU timeout must be less than wall clock timeout"
        }
        "should reject a default thread priority above the maximum" {
            shouldThrow<IllegalArgumentException> {
                Sandbox.ExecutionArguments(
                    maxThreadPriority = Thread.MIN_PRIORITY,
                    defaultThreadPriority = Thread.NORM_PRIORITY,
                )
            }.message shouldContain "defaultThreadPriority must be less than or equal to maxThreadPriority"
        }
        "should reject a class whitelist and blacklist together" {
            shouldThrow<IllegalArgumentException> {
                Sandbox.ClassLoaderConfiguration(
                    whitelistedClasses = setOf("java.util."),
                    blacklistedClasses = setOf("java.io."),
                )
            }.message shouldContain "can't set both a class whitelist and blacklist"
        }
        "should reject an unsafe exception that is not a Throwable" {
            shouldThrow<IllegalArgumentException> {
                Sandbox.ClassLoaderConfiguration(unsafeExceptions = setOf("java.lang.String"))
            }.message shouldContain "does not refer to a Java Throwable"
        }
        "should reject a safe error that is not an Error" {
            shouldThrow<IllegalArgumentException> {
                Sandbox.ClassLoaderConfiguration(safeErrors = setOf("java.lang.Exception"))
            }.message shouldContain "does not refer to a Java Error"
        }
        "should reject a safe error that can never be safe" {
            Sandbox.ClassLoaderConfiguration.NEVER_SAFE_ERRORS.forEach { neverSafe ->
                shouldThrow<IllegalArgumentException> {
                    Sandbox.ClassLoaderConfiguration(safeErrors = setOf(neverSafe))
                }.message shouldContain "cannot be a safe error"
            }
        }
        "should reject a safe error that subclasses one that can never be safe" {
            shouldThrow<IllegalArgumentException> {
                // StackOverflowError is a VirtualMachineError, which is never safe
                Sandbox.ClassLoaderConfiguration(safeErrors = setOf("java.lang.StackOverflowError"))
            }.message shouldContain "cannot be a safe error"
        }
    })
