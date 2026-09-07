# Changelog

## Unreleased

### Added

- The sandbox now checks, before and after every task, that `System.out` and `System.err` are still
  the streams it installed, and probes any replacement to see whether it still forwards to them. A
  host that replaces or wraps them after the sandbox has started sends every sandboxed print through
  its replacement. A wrapper that forwards still captures correctly but also copies the output
  wherever the wrapper goes, which is how sandboxed output can turn up in a server's own logs; each
  distinct one is logged once as a warning, or fails the task when `Sandbox.failOnReplacedStreams`
  or `JEED_FAIL_ON_REPLACED_STREAMS=true` is set. A replacement that does not forward means nothing
  sandboxed code prints can be captured at all, so the task always fails with
  `Sandbox.SandboxOutputStreamsReplaced`. jansi's `AnsiConsole.systemInstall()` is the known case:
  its streams write to the file descriptor directly, and ktor 3.5's `CallLogging` plugin calls it
  whenever colours are enabled.
- The sandbox warns at start when `System.out` or `System.err` is already one of jansi's streams,
  since output it routes to the host will then bypass anything capturing `System.out`.

### Where the capture boundary is

The sandbox captures output by owning the `System.out` and `System.err` streams and routing each
write by the thread that made it. Anything that writes below those streams is outside its reach:
native console libraries such as jansi, JNI, or a child process with inherited descriptors. The
checks above detect the case where the streams themselves are displaced; they cannot see writes that
never pass through them.
- `JEED_DEBUG_OUTPUT_LEAKS` reports writes that reach the host's stdout or stderr through the
  sandbox's redirect, which is where writes from threads outside any confined thread group end up.
  With the value `true`, every such write while a task is running is reported; with any other
  value, every write containing that text is reported whether or not a task is running. Each
  distinct call site is reported once, with the writing thread, its thread group and its stack, and
  a banner is printed when the sandbox starts naming the class loader that loaded `Sandbox`, the
  streams it captured, and any child processes. Everything is written through three tagged routes,
  the captured host stdout, the captured host stderr, and the process's real stderr descriptor,
  since a test harness such as Gradle's shows only some of these on its console. Off by default,
  since each reported write pays for a stack walk.

### Fixed

- `jeed-core` no longer prints `kotlin-logging: initializing... active logger factory: ...` to the
  host process's stdout the first time it creates a logger. kotlin-logging 8 turns that startup
  line on by default; Jeed now turns it off unless the embedder has set the library's own
  `kotlin-logging.logStartupMessage` system property or `KOTLIN_LOGGING_STARTUP_MESSAGE`
  environment variable.

### Tests

- Added a regression test that runs sandboxed code through every printing path (plain output,
  student threads, static initializers, parallel streams against a warm common pool, Kotlin and
  coroutines, stdin echo, trusted-task redirection, concurrent tasks, and a task killed
  mid-print) with the real stdout and stderr captured underneath the sandbox, and checks that
  nothing sandboxed reaches them.
- Added server tests that do the same through HTTP, on both the ktor test engine and Netty, after
  an earlier execution in the same JVM, including a handler shaped the way questioner drives jeed:
  several executions per request under `redirectOutput`, a reference solution loaded outside the
  sandbox, and the server's plugins attached.

## 2026.9.1

Everything since 2026.4.0, the last version published to Maven Central. 2026.9.0 was an
intermediate version that reached Docker Hub but not Maven Central, so its changes are folded in
here.

### Breaking

**Container execution has been removed.** Jeed could run compiled code inside a Docker container
rather than the in-process sandbox. That path had been broken for some time: the
`cs124/jeed-containerrunner` image stopped tracking Jeed's version in March 2025, while the default
image name interpolated the current version, so it had been resolving to a tag that did not exist.

- `Task.cexecute` and the `cexecution` request and response fields are gone from the HTTP API.
  `cexecute` is no longer an accepted task.
- `ContainerExecutionArguments`, `ContainerExecutionResults` and `checkDockerEnabled` are gone from
  `jeed-core`, along with `withTempDir`, `eject`, `isInside`, `runCommand` and `StreamGobbler`,
  which existed only to support it.
- The `Limits.Cexecution` server configuration section is gone; remove it from any `config.yaml`
  that sets it.
- `ContainerExecutionArguments`, `ContainerExecutionResults` and the `cexecution` fields are gone
  from `@cs124/jeed-types`.
- The `cs124/jeed-containerrunner` image has been deleted from Docker Hub.
- `JEED_DOCKER_DISABLED` no longer does anything.

**`jeed-core` no longer brings a logging backend.** logback has been replaced by `slf4j-jdk14`,
which routes SLF4J into `java.util.logging`. Embedders who relied on logback arriving transitively
through `jeed-core` will get no output until they add a backend of their own, or call
`configureJeedLogging` to install Jeed's. SLF4J itself is unchanged and still required.

**`KompilationArguments.useK2` is ignored.** Kotlin 2.4 removed the K1 compiler, so there is no
longer another frontend to select. The property is retained so that existing requests carrying it
still deserialize, but setting it to `false` has no effect.

**kotlin-logging moved.** `jeed-core` now exposes `io.github.oshai:kotlin-logging` rather than the
abandoned `io.github.microutils` coordinates, and version 8 removed the logging overloads that take
a value in favour of the lambda form.

### Added

- A `publish` task that uploads core and server to Maven Central and then closes and releases the
  staging repository, in that order.
- `JeedLogFormatter` and `configureJeedLogging` in `jeed-core`, for `java.util.logging` output
  matching the format Jeed used to produce through logback.
- JaCoCo coverage reporting for core and server, via `./gradlew jacocoTestReport`.

### Changed

- Kotlin 2.3.20 to 2.4.10, and Kotlin compilation now runs on the K2 pipeline. Kotlin 2.4 removed
  K1, and the entry point Jeed had been using ran the classic frontend regardless of the `useK2`
  flag, so this is a real change of frontend rather than a version bump.
- Compiler arguments are now actually applied. The `-opt-in` flags Jeed passes had never reached
  the compiler, so experimental APIs behaved as though they were not opted into.
- `-Xcontext-receivers` has been dropped, since Kotlin 2.4 rejects it outright in favour of context
  parameters.
- Unused-variable warnings are reported again. Kotlin 2.4 moved them behind the extra checkers, so
  Jeed now requests those explicitly rather than losing a warning class in the upgrade. This also
  enables K2's other extra checkers, which count as errors for callers using `allWarningsAsErrors`.
- Dependency updates, notably ktor 3.4.2 to 3.5.2, checkstyle 13.4.0 to 14.1.0, kotest 6.1.10 to
  6.2.4, google-java-format 1.35.0 to 1.36.1, jacoco 0.8.14 to 0.8.15, asm 9.9.1 to 9.10.1,
  coroutines and serialization to 1.11.0, libcs1 to 2026.9.0, and Gradle 9.4.1 to 9.7.1.

### Fixed

- Kotlin warnings were not being reported at all. The compiler arguments were never applied, so the
  settings the checkers consult were never derived from them.
- Top-level Kotlin declarations from a previous compilation could not be resolved. Jeed's in-memory
  classpath reported a bare file name as its path, and the compiler attributes a library file to a
  module by prefix-matching that path, so Kotlin metadata was attributed to no module and silently
  dropped. Classes still resolved, because those go through the Java class finder, which is why
  only top-level declarations were affected.
- The shaded server jar was dropping service providers. Five `META-INF/services` files collide with
  different contents across the classpath, and only one of each survived, so which logging backend
  the server used and whether jackson registered both of its modules came down to jar ordering.
- checkstyle ships `slf4j-simple`, which landed a second SLF4J binding in the server jar and ignores
  logging configuration. It is now excluded.

### Security

Rebuilding the published images from this release clears every known critical and high advisory
against them.

| image | before | after |
| --- | --- | --- |
| `cs124/jeed` | 0 critical, 22 high | none |
| `cs124/jeed-proxy` | 6 critical, 53 high | none |
| `cs124/jeed-containerrunner` | 6 critical, 67 high | image deleted |

- `cs124/jeed-proxy` moves to a current Alpine base and drops the npm, npx, yarn and corepack CLIs,
  which the image never used and which were the source of every npm-ecosystem advisory against it.
- Transitive dependencies that ship advisories and have no newer release to upgrade to are now
  pinned: jackson to 2.22.2 through its BOM, gson to 2.14.0, and plexus-utils to 4.1.0.
