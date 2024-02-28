@file:Suppress("MagicNumber")

package edu.illinois.cs.cs125.jeed.core.sandbox

import edu.illinois.cs.cs125.jeed.core.CompiledSource
import edu.illinois.cs.cs125.jeed.core.Sandbox
import edu.illinois.cs.cs125.jeed.core.SnippetArguments
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.SourceExecutionArguments
import edu.illinois.cs.cs125.jeed.core.compile
import edu.illinois.cs.cs125.jeed.core.execute
import edu.illinois.cs.cs125.jeed.core.fromSnippet
import edu.illinois.cs.cs125.jeed.core.haveCompleted
import edu.illinois.cs.cs125.jeed.core.haveOutput
import edu.illinois.cs.cs125.jeed.core.haveTimedOut
import edu.illinois.cs.cs125.jeed.core.kompile
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.beInstanceOf
import io.kotest.matchers.types.instanceOf
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.PrintStream
import java.lang.IllegalArgumentException
import java.net.URI
import java.nio.file.FileSystems
import java.util.PropertyPermission

val blacklistArguments = SourceExecutionArguments(
    permissionBlackList = true,
    permissions = SourceExecutionArguments.GENERALLY_UNSAFE_PERMISSIONS,
)

class TestPermissions : StringSpec({
    "should prevent threads from populating a new thread group" {
        Source.fromSnippet(
            """
public class Example implements Runnable {
    public void run() {
        System.out.println("Here");
        System.exit(1);
    }
}
ThreadGroup threadGroup = new ThreadGroup("test");
Thread thread = new Thread(new ThreadGroup("test"), new Example());
thread.start();
try {
    thread.join();
} catch (Exception e) { }
System.out.println("There");
        """.trim(),
        ).compile().checkPermissions(SourceExecutionArguments(maxExtraThreads = 7))
    }
    "should prevent snippets from exiting" {
        Source.fromSnippet(
            """
System.exit(2);
        """.trim(),
        ).compile().checkPermissions()
    }
    "should prevent creating virtual threads".config(enabled = Runtime.version().feature() >= 21) {
        // Warm virtual thread machinery outside sandbox
        val virtualBuilder = Thread::class.java.methods.find { it.name == "ofVirtual" }!!.invoke(null)
        val builderIface = Class.forName("java.lang.Thread\$Builder")
        val startMethod = builderIface.methods.find { it.name == "start" }!!
        val thread = startMethod.invoke(virtualBuilder, Runnable { }) as Thread
        thread.join()

        val executionResult = Source.fromSnippet(
            """
            Thread.ofVirtual().start(() -> {
                while (true);
            });
            """.trimIndent(),
        ).compile().execute()

        executionResult shouldNot haveCompleted()
        executionResult.threw shouldNot beInstanceOf<ExceptionInInitializerError>()
        executionResult.permissionDenied shouldBe true
    }
    "should prevent snippets from redirecting System.out" {
        Source.fromSnippet(
            """
import java.io.*;

ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
PrintStream printStream = new PrintStream(byteArrayOutputStream);
System.setOut(printStream);
        """.trim(),
        ).compile().checkPermissions()
    }
    "should prevent trusted task code from redirecting System.out" {
        val executionResult = Sandbox.execute<Any> {
            val byteArrayOutputStream = ByteArrayOutputStream()
            val printStream = PrintStream(byteArrayOutputStream)
            System.setOut(printStream)
        }
        executionResult shouldNot haveCompleted()
        executionResult.permissionDenied shouldBe true
    }
    "should prevent snippets from reading files" {
        Source.fromSnippet(
            """
import java.io.*;
System.out.println(new File("/").listFiles().length);
        """.trim(),
        ).compile().checkPermissions()
    }
    "should prevent snippets from writing files" {
        Source.fromSnippet(
            """
import java.io.*;
var writer = new PrintWriter("test.txt", "UTF-8");
writer.println("Uh oh");
writer.close();
        """.trim(),
        ).compile().checkPermissions()
    }
    "should prevent snippets from writing files through the Files API" {
        Source.fromSnippet(
            """
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
Path file = Paths.get("test.txt");
Files.write(file, Arrays.asList("oh", "no"), StandardCharsets.UTF_8);
        """.trim(),
        ).compile().checkPermissions()
    }
    "should prevent writing files through the Kotlin stdlib" {
        Source.fromSnippet(
            """
import java.io.File
File("test.txt").writeText("uh oh")
                """.trim(),
            SnippetArguments(fileType = Source.FileType.KOTLIN),
        ).kompile().checkPermissions()
    }
    "should allow snippets to read system properties if allowed" {
        Source.fromSnippet(
            """
System.out.println(System.getProperty("file.separator"));
        """.trim(),
        ).compile()
            .checkPermissions(SourceExecutionArguments(permissions = setOf(PropertyPermission("*", "read"))), ok = true)
    }
    "should prevent snippets from reading system properties" {
        Source.fromSnippet(
            """
System.out.println(System.getProperty("file.separator"));
        """.trim(),
        ).compile().execute().also { executionResult ->
            executionResult shouldNot haveCompleted()
            executionResult.permissionDenied shouldBe true
        }
        Source.fromSnippet(
            """
System.out.println(System.getProperty("file.separator"));
        """.trim(),
        ).compile().execute(blacklistArguments).also { executionResult ->
            executionResult should haveCompleted()
            executionResult.permissionDenied shouldBe false
        }
    }
    "should allow permissions to be changed between runs" {
        val compiledSource = Source.fromSnippet(
            """
System.out.println(System.getProperty("file.separator"));
        """.trim(),
        ).compile()

        compiledSource.execute().also { failedExecution ->
            failedExecution shouldNot haveCompleted()
            failedExecution.permissionDenied shouldBe true
        }

        compiledSource
            .execute(SourceExecutionArguments(permissions = setOf(PropertyPermission("*", "read"))))
            .also { successfulExecution ->
                successfulExecution should haveCompleted()
                successfulExecution.permissionDenied shouldBe false
            }
    }
    "should prevent snippets from starting threads by default" {
        Source.fromSnippet(
            """
public class Example implements Runnable {
    public void run() { }
}
Thread thread = new Thread(new Example());
thread.start();
System.out.println("Started");
        """.trim(),
        ).compile().checkPermissions()
    }
    "should allow snippets to start threads when configured" {
        val compiledSource = Source.fromSnippet(
            """
public class Example implements Runnable {
    public void run() {
        System.out.println("Ended");
    }
}
Thread thread = new Thread(new Example());
System.out.println("Started");
thread.start();
try {
    thread.join();
} catch (Exception e) {
    System.out.println(e);
}
        """.trim(),
        ).compile()

        compiledSource.checkPermissions()
        compiledSource.checkPermissions(SourceExecutionArguments(maxExtraThreads = 1), ok = true)
            .also { successfulExecutionResult ->
                successfulExecutionResult should haveOutput("Started\nEnded")
            }
    }
    "should limit threads with delayed start" {
        Source.fromSnippet(
            """
Runnable r = () -> {
    while (true);
};
Thread[] threads = new Thread[5];
for (int i = 0; i < threads.length; i++) {
    threads[i] = new Thread(r);
}
for (int i = 0; i < threads.length; i++) {
    threads[i].start();
    System.out.println(i);
}
threads[0].join();
        """.trim(),
        ).compile().checkPermissions(SourceExecutionArguments(maxExtraThreads = 1)).also { result ->
            result.output shouldNotContain "1"
        }
    }
    "should not allow unsafe permissions to be provided" {
        shouldThrow<IllegalArgumentException> {
            Source.fromSnippet(
                """
System.exit(3);
            """.trim(),
            ).compile().execute(
                SourceExecutionArguments(permissions = setOf(RuntimePermission("exitVM"))),
            )
        }
    }
    "should allow Java streams with default permissions" {
        Source.fromSnippet(
            """
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

List<String> strings = new ArrayList<>(Arrays.asList(new String[] { "test", "me", "another" }));
strings.stream()
    .filter(string -> string.length() <= 4)
    .map(String::toUpperCase)
    .sorted()
    .map(String::new)
    .forEach(System.out::println);
        """.trim(),
        ).compile().checkPermissions(ok = true).also { executionResult ->
            executionResult should haveOutput("ME\nTEST")
        }
    }
    "should allow generic methods with the default permissions" {
        Source(
            mapOf(
                "A.java" to """
public class A implements Comparable<A> {
    public int compareTo(A other) {
        return 0;
    }
}
                """.trim(),
                "Main.java" to """
public class Main {
    public static <T extends Comparable<T>> int test(T[] values) {
        return 8;
    }
    public static void main() {
        System.out.println(test(new A[] { }));
    }
}
        """.trim(),
            ),
        ).compile().checkPermissions(ok = true).also { executionResult ->
            executionResult should haveOutput("8")
        }
    }
    "it should not allow snippets to contact the internet" {
        Source.fromSnippet(
            """
import java.net.URL;

URL url = new URL("http://cs124.org");
var connection = url.openConnection();
connection.connect();
        """.trim(),
        ).compile().checkPermissions(SourceExecutionArguments(timeout = 10000))
    }
    "should not allow serving web requests".config(enabled = Runtime.version().feature() >= 18) {
        val executionResult = Source.fromSnippet(
            """
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.file.*;

SimpleFileServer.createFileServer(new InetSocketAddress(8124), Path.of("/"), SimpleFileServer.OutputLevel.NONE).start();
        """.trim(),
        ).compile().execute()

        executionResult shouldNot haveTimedOut()
        executionResult shouldNot haveCompleted()
        executionResult.permissionDenied shouldBe true
    }
    "it should not allow snippets to execute commands" {
        Source.fromSnippet(
            """
import java.io.*;

Process p = Runtime.getRuntime().exec("/bin/sh ls");
BufferedReader in = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));
String line = null;

while ((line = in.readLine()) != null) {
    System.out.println(line);
}
        """.trim(),
        ).compile().checkPermissions()
    }
    "it should not allow snippets to examine other processes" {
        ProcessHandle.current() // Warm (execute static initializers) outside sandbox
        Source.fromSnippet(
            """
ProcessHandle.allProcesses().forEach(p -> System.out.println(p.info()));
        """.trim(),
        ).compile().apply {
            execute().also { executionResult ->
                executionResult shouldNot haveCompleted()
                executionResult.permissionDenied shouldBe true
            }
            execute(blacklistArguments).also { executionResult ->
                executionResult shouldNot haveCompleted()
                executionResult.permissionDenied shouldBe true
            }
        }
    }
    "should not allow SecurityManager to be created again through reflection" {
        Source.fromSnippet(
            """
Class<SecurityManager> c = SecurityManager.class;
SecurityManager s = c.newInstance();
        """.trim(),
        ).compile().checkPermissions()
    }
    "should not allow access to the compiler" {
        Source.fromSnippet(
            """
import java.lang.reflect.*;

Class<?> sourceClass = Class.forName("edu.illinois.cs.cs125.jeed.core.Source");
Field sourceCompanion = sourceClass.getField("Companion");
Class<?> snippetKtClass = Class.forName("edu.illinois.cs.cs125.jeed.core.SnippetKt");
Method transformSnippet = snippetKtClass.getMethod(
    "transformSnippet", sourceCompanion.getType(), String.class, int.class
);
Object snippet = transformSnippet.invoke(null, sourceCompanion.get(null), "System.out.println(403);", 4);
Class<?> snippetClass = snippet.getClass();
Class<?> compileArgsClass = Class.forName("edu.illinois.cs.cs125.jeed.core.CompilationArguments");
Method compile = Class.forName(
    "edu.illinois.cs.cs125.jeed.core.CompileKt").getMethod("compile", sourceClass, compileArgsClass
);
Object compileArgs = compileArgsClass.newInstance();
Object compiledSource = compile.invoke(null, snippet, compileArgs);
        """.trim(),
        ).compile().checkPermissions()
    }
    "should not allow reflection to control the sandbox" {
        Source.fromSnippet(
            """
import java.lang.reflect.*;

Class<?> groupClass = Class.forName("edu.illinois.cs.cs125.jeed.core.Sandbox${'$'}ConfinedThreadGroup");
Field field = groupClass.getDeclaredField("task");
field.setAccessible(true);
Object confinedTask = field.get(Thread.currentThread().getThreadGroup());
        """.trim(),
        ).compile().checkPermissions()
    }
    "should not prevent trusted code from using reflection" {
        val executionResult = Sandbox.execute {
            val groupClass = Class.forName("edu.illinois.cs.cs125.jeed.core.Sandbox\$ConfinedThreadGroup")
            val field = groupClass.getDeclaredField("task")
            field.isAccessible = true
        }

        executionResult should haveCompleted()
    }
    "should not prevent trusted code from accessing files" {
        val executionResult = Sandbox.execute {
            File("test.txt").exists()
        }

        executionResult should haveCompleted()
        executionResult.permissionDenied shouldBe false
    }
    "should not allow static{} to escape the sandbox" {
        Source(
            mapOf(
                "Example.java" to """
public class Example {
    static {
        System.out.println("Static initializer");
        System.exit(-1);
    }
    public static void main() {
        System.out.println("Main");
    }
}
        """,
            ),
        ).compile().checkPermissions(SourceExecutionArguments("Example"))
    }
    "should not allow finalizers to escape the sandbox" {
        Source(
            mapOf(
                "Example.java" to """
public class Example {
    public static void main() {
        Example ex = null;
        for (int i = 0; i < 10000; i++) {
            ex = new Example();
        }
    }
    public Example() {
        System.out.println("Example");
    }
    protected void finalize() {
        System.out.println("Finalizer");
        System.exit(-1);
    }
}
""".trim(),
            ),
        ).compile()
            .checkPermissions(SourceExecutionArguments("Example", maxOutputLines = 10240), ok = true)
            .also { executionResult ->
                System.gc()
                executionResult.outputLines shouldHaveSize 10000
                executionResult.outputLines.all { (_, line) -> line.trim() == "Example" } shouldBe true
            }
    }
    "should not allow LambdaMetafactory to escape the sandbox" {
        Source.fromSnippet(
            """
import java.lang.invoke.*;
            
try {
    MethodHandle handle =
        MethodHandles.lookup().findStatic(System.class, "exit", MethodType.methodType(void.class, int.class));
    CallSite site = LambdaMetafactory.metafactory(MethodHandles.lookup(),
            "run",
            MethodType.methodType(Runnable.class, int.class),
            MethodType.methodType(void.class),
            handle,
            MethodType.methodType(void.class));
    Runnable runnable = (Runnable) site.dynamicInvoker().invoke(125);
    Thread thread = new Thread(runnable);
    thread.start();
    Thread.sleep(50);
} catch (Throwable t) {
    throw new RuntimeException(t);
}
        """.trim(),
        ).compile().checkPermissions(SourceExecutionArguments(maxExtraThreads = 1))
    }
    "should not allow MethodHandle-based reflection to dodge the sandbox" {
        Source.fromSnippet(
            """
import java.lang.invoke.*;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
            
try {
    MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(CheckstyleException.class, MethodHandles.lookup());
    Class<?> shutdownClass = Class.forName("java.lang.Shutdown");
    Class<?> methodClass = lookup.findClass("java.lang.reflect.Method");
    MethodHandle gdmHandle = lookup.findVirtual(
        Class.class, "getDeclaredMethod", MethodType.methodType(methodClass, String.class, Class[].class)
    );
    Object exitMethod = gdmHandle.invokeWithArguments(shutdownClass, "exit", int.class);
    MethodHandle saHandle = lookup.findVirtual(methodClass, "trySetAccessible", MethodType.methodType(boolean.class));
    saHandle.invokeWithArguments(exitMethod);
    MethodHandle invokeHandle = lookup.findVirtual(
        methodClass, "invoke", MethodType.methodType(Object.class, Object.class, Object[].class)
    );
    invokeHandle.invokeWithArguments(exitMethod, null, 125);
} catch (Throwable t) {
    throw new RuntimeException(t);
}
        """.trim(),
        ).compile().checkPermissions(SourceExecutionArguments())
    }
    "should not allow MethodHandles to alter the security manager" {
        Source.fromSnippet(
            """
import java.lang.invoke.*;
import java.util.Map;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
            
try {
    MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(CheckstyleException.class, MethodHandles.lookup());
    Class<?> sandboxClass = lookup.findClass("edu.illinois.cs.cs125.jeed.core.Sandbox");
    Class<?> fieldClass = lookup.findClass("java.lang.reflect.Field");
    MethodHandle gdfHandle = lookup.findVirtual(
        Class.class, "getDeclaredField", MethodType.methodType(fieldClass, String.class)
    );
    Object confinedField = gdfHandle.invokeWithArguments(sandboxClass, "confinedTasks");
    MethodHandle saHandle = lookup.findVirtual(
        fieldClass, "setAccessible", MethodType.methodType(void.class, boolean.class)
    );
    saHandle.invokeWithArguments(confinedField, true);
    MethodHandle getHandle = lookup.findVirtual(fieldClass, "get", MethodType.methodType(Object.class, Object.class));
    Map confined = (Map) getHandle.invokeWithArguments(confinedField, null);
    confined.clear();
} catch (Throwable t) {
    throw new RuntimeException(t);
}
        """.trim(),
        ).compile().checkPermissions()
    }
    "should not allow calling forbidden methods" {
        Source.fromSnippet(
            """
import java.lang.invoke.MethodHandles;

MethodHandles.Lookup lookup = null;
var clazz = lookup.findClass("edu.illinois.cs.cs125.jeed.core.Sandbox");
System.out.println(clazz);
        """.trim(),
        ).compile().checkPermissions().also { executionResult ->
            executionResult.threw shouldBe instanceOf<SecurityException>()
            executionResult.threw!!.message shouldBe "use of forbidden method"
        }
    }
    "should not allow installing an agent through ByteBuddy, coroutine-style" {
        Source.fromSnippet(
            """
import net.bytebuddy.agent.ByteBuddyAgent;

ByteBuddyAgent.install(ByteBuddyAgent.AttachmentProvider.ForEmulatedAttachment.INSTANCE);
        """.trim(),
        ).compile().checkPermissions().also { executionResult ->
            executionResult.threw shouldNot beNull()
        }
    }
    "should not allow installing an agent through ByteBuddy's default provider" {
        Source.fromSnippet(
            """
import net.bytebuddy.agent.ByteBuddyAgent;

ByteBuddyAgent.install();
        """.trim(),
        ).compile().checkPermissions().also { executionResult ->
            executionResult.threw shouldNot beNull()
        }
    }
    "should not allow using the attachment/VM API directly" {
        Source.fromSnippet(
            """
import com.sun.tools.attach.VirtualMachine;

var vms = VirtualMachine.list();
var vmid = vms.get(0).id();
var vm = VirtualMachine.attach(vmid);
        """.trim(),
        ).compile().checkPermissions().also { executionResult ->
            executionResult.threw shouldNot beNull()
        }
    }
    "should not allow access to private constructors" {
        Source.fromSnippet(
            """
import java.lang.reflect.*;

class Example {
    private Example() { }
}

Constructor<?> cons = Example.class.getDeclaredConstructors()[0];
cons.setAccessible(true);
System.out.println(cons.newInstance());
        """.trim(),
        ).compile().checkPermissions()
    }
    "should not allow loading sun.misc classes" {
        Source.fromSnippet(
            """
import sun.misc.Unsafe;

Unsafe unsafe = null;
unsafe.getInt(null, 0); // obvious NPE, but should fail in classloading first
        """.trim(),
        ).compile().execute().also { executionResult ->
            executionResult shouldNot haveTimedOut()
            executionResult shouldNot haveCompleted()
            executionResult.permissionDenied shouldBe true
            executionResult.permissionRequests.find {
                it.permission.name.startsWith("accessClassInPackage.sun")
            } shouldNot beNull()
        }
    }
    "should not allow Class.forName by default" {
        Source.fromSnippet(
            """
class X {}
var cl = X.class.getClassLoader().getParent();
System.out.println(Class.forName("edu.illinois.cs.cs125.jeed.core.Sandbox", true, cl));
        """.trim(),
        ).compile().checkPermissions()
    }
    "should block forbidden methods in method references" {
        Source.fromSnippet(
            """
                import java.util.function.*;
                class X {}
                Function<Class<?>, ClassLoader> gcl = Class::getClassLoader;
                System.out.println(gcl.apply(X.class));
            """.trimIndent(),
        ).compile().checkPermissions()
    }
    "should not allow using classloaders by default" {
        Source.fromSnippet(
            """
class X {}
var cl = X.class.getClassLoader().getParent();
System.out.println(cl.loadClass("edu.illinois.cs.cs125.jeed.core.Sandbox"));
        """.trim(),
        ).compile().checkPermissions()
    }
    "should not allow using classloaders through a cast to SecureClassLoader" {
        Source.fromSnippet(
            """
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import java.security.SecureClassLoader;
var cl = (SecureClassLoader) CheckstyleException.class.getClassLoader();
System.out.println(cl.loadClass("edu.illinois.cs.cs125.jeed.core.Sandbox"));
        """.trim(),
        ).compile().checkPermissions()
    }
    "should not allow Kotlin reflection by default" {
        Source.fromSnippet(
            """
import edu.illinois.cs.cs125.jeed.core.Sandbox.RewriteBytecode

val klass = RewriteBytecode::class
val sandboxKlass = klass.java.enclosingClass.kotlin
println(sandboxKlass.constructors)
                """.trim(),
            SnippetArguments(fileType = Source.FileType.KOTLIN),
        ).kompile().checkPermissions()
    }
    "should not allow allocation of direct byte buffers" {
        Source.fromSnippet(
            """
import java.nio.*;
MappedByteBuffer.allocateDirect(1000);
        """.trim(),
        ).compile().checkPermissions()
    }
    "should not allow native memory access".config(enabled = Runtime.version().feature() >= 20) {
        val executionResult = Source.fromSnippet(
            """
import java.lang.foreign.*;
Linker linker = Linker.nativeLinker();
MemorySegment segment = MemorySegment.ofAddress(0x200000).reinterpret(2);
segment.set(ValueLayout.JAVA_BYTE, 0L, (byte) 100);
        """.trim(),
        ).compile().execute()
        executionResult shouldNot haveCompleted()
        executionResult.permissionDenied shouldBe true
    }
    "should not allow parallel streams to escape the sandbox" {
        repeat(3) {
            // Try a few times, with a short delay --
            // the sandbox thread can throw before the background threads call exit
            Source.fromSnippet(
                """
                import java.util.Arrays;
                int[] ints = new int[1024];
                Arrays.stream(ints).parallel().forEach(System::exit);
                """.trimIndent(),
            ).compile().checkPermissions().also { executionResult ->
                executionResult.threw should beInstanceOf<SecurityException>()
            }
            delay(2L - it)
        }
    }
    "should not allow parallel streams to poison the pool" {
        Source.fromSnippet(
            """
            import java.util.Arrays;
            int[] ints = new int[1024];
            Arrays.stream(ints).parallel().forEach(System::exit);
            """.trimIndent(),
        ).compile().checkPermissions()
        (0 until 1024).toList().parallelStream().map {
            Thread.sleep(1) // Prevent the main thread from doing all the work
            Thread.currentThread()
        }.distinct().count() shouldBeGreaterThan 1
    }
    "should not allow printing to the real stdout" {
        Source.fromSnippet(
            """
            import java.io.*;
            FileOutputStream fdOut = new FileOutputStream(FileDescriptor.out);
            PrintStream psOut = new PrintStream(fdOut, true);
            psOut.println("Uh oh");
            """.trimIndent(),
        ).compile().checkPermissions()
    }
    "should not choke on security checks from other classloaders" {
        val executionResult = Sandbox.execute(executionArguments = Sandbox.ExecutionArguments(timeout = 1000)) {
            FileSystems.newFileSystem(URI.create("jar:file:/nonexistent"), mapOf("create" to false))
        }
        executionResult.timeout shouldBe false
        executionResult.threw should beInstanceOf<IOException>() // Not StackOverflowError
    }
})

private suspend fun CompiledSource.checkPermissions(
    arguments: SourceExecutionArguments = SourceExecutionArguments(),
    ok: Boolean = false,
): Sandbox.TaskResults<*> {
    fun Sandbox.TaskResults<*>.check() {
        this shouldNot haveTimedOut()
        if (!ok) {
            this shouldNot haveCompleted()
            permissionDenied shouldBe true
        } else {
            this should haveCompleted()
            permissionDenied shouldBe false
        }
    }
    execute(arguments).check()
    val blacklistArguments = SourceExecutionArguments(
        klass = arguments.klass,
        timeout = arguments.timeout,
        maxOutputLines = arguments.maxOutputLines,
        maxExtraThreads = arguments.maxExtraThreads,
        permissionBlackList = true,
        permissions = SourceExecutionArguments.GENERALLY_UNSAFE_PERMISSIONS,
    )
    execute(blacklistArguments).also {
        it.check()
        return it
    }
}
