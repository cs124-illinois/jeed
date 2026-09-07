# Sandbox Output Leak Under Ktor 3.5

Findings from the questioner side (`~/code/questioner`), written for whoever picks this up in jeed.

## Summary: Resolved

**Cause: `org.fusesource.jansi:jansi:2.4.3`, arriving through `io.ktor:ktor-server-call-logging:3.5.2`.**
Jansi does native console I/O straight to the process's file descriptor. Questioner installs
`CallLogging`, so it is on the path, and submitted code's output ends up reaching the console below
`System.out`. Excluding jansi takes the leak from 315 lines to 0, confirmed twice in each direction.

**This is very likely not a jeed bug.** Jeed's capture worked correctly throughout: grading was never
affected, a deliberately wrong printing submission was still rejected, and `System.out` remained
`Sandbox$RedirectingPrintStream` from the first execution onward, so `checkOutputStreams()` was right
to stay quiet. A writer below `System.out` is invisible to a `PrintStream`-level redirect by
construction. What is left for jeed is a question about the guarantee, not a defect. See
**What This Means For Jeed** at the end.

Questioner has excluded jansi and keeps Ktor 3.5.2. Nothing is blocked on jeed.

The sections below are the investigation trail in the order it happened, so earlier conclusions in it
were superseded. Two in particular were wrong and are corrected later: that the leak was pre-existing,
and that the escape path was jeed's fall-through.

## Reproduction

In `~/code/questioner`, roughly four seconds:

```bash
./gradlew :server:test --tests "*TestSimpleIfElseServer" --rerun
```

Count bare leaked lines with `grep -cE "^(positive|non-positive)$"` over the output. Use `--rerun`,
not `--rerun-tasks`; the latter rebuilds the world and takes about three minutes.

The fixture is `plugin-fixtures/.../simpleifelse/Question.java`, a pure printing question whose
`checkValue` returns `void` and prints `positive` or `non-positive` for six fixed parameters.

## Evidence

Ktor version, everything else held constant. Confirmed twice in each direction:

| Ktor | leaked lines |
| --- | --- |
| 3.5.2 | 315 |
| 3.4.2 | 0 |

Also ruled out by individual revert, each still leaking 315: jeed core 2026.9.1 to 2026.4.0,
jenisol and libcs1 2026.9.0 to 2026.4.0, kotlinx-coroutines 1.11.0 to 1.10.2, the
`com.ryandens.javaagent-test` plugin 0.12.2 to 0.10.0, and everything in the root build file
including Kotlin 2.4.20 and Gradle 9.7.1.

Isolating the three layers of `TestSimpleIfElseServer` into separate specs:

| probe | leaked |
| --- | --- |
| Layer 1 alone, direct `question.test()` | 0 |
| Layer 2 alone, `submission.test(question)`, no HTTP | 0 |
| Layer 3 alone, `testApplication` HTTP | 0 |
| Layer 1 + Layer 2 | 0 |
| Layer 1 twice | 0 |
| Layer 1 + Layer 3 | 315 |
| Layer 2 + Layer 3 | 315 |
| Layer 3 + Layer 3 | 315 |

The rule: **any HTTP-path execution that is not the first execution in the JVM leaks.** No amount of
non-HTTP execution triggers it, and the first HTTP execution is clean.

## What Is Established About The Mechanism

Instrumenting `System.out` and the current thread group across executions in one JVM:

```
start:       System.out=org.gradle.internal.io.LinePerThreadBufferingOutputStream
after L1:    System.out=edu.illinois.cs.cs125.jeed.core.Sandbox$RedirectingPrintStream
after L3 #0: System.out=edu.illinois.cs.cs125.jeed.core.Sandbox$RedirectingPrintStream
after L3 #1: System.out=edu.illinois.cs.cs125.jeed.core.Sandbox$RedirectingPrintStream
```

So `RedirectingPrintStream` is installed on first use and stays installed, which is by design since
it routes per thread group.

The escape path is `RedirectingPrintStream.taskPrintStream` in `core/src/main/kotlin/Sandbox.kt`:

```kotlin
val confinedTask = confinedTaskByThreadGroup() ?: return (
    originalPrintStreams[console] ?: error("original console should exist")
    )
```

and `confinedTaskByThreadGroup()` at `Sandbox.kt:877`:

```kotlin
val confinedThreadGroup = Thread.currentThread().threadGroup as? ConfinedThreadGroup ?: return null
```

When the printing thread's group is not a `ConfinedThreadGroup`, output goes to
`originalPrintStreams[STDOUT]`, the stream captured at `Sandbox.start()`. In the test JVM that is
Gradle's stream; in the container it is the Docker log.

## The Open Question

Why does a print escape the confined group only under Ktor 3.5, and only after a first execution?

`Sandbox.kt:637` creates the task thread inside a `ConfinedThreadGroup`
(`Thread(threadGroup, task, "Jeed Sandbox Thread $startedTaskCount")`), so the task thread itself
should be confined. Something in the execution path is printing from a thread that is not.

Two Ktor 3.5.0 changelog entries look relevant, both about which thread runs the handler:

- **KTOR-6462**, "Ktor clients and servers should use `Dispatchers.IO.limitedParallelism(...)`
  wherever possible"
- **KTOR-9542**, "Netty: The request handler runs on worker event loop instead of call event loop
  since 3.4.3"

The working hypothesis is that the handler now runs on shared `Dispatchers.IO` pool threads rather
than Ktor's own, and a pooled thread reused across executions carries a thread group that is either
not a `ConfinedThreadGroup` or belongs to an execution that has already finished. That would explain
why the first execution is clean and later ones are not. It is a hypothesis; it has not been
instrumented.

One more detail worth chasing: **315 escaped lines is a lot** for a question with six fixed
parameters. That suggests something is running the whole test sweep unconfined, possibly the
reference solution rather than the submission, rather than a stray line here and there.

## Not In Scope Here

Separately, jeed 2026.9.1 declares `api("org.slf4j:slf4j-jdk14")`, which collides with questioner's
`logback-classic` and puts two SLF4J providers on the server classpath. That is being fixed on the
questioner side by moving to `configureJeedLogging`. It is unrelated to this leak; the leaked lines
are bare, with no log formatting.

## A Hook That Already Exists

`Sandbox.checkOutputStreams()` already detects the adjacent failure mode, where something replaces
Jeed's streams after `start()`, and either warns or throws `SandboxOutputStreamsReplaced` depending
on `failOnReplacedStreams`. That is not this bug, but it is the natural place to look for where an
equivalent assertion about thread-group routing would live.

Worth knowing if you try to write a regression test on the questioner side: you cannot. `start()` is
`@Synchronized` and returns early `if (running)`, capturing `originalStdout`/`originalStderr` once,
and by the time any Kotest hook runs the capture has already happened. Three detector designs were
tried from questioner's test code and all three observed nothing while 315 lines went to the console:

1. `System.setOut` inside the test body, before the first sandbox execution.
2. A tee installed from `ProjectConfig.beforeProject`, which does run before any spec.
3. The same tee on both `System.out` and `System.err`.

Worse, a tee installed this way *replaces* Jeed's redirecting streams, which is exactly what
`checkOutputStreams` warns about, so the detector breaks the routing it is trying to measure. A
regression test for this almost certainly has to live in jeed, where the sandbox internals are
reachable.

## Result Of The JEED_DEBUG_OUTPUT_LEAKS Diagnostic Build

The diagnostic build was run against the standing repro. **It produced zero reports while 315 lines
still leaked.**

Verified before drawing that conclusion:

- The diagnostic jar is the one loaded. The JVM's security-manager warning names
  `file:/Users/challen/.m2/repository/org/cs124/jeed/core/2026.9.1/core-2026.9.1.jar`, so
  `mavenLocal()` won and it is not the artifact from the remote repository. `--refresh-dependencies`
  was needed, since Gradle had the same version number cached from the remote.
- `JEED_DEBUG_OUTPUT_LEAKS=true` reaches the forked test JVM. A probe spec read
  `System.getenv("JEED_DEBUG_OUTPUT_LEAKS")` and got `true`. Note that Gradle test workers inherit
  the *daemon's* environment, so `VAR=x ./gradlew ...` alone does not reliably reach them; the run
  above forwards it explicitly from the root build file.
- The leak still reproduces under the diagnostic build: 315 lines, 0 test failures.

So the instrumentation is active and the writes are not passing through it. Two readings, and the
distinction matters:

1. `reportUnconfinedWrite` returns early on `activeTasks.get() == 0`. If the leaked writes happen
   when no task is active, they are invisible to it. That would mean the output is not escaping
   *during* a confined execution at all, but being flushed afterwards, which fits the buffering
   hazard described in the `RedirectingPrintStream` comment: content left in a `PrintStream`'s
   internal buffers and spewed at whoever uses the stream next.
2. The writes never reach the host fall-through path being instrumented, because whatever is
   printing holds a direct reference to the original stream rather than going through
   `RedirectingPrintStream`.

Dropping the `activeTasks.get() == 0` condition, and reporting unconditionally, would separate these
two cheaply. If reports appear, it is reading 1 and the interesting question is what flushes them. If
still nothing, it is reading 2 and the instrumentation needs to sit on the original streams
themselves rather than on the fall-through.

## Second Diagnostic Run: Reading 2 Confirmed, Plus A Surprise

The republished build was run as `-PjeedDebugOutputLeaks=positive`. Result: **315 lines still leaked,
zero reports, and the startup banner never appeared.**

Everything needed to trust that was verified by reflecting on the live `Sandbox` object from a spec
that had already performed a real execution, so the agent was active and initialization had
succeeded:

```
debugOutputLeaks   = positive
running            = true
hostPrintStreams   = STDOUT=java.io.PrintStream@413409770, STDERR=java.io.PrintStream@44047445
originalPrintStreams = STDOUT=org.gradle.internal.io.LinePerThreadBufferingOutputStream@1447218435, ...
hostIsSameObjectAsOriginal = false
```

So: the switch reaches the sandbox, the sandbox is running, and `hostPrintStreams` really is the
wrapped `LeakReportingStream` rather than the raw original. The instrumentation is installed and the
fall-through at `Sandbox.kt:2182` does return `hostPrintStreams[console]`. The writes still do not
go through it.

**That is reading 2.** The leaked output never traverses `RedirectingPrintStream` at all.

The missing banner sharpens this considerably, and is worth more attention than the missing reports.
It is printed to `originalStderr` inside `start()`, in the same block and immediately after the
wrapping that demonstrably happened. So the `println` ran. It never showed up in the build output,
while 315 bare lines from the same JVM did. Writes to the captured Gradle stream objects are not
surfacing, and the leaked lines are surfacing, so the leaked lines are not going through those
objects either. Something is reaching the process's real stdout by a route that bypasses both Jeed's
redirect and the `System.out` object Gradle installed, which would fit a
`FileOutputStream(FileDescriptor.out)` or an equivalent native path rather than anything holding a
`PrintStream` reference.

Suggested next probe: forget the redirect and instrument the descriptor. If the banner can be made to
appear at all, that alone tells you which stream object actually reaches the terminal in a Gradle
test worker, and the same answer probably explains where the 315 lines come from.

### One Gotcha Worth Keeping

Gradle test workers inherit the *daemon's* environment, not the invoking shell's, so
`JEED_DEBUG_OUTPUT_LEAKS=x ./gradlew ...` does not reliably reach them. The runs above forward an
invocation-scoped project property from questioner's root build file:

```kotlin
(findProperty("jeedDebugOutputLeaks") as String?)?.let { environment["JEED_DEBUG_OUTPUT_LEAKS"] = it }
```

Confirmed reaching the worker by reading `System.getenv` from inside a spec. Also: republishing under
an unchanged version needs `--refresh-dependencies` on the questioner side, or Gradle keeps serving
the previously resolved artifact.

## Root Cause Found: jansi, Via ktor-server-call-logging

The calibration was right, and it led straight to the answer. Following the decision tree on the
tagged build: **no child processes** at start or at any task release, one `Sandbox` on the app class
loader, and `System.out` captured at start was Gradle's own
`LinePerThreadBufferingOutputStream` — so no second copy of Sandbox, and the writer is in-process
using the descriptor directly.

Scanning every jar on questioner's `:server` test runtime classpath for `java/io/FileDescriptor`
references gave seven candidates: `virtualfsplugin`, the resource `agent`, `jeed core`,
`kotlin-compiler-embeddable`, **`jansi-2.4.3`**, and Netty's kqueue and epoll transports. Questioner's
own sources are clean: the only `ProcessBuilder` is in `ValidationServerManager`, and it pipes rather
than inheriting.

`jansi` arrives here:

```
org.fusesource.jansi:jansi:2.4.3
\--- io.ktor:ktor-server-call-logging-jvm:3.5.2
```

Questioner's server installs `CallLogging`, so it is on the path. Excluding
`org.fusesource.jansi` and changing nothing else:

| jansi | leaked lines |
| --- | --- |
| present | 315 |
| excluded | 0 |
| present again | 315 |
| excluded again | 0 |

**That is the ktor correlation.** Not a threading change at all. `ktor-server-call-logging` gained a
jansi dependency in 3.5, and jansi does native console I/O straight to the file descriptor.

Worth knowing for the jeed side: **jansi never replaces `System.out`.** Probing around the HTTP path,
it stays `Sandbox$RedirectingPrintStream` from the first execution through to the end, which is why
`checkOutputStreams()` correctly stayed quiet. So this is not a stream-replacement bug and the
existing guard was never going to catch it. Anything writing below `System.out` is invisible to
Jeed's redirect by construction, which may argue for a descriptor-level assertion in the sandbox's
own test suite rather than a stream-level one.

The exact path by which sandboxed code's output ends up going through jansi is not established. What
is established is that jansi's presence is necessary and sufficient for the leak, in both directions,
twice.

Questioner now excludes jansi in `server/build.gradle.kts`. Full build green, and the standing repro
reports 0 leaked lines while keeping ktor 3.5.2.


## What This Means For Jeed

Nothing here is urgent, and questioner is unblocked. Three things seem worth a decision.

**1. The guarantee has a gap worth naming.** Jeed's capture is `PrintStream`-level, so anything
writing beneath it reaches the console regardless: native console libraries like jansi, JNI, and child
processes with inherited stdio. Consumers reasonably read "sandboxed output is captured" as absolute.
A sentence in the docs saying where the boundary actually is would have saved this entire
investigation, because the search would have started at the descriptor rather than at the redirect.

**2. A descriptor-level assertion in jeed's own suite would catch the class of problem.** The existing
`checkOutputStreams()` guard covers stream *replacement*, which is a different failure and was
correctly silent here. Something that runs a printing snippet and asserts nothing reached fd1 would
have caught this on jeed's side the moment a dependency like jansi appeared, rather than surfacing as
mystery output in a consumer's production logs. Whether that is worth the cost is your call.

**3. Keep the `JEED_DEBUG_OUTPUT_LEAKS` switch.** It is what cracked this. Specifically the tagged
`[fd2]` / `[host stdout]` / `[host stderr]` routes and the child-process listing: ruling out a spawned
process, and proving `System.out` at start was Gradle's own stream from a single loader, is what
narrowed it to an in-process descriptor writer and made the classpath scan the obvious next move. The
calibration run that established "a bare console line in a Gradle worker is a write to fd1" was the
single most useful step in the whole exercise.

### The One Open Question

The exact path by which sandboxed code's output ends up going through jansi is **not** established.
Established is only that jansi's presence is necessary and sufficient. Two hypotheses, both testable
with the sandbox internals that are reachable on your side but not from questioner:

- Jansi duplicates or takes over fd1 natively when it initializes. If so the output is not escaping
  jeed at all; it is being echoed at the OS level, and jeed's captured copy is intact and separate.
  That would fit every observation, including grading staying correct and `System.out` never changing.
- Something on the execution path resolves the console through jansi rather than through
  `System.out`, in which case there may be a real interception point.

The distinction matters for whether item 2 above is worth building: under the first hypothesis, no
assertion inside jeed can prevent it, only detect it.

### Repro For Testing Any Change

Questioner now excludes jansi, so the leak no longer reproduces there by default. To bring it back,
drop the `exclude(group = "org.fusesource.jansi")` block from `server/build.gradle.kts` and run the
four-second repro at the top of this document.
