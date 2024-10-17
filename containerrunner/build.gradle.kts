import java.util.Properties
import java.io.StringWriter
import java.io.File

plugins {
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jmailen.kotlinter")
    id("io.gitlab.arturbosch.detekt")
}
configurations.all {
    resolutionStrategy {
        force("org.slf4j:slf4j-api:2.0.16")
    }
}
dependencies {
    implementation("ch.qos.logback:logback-classic:1.5.11")
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    implementation("com.github.ajalt.clikt:clikt:5.0.1")
    implementation("io.github.classgraph:classgraph:4.8.177")
}
application {
    mainClass.set("edu.illinois.cs.cs125.jeed.containerrunner.MainKt")
}
tasks.test {
    useJUnitPlatform()
    systemProperties["logback.configurationFile"] = File(projectDir, "src/test/resources/logback-test.xml").absolutePath
}
task("createProperties") {
    doLast {
        val properties = Properties().also {
            it["version"] = project.version.toString()
        }
        File(
            projectDir,
            "src/main/resources/edu.illinois.cs.cs125.jeed.containerrunner.version"
        )
            .printWriter().use { printWriter ->
                printWriter.print(
                    StringWriter().also { properties.store(it, null) }.buffer.toString()
                        .lines().drop(1).joinToString(separator = "\n").trim()
                )
            }
    }
}
val dockerName = "cs124/jeed-containerrunner"
tasks.processResources {
    dependsOn("createProperties")
}
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
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
