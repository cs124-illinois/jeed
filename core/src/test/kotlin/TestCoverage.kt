package edu.illinois.cs.cs125.jeed.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should

class TestCoverage : StringSpec({
    "it should ignore synthetic Java constructors" {
        val source = Source.fromJava(
            """
public class Main {
  public static void whoops() {
    System.out.println("Whoops!");
  }
  public static void main() {
    whoops();
    System.out.println("Done!");
  }
}"""
        )
        source.coverage().let { coverageResult ->
            coverageResult should haveFileMissedCount(1)
            coverageResult.adjustWithFeatures(source.features(), source.type)
        }.also { coverageResult ->
            coverageResult should haveFileMissedCount(0)
        }
    }
    "it should ignore reached but not fired Java asserts" {
        val source = Source.fromJava(
            """
public class Main {
  public static void main() {
    assert System.currentTimeMillis() != 0;
  }
}"""
        )
        source.coverage().let { coverageResult ->
            coverageResult should haveFileMissedCount(2)
            coverageResult.adjustWithFeatures(source.features(), source.type)
        }.also { coverageResult ->
            coverageResult should haveFileMissedCount(0)
        }
    }
    "it should ignore reached but not fired Kotlin asserts" {
        val source = Source.fromKotlin(
            """
fun main() {
  assert(System.currentTimeMillis() != 0L)
  assert(System.currentTimeMillis() != 1L)
  assert(System.currentTimeMillis() != 2L)
}"""
        )
        source.coverage().let { coverageResult ->
            coverageResult should haveFileMissedCount(3)
            coverageResult.adjustWithFeatures(source.features(), source.type)
        }.also { coverageResult ->
            coverageResult should haveFileMissedCount(0)
        }
    }
})