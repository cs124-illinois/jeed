import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.23" apply false
    id("org.jmailen.kotlinter") version "4.1.1" apply false
    id("com.github.ben-manes.versions") version "0.51.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.5"
    id("com.google.devtools.ksp") version "1.9.23-1.0.19" apply false
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
    id("com.autonomousapps.dependency-analysis") version "1.30.0"
}
allprojects {
    group = "org.cs124.jeed"
    version = "2024.3.2"
}
subprojects {
    tasks.withType<Test> {
        useJUnitPlatform()
        enableAssertions = true
        jvmArgs(
            "-ea", "--enable-preview", "-Dfile.encoding=UTF-8",
            "-Xms512m", "-Xmx1G", "-Xss256k",
            "-XX:+UseZGC", "-XX:ZCollectionInterval=8",
            "-XX:-OmitStackTraceInFastThrow",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "--add-exports", "java.management/sun.management=ALL-UNNAMED"
        )
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_17.toString()
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
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
}
