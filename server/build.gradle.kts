import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask
import java.io.File

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
    `maven-publish`
    signing
    id("com.gradleup.shadow") version "9.6.1"
    id("org.jmailen.kotlinter")
    id("io.gitlab.arturbosch.detekt")
    id("com.google.devtools.ksp")
    id("com.ryandens.javaagent-test") version "0.12.2"
    id("com.adarshr.test-logger")
    jacoco
}
val agentVersion: String by rootProject.extra
configurations.all {
    resolutionStrategy {
        force("org.slf4j:slf4j-api:2.0.19")
        // konf reaches gson through toml4j, which is abandoned on gson 2.8.1 and so still carries
        // CVE-2022-25647, fixed in 2.8.9.
        force("com.google.code.gson:gson:2.14.0")
        // See the note in core: inherited through plexus-container-default.
        force("org.codehaus.plexus:plexus-utils:4.1.0")
    }
}
dependencies {
    val ktorVersion = "3.5.2"

    testJavaagent("com.beyondgrader.resource-agent:agent:$agentVersion")

    implementation(project(":core"))

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    // konf 2.1.0 is the newest release and pins jackson 2.17.1, which carries advisories fixed in
    // 2.18.8. The BOM keeps the family consistent; jackson-annotations versions differently from
    // the rest, so forcing the artifacts individually needs two version lines kept in step.
    // A plain platform rather than an enforced one: enforced platforms behave like forced
    // dependencies and leak to consumers, which Gradle refuses to publish. This still wins over
    // konf's older BOM, since the higher version carries conflict resolution.
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.22.2"))
    implementation("io.github.nhubbard:konf:2.1.0")
    implementation("com.beyondgrader.resource-agent:agent:$agentVersion")
    implementation("com.beyondgrader.resource-agent:jeedplugin:$agentVersion")

    // Libraries for student use
    implementation("org.cs124:libcs1:2026.9.0")
    implementation("io.kotest:kotest-runner-junit5:6.2.4")
    implementation("com.google.truth:truth:1.4.5")

    testImplementation("io.kotest:kotest-runner-junit5:6.2.4")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}
testlogger {
    slowThreshold = 600000L
}

application {
    mainClass.set("edu.illinois.cs.cs125.jeed.server.MainKt")
}

val dockerName = "cs124/jeed"
tasks.register<Copy>("dockerCopyJar") {
    from(tasks["shadowJar"].outputs)
    into(layout.buildDirectory.dir("docker"))
}
tasks.register<Copy>("dockerCopyDockerfile") {
    from("${projectDir}/Dockerfile")
    into(layout.buildDirectory.dir("docker"))
}
tasks.register<Exec>("dockerBuild") {
    dependsOn("dockerCopyJar", "dockerCopyDockerfile")
    workingDir(layout.buildDirectory.dir("docker"))
    environment("DOCKER_BUILDKIT", "1")
    commandLine(
        ("/usr/local/bin/docker build . " +
            "-t ${dockerName}:latest " +
            "-t ${dockerName}:${project.version}").split(" ")
    )
}
tasks.register<Exec>("dockerPush") {
    dependsOn("dockerCopyJar", "dockerCopyDockerfile")
    workingDir(layout.buildDirectory.dir("docker"))
    commandLine(
        ("/usr/local/bin/docker buildx build . --platform=linux/amd64,linux/arm64/v8 " +
            "--tag ${dockerName}:latest " +
            "--tag ${dockerName}:${project.version} --push").split(" ")
    )
}
tasks.test {
    useJUnitPlatform()
    environment["JEED_USE_CACHE"] = "true"
}
tasks.shadowJar {
    isZip64 = true
    // Shadow's transformers only see duplicates that reach them, and the default EXCLUDE drops
    // them first. Fifteen .kotlin_module names genuinely collide here -- kotlin-reflect overlaps
    // kotlin-compiler-embeddable, and kotlin-logging appears twice -- so excluding them keeps one
    // copy and discards the other module's package listing rather than merging the two.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    // With duplicates now reaching the transformers, merge the service files rather than letting
    // them collide. Five of them ship different providers under the same name -- logback-classic
    // against slf4j-simple, jackson's JavaTimeModule against its KotlinModule, GraalJS against its
    // regex engine -- so keeping one entry per name silently dropped a provider, and duplicate
    // entries inside a single jar are not reliably enumerated by ServiceLoader either.
    mergeServiceFiles()
    // core/src/main/kotlin/KtlintKotlinCompiler.kt is a patched copy of a file that also ships
    // inside ktlint-rule-engine-core, so five class entries under this package exist twice:
    // KtlintKotlinCompiler, KtlintKotlinCompilerKt, LoggerFactory, LoggerFactory$getLoggerInstance$1
    // and FormatPomModel. On a classpath Jeed's copy wins because the core jar precedes ktlint's,
    // but a jar has no such rule: with the INCLUDE strategy above both entries are written and the
    // JDK's zip lookup hands back the last one, which is ktlint's, so the packaged server failed
    // where the tests passed. First-wins for this package restores the classpath ordering. Nothing
    // else here is duplicated, so it only affects those five.
    filesMatching("com/pinterest/ktlint/rule/engine/core/api/**") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    manifest {
        attributes["Launcher-Agent-Class"] = "com.beyondgrader.resourceagent.AgentKt"
        attributes["Can-Redefine-Classes"] = "true"
        attributes["Can-Retransform-Classes"] = "true"
    }
}
java {
    withJavadocJar()
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
sourceSets {
    main {
        kotlin {
            exclude("build/**")
        }
    }
}
afterEvaluate {
    tasks.withType<FormatTask> {
        this.source = this.source.minus(fileTree("build")).asFileTree
    }
    tasks.withType<LintTask> {
        this.source = this.source.minus(fileTree("build")).asFileTree
    }
}
publishing {
    publications {
        create<MavenPublication>("server") {
            artifactId = "server"
            from(components["java"])
            pom {
                name = "jeed"
                description = "Jeed server components for CS 124."
                url = "https://cs124.org"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/license/mit/"
                    }
                }
                developers {
                    developer {
                        id = "gchallen"
                        name = "Geoffrey Challen"
                        email = "challen@illinois.edu"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/cs124-illinois/jeed.git"
                    developerConnection = "scm:git:https://github.com/cs124-illinois/jeed.git"
                    url = "https://github.com/cs124-illinois/jeed"
                }
            }
        }
    }
}
signing {
    setRequired {
        gradle.taskGraph.allTasks.any { it.name.contains("ToSonatype") }
    }
    sign(publishing.publications["server"])
}
tasks.withType<Sign>().configureEach {
    onlyIf {
        gradle.taskGraph.allTasks.any { it.name.contains("ToSonatype") }
    }
}

jacoco {
    // Match the org.jacoco.core version already used by the coverage analysis feature.
    toolVersion = "0.8.15"
}
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}
