import java.io.File
import java.io.StringWriter
import java.util.Properties

plugins {
    kotlin("jvm")
    application
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jmailen.kotlinter")
    id("io.gitlab.arturbosch.detekt")
    id("com.google.devtools.ksp")
    id("com.ryandens.javaagent-test") version "0.5.0"
}
dependencies {
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.0")

    testJavaagent("com.beyondgrader.resource-agent:agent:2023.9.0")

    implementation(project(":core"))

    implementation("io.ktor:ktor-server-netty:2.3.5")
    implementation("io.ktor:ktor-server-cors:2.3.5")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    implementation("com.github.cs124-illinois:ktor-moshi:2023.10.1")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("com.uchuhimo:konf-core:1.1.2")
    implementation("com.uchuhimo:konf-yaml:1.1.2")
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    implementation("com.github.cs124-illinois:libcs1:2023.10.0")
    implementation("com.beyondgrader.resource-agent:agent:2023.9.0")
    implementation("com.beyondgrader.resource-agent:jeedplugin:2023.9.0")

    testImplementation("io.kotest:kotest-runner-junit5:5.7.2")
    testImplementation("io.kotest:kotest-assertions-ktor:4.4.3")
    testImplementation("io.ktor:ktor-server-test-host:2.3.5")
}

application {
    mainClass.set("edu.illinois.cs.cs125.jeed.server.MainKt")
}

tasks.processResources {
    dependsOn("createProperties")
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
        ("docker build . " +
            "-t ${dockerName}:latest " +
            "-t ${dockerName}:${project.version}").split(" ")
    )
}
tasks.register<Exec>("dockerPush") {
    dependsOn("dockerCopyJar", "dockerCopyDockerfile")
    workingDir(layout.buildDirectory.dir("docker"))
    commandLine(
        ("docker buildx build . --platform=linux/amd64,linux/arm64/v8 " +
            "--builder multiplatform " +
            "--tag ${dockerName}:latest " +
            "--tag ${dockerName}:${project.version} --push").split(" ")
    )
}
tasks.test {
    useJUnitPlatform()
    systemProperties["logback.configurationFile"] = File(projectDir, "src/test/resources/logback-test.xml").absolutePath
    environment["JEED_USE_CACHE"] = "true"
}

task("createProperties") {
    doLast {
        val properties = Properties().also {
            it["version"] = project.version.toString()
        }
        File(projectDir, "src/main/resources/edu.illinois.cs.cs125.jeed.server.version")
            .printWriter().use { printWriter ->
                printWriter.print(
                    StringWriter().also { properties.store(it, null) }.buffer.toString()
                        .lines().drop(1).joinToString(separator = "\n").trim()
                )
            }
    }
}

tasks.processResources {
    dependsOn("createProperties")
}

publishing {
    publications {
        create<MavenPublication>("server") {
            from(components["java"])
        }
    }
}
kotlin {
    kotlinDaemonJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

tasks.shadowJar {
    manifest {
        attributes["Launcher-Agent-Class"] = "com.beyondgrader.resourceagent.AgentKt"
        attributes["Can-Redefine-Classes"] = "true"
        attributes["Can-Retransform-Classes"] = "true"
    }
}
afterEvaluate {
    tasks.named("formatKotlinGeneratedByKspKotlin") {
        enabled = false
    }
    tasks.named("lintKotlinGeneratedByKspKotlin") {
        enabled = false
    }
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
