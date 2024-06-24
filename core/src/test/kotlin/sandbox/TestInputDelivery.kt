package edu.illinois.cs.cs125.jeed.core.sandbox

import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.SourceExecutionArguments
import edu.illinois.cs.cs125.jeed.core.compile
import edu.illinois.cs.cs125.jeed.core.execute
import edu.illinois.cs.cs125.jeed.core.fromSnippet
import edu.illinois.cs.cs125.jeed.core.haveCombinedInputOutput
import edu.illinois.cs.cs125.jeed.core.haveCompleted
import edu.illinois.cs.cs125.jeed.core.haveStderr
import edu.illinois.cs.cs125.jeed.core.haveStdin
import edu.illinois.cs.cs125.jeed.core.haveStdout
import edu.illinois.cs.cs125.jeed.core.haveTimedOut
import edu.illinois.cs.cs125.jeed.core.toSystemIn
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot

class TestInputDelivery :
    StringSpec({
        "should deliver stdin" {
            val executionResult = Source.fromSnippet(
                """
import java.util.Scanner;

Scanner scanner = new Scanner(System.in);
String nextLine = scanner.nextLine();
System.out.println("Echo: " + nextLine);
            """.trim(),
            ).compile().execute(SourceExecutionArguments(systemInStream = "Here\n".toSystemIn()))

            executionResult should haveCompleted()
            executionResult shouldNot haveTimedOut()

            executionResult.inputLines shouldHaveSize 1
            executionResult.combinedInputOutput.lines() shouldHaveSize 3

            executionResult should haveStdout("Echo: Here")
            executionResult should haveStderr("")
            executionResult should haveStdin("Here")
            executionResult should haveCombinedInputOutput("Here\nEcho: Here")
        }
    })
