# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Jeed is a fast Java and Kotlin compilation, execution, and analysis toolkit designed for in-memory compilation and secure sandbox execution. It's heavily used by CS 124 (CS1 course) at the University of Illinois.

## Development Commands

### Building and Testing
```bash
# Build the entire project
./gradlew build

# Run all tests
./gradlew test

# Run tests for specific modules
./gradlew :core:test
./gradlew :server:test

# Run tests continuously (watch mode)
./gradlew test --continuous
```

### Code Quality
```bash
# Check Kotlin formatting
./gradlew kotlinterCheck

# Auto-format Kotlin code
./gradlew kotlinterFormat

# Run static analysis (Detekt)
./gradlew detekt

# Run all checks (includes detekt)
./gradlew check
```

### Running the Server
```bash
# Build and run the Jeed server
./gradlew :server:build
./gradlew :server:run
```

### JavaScript Development
```bash
# Start backend services for JS development
cd js
npm run backend
```

## Architecture Overview

### Multi-Module Structure
- **core/**: Main compilation, execution, and analysis engine
  - Compilation: `Compile.kt` (Java), `Kompile.kt` (Kotlin), `Mompile.kt` (Mixed)
  - Execution: `Execute.kt`, `Sandbox.kt` (secure execution environment)
  - Analysis: Complexity, features, mutations, and coverage analysis
  - Formatting: Google Java Format, KtLint integration
- **server/**: Ktor-based HTTP API wrapper
- **js/**: JavaScript/TypeScript packages (React components, types, demo)

### Key Design Patterns
1. **Security-First**: All code execution happens in a sandboxed JVM with strict resource limits
2. **Parser Infrastructure**: ANTLR4-based parsers with custom grammar modifications in `/resources`
3. **Caching**: Caffeine cache for compilation results
4. **Testing**: Kotest framework with extensive test coverage

### Important Files and Locations
- Parser grammars: `/core/src/main/resources/`
- Generated parser code: `/core/src/main/gen/`
- Test resources: `/core/src/test/resources/`
- Server configuration: `/server/src/main/resources/`
- Reference sources: `externals/` holds read-only reference checkouts as Git submodules. `externals/kotlin` is the Kotlin compiler source at the exact tag Jeed embeds (v2.4.20). It is never built and sits on no classpath—it exists because `Kompile.kt` composes non-public compiler pipeline APIs and the real source is the only reliable documentation for them. Fetch it only when you need to read compiler internals: `git submodule update --init --depth 1 externals/kotlin` (about 700MB). When bumping Kotlin, keep the tag in step with `kotlin-compiler-embeddable` in `core/build.gradle.kts`: `git -C externals/kotlin fetch --depth 1 origin tag vX.Y.Z && git -C externals/kotlin checkout vX.Y.Z`.

### Versioning
The project uses date-based versioning: YYYY.M.P (e.g., 2025.6.0)

`version` in the root `build.gradle.kts` is the only place a version is set. Every JS package under
`js/` carries the same version, and so does every dependency one has on another, since npm publish
needs exact versions there. After bumping it, run `./gradlew syncJsVersions`. `./gradlew check`,
`./gradlew publish` and `:server:dockerPush` all fail until the JS packages agree.

### Environment Requirements
- Java: OpenJDK 21
- Kotlin: 2.4.20
- Node.js: 24.4.0 (for JS components)
- Gradle: 9.x with Kotlin DSL

## Dependency Management

- Always specify exact dependencies in package.json files, avoiding ~ or ^ version prefixes
- Use `./gradlew dependencyUpdates` to check for available dependency updates
