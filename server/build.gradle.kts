import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask
import java.io.File

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
    `maven-publish`
    signing
    id("com.gradleup.shadow") version "9.3.0"
    id("org.jmailen.kotlinter")
    id("io.gitlab.arturbosch.detekt")
    id("com.google.devtools.ksp")
    id("com.ryandens.javaagent-test") version "0.10.0"
    id("com.adarshr.test-logger")
}
val agentVersion: String by rootProject.extra
configurations.all {
    resolutionStrategy {
        force("org.slf4j:slf4j-api:2.0.16")
    }
}
dependencies {
    val ktorVersion = "3.3.3"

    testJavaagent("com.beyondgrader.resource-agent:agent:$agentVersion")

    implementation(project(":core"))

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("io.github.nhubbard:konf:2.1.0")
    implementation("com.beyondgrader.resource-agent:agent:$agentVersion")
    implementation("com.beyondgrader.resource-agent:jeedplugin:$agentVersion")

    // Libraries for student use
    implementation("org.cs124:libcs1:2025.12.0")
    implementation("io.kotest:kotest-runner-junit5:6.0.7")
    implementation("com.google.truth:truth:1.4.5")

    testImplementation("io.kotest:kotest-runner-junit5:6.0.7")
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
    systemProperties["logback.configurationFile"] = File(projectDir, "src/test/resources/logback-test.xml").absolutePath
    environment["JEED_USE_CACHE"] = "true"
}
tasks.shadowJar {
    isZip64 = true
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
