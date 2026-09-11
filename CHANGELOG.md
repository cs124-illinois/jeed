# Changelog

## 2026.9.5

Everything since 2026.9.2, the last version to reach Maven Central and Docker Hub. 2026.9.3 and
2026.9.4 have their own entries below but were never published, so their changes ship here as well.

### Changed

- Updated KSP to 2.3.12. Nothing else on the Java or Kotlin side had moved: Gradle, Kotlin, ktlint,
  checkstyle, kotest, ktor, ASM, caffeine, classgraph and the rest are already on their latest
  releases.

### Security

Rebuilding the published images from this release clears every known critical and high advisory
against them. Both were scanned with Docker Scout on 2026-09-11: before is the image Docker Hub
serves, after is this release built locally.

| image | before | after |
| --- | --- | --- |
| `cs124/jeed` | 1 critical, 0 high | none |
| `cs124/jeed-proxy` | 6 critical, 53 high | none |

- netty moves to 4.2.18 through its BOM. ktor 3.5.2, the newest release, pins netty 4.2.16, which
  carries CVE-2026-75595 (critical) and CVE-2026-75596, both fixed in 4.2.17. Drop the BOM once a
  ktor release ships a fixed netty.
- `jeed-core` now publishes plexus-utils 4.1.0 as a dependency. 2026.9.1 pinned it with a forced
  resolution, which fixes only this build, so anything depending on `jeed-core` still resolved 3.1.1
  through plexus-container-default and carried CVE-2025-67030.
- The proxy fix 2026.9.1 describes never reached Docker Hub: nothing was pushed after 2025.12.4,
  which still carries every advisory counted above. The proxy now builds on node 24.21.0, and both
  images are pushed with `--pull --no-cache`, so each release resolves the base image and its OS
  packages afresh instead of reusing an `apk upgrade` layer cached by an earlier one. The proxy's
  build context also ignores `.env` files.

## 2026.9.4

### Changed

- Kotlin moved to 2.4.20, which deletes the legacy K2 CLI pipeline `Kompile.kt` compiled through, so
  it now composes the phased CLI pipeline that replaced it. The frontend phase cannot be called as a
  phase—it collects its sources by walking source roots through the local filesystem—so the project
  environment, library list and sessions are composed from its public members, and the fir2ir and
  backend phases are then called directly. Sources go in as in-memory text through the light tree
  rather than as PSI files, which is the compiler's default and the only mode the backend phase
  accepts for them. Diagnostics keep their line and column numbers, a parse failure still stops
  before anything is resolved, and nothing on the path opens a file for writing: the phase that
  writes class files is never invoked. The one regression is that diagnostics reported during the
  backend phases—`CONFLICTING_JVM_DECLARATIONS`, `ACCIDENTAL_OVERRIDE`, `INLINE_CALL_CYCLE` and the
  rest of `JvmBackendErrors`—now arrive without a line or column, because the compiler only computes
  positions for those against a file that exists on disk. They still count as errors, and frontend
  diagnostics, which are nearly all of them, are unaffected.
- ktlint 1.8.0 does not initialize on Kotlin 2.4.20, so Jeed now carries a patched copy of the one
  ktlint file that breaks. ktlint builds its PSI file factory from a bare `CompilerConfiguration()`,
  and 2.4.20 changed `KotlinCoreEnvironment.configureProjectEnvironment` to read the compiler
  extensions off the configuration, which throws `IllegalStateException: Extensions storage is not
  registered` unless the configuration came from `CompilerConfiguration.create`. Nothing in Jeed can
  reach the configuration ktlint constructs, so `core/src/main/kotlin/KtlintKotlinCompiler.kt` is a
  copy of ktlint's file under ktlint's own package that builds the configuration the new way and is
  otherwise unchanged apart from `MockProject`, whose Kotlin metadata names a supertype 2.4.20 no
  longer shades into `kotlin-compiler-embeddable`. Jeed's classes precede dependency jars on the
  classpath, so this copy loads and the one in the jar does not. The server's shaded jar needed that
  ordering spelled out, since a jar has no first-wins rule and the JDK hands back the last of two
  entries sharing a name—`shadowJar` now resolves duplicates under ktlint's `core.api` package in
  favour of the first and then checks the jar it produced, since that ordering is not something any
  test on a classpath can see. All of this is temporary, and upstream has already fixed it in
  ktlint/ktlint#3289: the bootstrap there drops `KotlinCoreEnvironment` for
  `KotlinCoreProjectEnvironment`, which cannot fail this way. That is in no 1.x release and ships
  first in 2.0.0, which also moves the coordinates to `io.github.ktlint`. Delete the file, its guard
  test and the `shadowJar` clause on the way to either.
- Top-level declarations in a Kotlin source whose name carries a directory now land in the class
  `kotlinc` puts them in. `com/example/Util.kt` produces `com.example.UtilKt` where Jeed used to
  produce `com.example.Com_example_UtilKt`, because it handed the compiler the whole source name as
  the file's *name*, a field that for a file on disk only ever holds the last segment, and the
  directory got mangled into the class name. Stock `kotlinc` produces `com.example.UtilKt` on 2.4.10
  and on 2.4.20 alike, so this is Jeed catching up with the compiler rather than the compiler
  changing. It has a corollary: two sources sharing a last segment and declaring no
  package—`a/Main.kt` and `b/Main.kt`—now fail with the same duplicate JVM class name error
  `kotlinc` gives them, where the mangled names used to hide the clash. Only the file facade moves;
  a class takes its name from its declaration, not from the file. Anything reading generated facade
  names sees the new ones, `byClass` coverage keys included.
- Type-checking `when` expressions keep the bytecode shape they have always had. 2.4.20 generates
  them as an `invokedynamic` to `java.lang.runtime.SwitchBootstraps.typeSwitch` once the JVM target
  is 21, which is Jeed's default on JDK 21. That runs correctly in the sandbox, but it moves the
  whole dispatch onto the line of the `when` subject, so a `when` with no `else` reports its
  unreachable default as a missed branch on that line and the `LAST_WHEN_ENTRY` coverage adjustment
  has nothing left to match. Jeed sets the generation scheme back to the chain of type checks rather
  than hand students a missed line for exhaustive code.
- Dropped `-XXLanguage:+RangeUntilOperator` from the Kotlin compiler arguments. It has done nothing
  since Kotlin 1.8 made the feature stable.
- The Kotlin compiler source is a Git submodule at `externals/kotlin`, pinned to the tag Jeed
  embeds. It is reference only—never built and on no classpath—and is not fetched unless asked for
  with `git submodule update --init --depth 1 externals/kotlin`. `Kompile.kt` composes compiler
  internals that have no documentation beyond their source, so the exact source is kept alongside.

### Tests

- A recording `SecurityManager` now pins the property the in-memory pipeline exists to preserve: a
  compilation records no filesystem write or delete on the calling thread, for Kotlin and for mixed
  sources. It has to warm up with a throwaway compilation first, since the first cache lookup
  initializes the disk cache, which creates its directory under `java.io.tmpdir`.
- Also new: that a syntax error stops before resolution rather than dragging follow-on unresolved
  references along with it, that a warning in a source with Windows line endings still reports the
  right line and column, that a type-checking `when` executes in the sandbox and carries no
  `SwitchBootstraps` bootstrap, and that a source name carrying a directory produces the file facade
  class the last segment of that name implies.
- `TestKtLint` pins where `KtlintKotlinCompiler` is loaded from. The patched copy only takes effect
  because of classpath ordering, and nothing else would notice if that ordering changed—the jar's
  copy would simply load and every ktlint call would fail again—so the test reads the class's code
  source and fails if it points into `ktlint-rule-engine-core`.

## 2026.9.3

### Changed

- Updated classgraph to 4.8.195. Kotlin stays at 2.4.10: 2.4.20 removes the legacy K2 CLI
  pipeline that `Kompile.kt` compiles through, which needs a port to the phased pipeline that
  replaced it rather than a version bump.
- The bytecode rewriter's ASM visitors moved out of `Sandbox.kt` into `SandboxRewriter.kt`, the
  repeated line handling in the output capture was consolidated, and the two longest methods were
  split. No API or behaviour change: `Sandbox.RewriteBytecode` in particular keeps its name, since
  that name is emitted into every class the sandbox rewrites and cached in that form.

### Fixed

- `Sandbox.stop()` now restores the host's `System.in`. `start()` replaces it with the stream that
  routes reads to the running task and saves the original, but `stop()` only ever put `System.out`
  and `System.err` back, so once the sandbox had been stopped anything reading `System.in` outside a
  task failed with "Non-confined tasks should not use System.in".
- How deeply nested a program could be before parsing failed with "Code is too complicated to
  determine complexity" depended on the host's thread stack size and on what the JIT had compiled
  so far, since interpreted and C1-compiled frames are larger than C2 frames. Under the server's
  `-Xss256k` a few dozen chained else-ifs were enough when the JVM was busy, which is also why two
  tests failed only during full runs. Parsing, snippet transformation, complexity, feature and
  mutation analysis, and line counting now run on threads with a fixed 16MB stack, so the limit is
  Jeed's own and does not move, and the parse-tree walks those analyses perform no longer recurse.
  The Java parser also reports an overflow the way the Kotlin one already did, rather than
  letting the error escape.

### Tests

- Sandbox coverage went from 86% of lines and 67% of branches to 94% and 75%. What had no test
  before: every `require` in `ExecutionArguments` and `ClassLoaderConfiguration`, including the
  rejection of an error that can never be safe; the guards that fire when an embedder gets the
  protocol wrong, being `autoStart`, confining one class loader in two tasks at once, and nesting
  `redirectOutput` or `hardLimitOutput`; the four `SandboxControl` members that clear a timeout or
  set both at once; `maxIOBytes`; the `System.err` half of the stream-replacement checks and the
  warning for a jansi stream installed before the sandbox starts; every hook on `SandboxPlugin`,
  three of which had no implementation in this repository at all; `notify()` in rewritten bytecode;
  and the `JEED_DEBUG_OUTPUT_LEAKS` reporting, which is now reachable from a test.
- `RedirectingPrintStream` overrides the whole `PrintStream` API so that nothing falls through to
  the stream that writes nowhere, and 23 of those overrides had never been called by a test. One
  now exercises all of them and pins the output. Note that it is not a strong test of any single
  override, since the inherited implementations funnel back through the ones that remain.

## 2026.9.2

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
