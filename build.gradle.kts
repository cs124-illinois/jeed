import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.serialization") version "2.4.10" apply false
    id("org.jmailen.kotlinter") version "5.7.0" apply false
    id("io.github.ben-manes.versions") version "0.61.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("com.google.devtools.ksp") version "2.3.11" apply false
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    id("com.adarshr.test-logger") version "4.0.0" apply false
}
extra.set("agentVersion", "2026.1.2")
allprojects {
    group = "org.cs124.jeed"
    version = "2026.9.3"
}
subprojects {
    tasks.withType<Test> {
        useJUnitPlatform()
        enableAssertions = true
        jvmArgs(
            "-ea", "--enable-preview", "-Dfile.encoding=UTF-8", "-Djava.security.manager=allow",
            "-Xms512m", "-Xmx2g", "-Xss256k",
            "-XX:+UseZGC", "-XX:ZCollectionInterval=8", "-XX:-OmitStackTraceInFastThrow",
            "-XX:+UnlockExperimentalVMOptions", "-XX:-VMContinuations", //  "-XX:+CrashOnOutOfMemoryError",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "--add-exports", "java.management/sun.management=ALL-UNNAMED"
        )
    }
    tasks.withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    tasks.withType<KotlinCompile> {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
    }
    tasks.withType<Detekt> {
        jvmTarget = "21"
    }
    afterEvaluate {
        tasks.withType<LintTask> {
            dependsOn(tasks.matching { it.name == name.replace("lint", "format") })
        }
    }
}
tasks.dependencyUpdates {
    fun String.isNonStable() = !(
        listOf("RELEASE", "FINAL", "GA").any { uppercase().contains(it) }
            || "^[0-9,.v-]+(-r|-jre)?$".toRegex().matches(this)
        )
    rejectVersionIf { candidate.version.isNonStable() }
    gradleReleaseChannel = "current"
}
detekt {
    buildUponDefaultConfig = true
}
tasks.register("check") {
    dependsOn("detekt")
}
nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
        }
    }
}
val publishToSonatypeTasks = listOf(":core:publishToSonatype", ":server:publishToSonatype")
val verificationTasks = listOf(":core:build", ":server:build")

// A release cannot be taken back, so nothing reaches Maven Central that has not been through the
// tests, lint and detekt first. The publication tasks only depend on the jars, so this has to be
// ordered explicitly, and there is no point opening a staging repository for a build that fails.
subprojects {
    tasks.matching { it.name == "publishToSonatype" }.configureEach {
        mustRunAfter(verificationTasks)
    }
}
tasks.named("initializeSonatypeStagingRepository") {
    mustRunAfter(verificationTasks)
}

// The staging repository can only be closed once everything has been uploaded into it.
tasks.named("closeAndReleaseSonatypeStagingRepository") {
    mustRunAfter(publishToSonatypeTasks)
}
tasks.register("publish") {
    group = "publishing"
    description =
        "Builds and tests core and server, uploads them to Maven Central, then closes and releases " +
        "the staging repository."
    dependsOn(verificationTasks)
    dependsOn(publishToSonatypeTasks)
    dependsOn("closeAndReleaseSonatypeStagingRepository")
}
