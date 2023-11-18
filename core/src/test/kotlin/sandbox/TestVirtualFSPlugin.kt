package edu.illinois.cs.cs125.jeed.core.sandbox

import com.beyondgrader.resourceagent.jeed.IsolatedFileSystemProvider
import com.beyondgrader.resourceagent.jeed.VirtualFilesystem
import com.beyondgrader.resourceagent.jeed.VirtualFilesystemArguments
import com.beyondgrader.resourceagent.jeed.populate
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.SourceExecutionArguments
import edu.illinois.cs.cs125.jeed.core.compile
import edu.illinois.cs.cs125.jeed.core.execute
import edu.illinois.cs.cs125.jeed.core.haveCompleted
import edu.illinois.cs.cs125.jeed.core.haveOutput
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should

class TestVirtualFSPlugin : StringSpec({
    "should provide a simple file" {
        val filesystem = IsolatedFileSystemProvider().apply {
            fileSystem.populate(mapOf("/test.txt" to "Hello, world!".toByteArray()))
        }
        Source.fromJava(
            """
import java.util.Scanner;
import java.io.File;

public class Main {
  public static void main() throws Exception {
    String greeting = new Scanner(new File("/test.txt"), "UTF-8").useDelimiter("\\A").next();
    System.out.println(greeting);
  }
}""".trim(),
        ).compile().execute(SourceExecutionArguments().addPlugin(VirtualFilesystem, VirtualFilesystemArguments(filesystem))).also { result ->
            result should haveCompleted()
            result should haveOutput("Hello, world!")
        }
    }
})
