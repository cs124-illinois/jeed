# Jeed: A Fast Java and Kotlin Compliation, Execution, and Analysis Toolkit

[![Docker Image Version (latest by date)](https://img.shields.io/docker/v/cs125/jeed?color=green&label=Docker&sort=date)](https://hub.docker.com/r/cs125/jeed/tags)
[![npm version](https://badge.fury.io/js/%40cs124%2Fjeed-react.svg)](https://badge.fury.io/js/%40cs124%2Fjeed-react)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Jeed is a speedy Java and Kotlin in-memory compilation and execution toolkit.
Jeed can rapidly compile Java and Kotlin code and safely execute the resulting bytecode in a secure sandbox.
Jeed can also perform a wide and growing number of Java and Kotlin source code analysis tasks.

Jeed is heavily used by [CS 124](https://www.cs124.org), the CS1 course at the
University of Illinois.
An [online demo is also available](https://cs124-illinois.github.io/jeed/).

## Reference Sources

`externals/` holds read-only reference checkouts as Git submodules, and `externals/kotlin` is the
[Kotlin compiler source](https://github.com/JetBrains/kotlin) at the exact tag Jeed embeds (v2.4.20).
It is never built and sits on no classpath—Jeed's in-memory Kotlin compilation composes non-public
compiler pipeline APIs, and the source is the only reliable documentation for them.
Fetch it only when you need to read those internals, with
`git submodule update --init --depth 1 externals/kotlin`, and keep its tag in step with the
`kotlin-compiler-embeddable` version in `core/build.gradle.kts` whenever Kotlin is upgraded.
