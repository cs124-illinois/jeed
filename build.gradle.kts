import groovy.json.JsonSlurper
import io.gitlab.arturbosch.detekt.Detekt
import java.io.File
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
    kotlin("jvm") version "2.4.20" apply false
    kotlin("plugin.serialization") version "2.4.20" apply false
    id("org.jmailen.kotlinter") version "5.7.0" apply false
    id("io.github.ben-manes.versions") version "0.61.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("com.google.devtools.ksp") version "2.3.12" apply false
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    id("com.adarshr.test-logger") version "4.0.0" apply false
}
extra.set("agentVersion", "2026.1.2")
allprojects {
    group = "org.cs124.jeed"
    version = "2026.9.5"
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
// Every JS package under js/ carries the backend's version, and so does every dependency one of
// them has on another: npm publish, which publish:all uses, needs an exact version there and does not
// understand workspace: specs. They were bumped by hand to match and drifted, the backend reaching
// 2026.9.5 with every package still on 2026.9.1, so neither the npm packages nor the proxy image moved
// with the server. syncJsVersions writes the version in, editing the text so each file keeps its
// formatting, and checkJsVersions gates check, publish and :server:dockerPush on the result.
@Suppress("UNCHECKED_CAST")
fun readPackageJson(file: File) = JsonSlurper().parse(file) as Map<String, Any?>

@Suppress("UNCHECKED_CAST")
fun jsPackageFiles(jsRoot: File) = (readPackageJson(File(jsRoot, "package.json"))["workspaces"] as List<String>)
    .map { File(jsRoot, "$it/package.json") }

// Only entries that parse as dependencies on a sibling package are touched. A textual match on names
// alone would also hit an unrelated key: "proxy" is a dev-server setting in a web app's package.json.
@Suppress("UNCHECKED_CAST")
fun jsInternalDependencies(json: Map<String, Any?>, names: Set<String>): Map<String, String> =
    listOf("dependencies", "devDependencies", "peerDependencies")
        .flatMap { (json[it] as Map<String, String>?).orEmpty().entries }
        .filter { it.key in names }
        .associate { it.key to it.value }

fun jsVersionMismatches(jsRoot: File, version: String): List<String> {
    val files = jsPackageFiles(jsRoot)
    val names = files.map { readPackageJson(it)["name"] as String }.toSet()
    return files.flatMap { file ->
        val json = readPackageJson(file)
        val path = file.relativeTo(jsRoot.parentFile).path
        val own = (json["version"] as String?).takeIf { it != version }?.let { "$path is at $it" }
        listOfNotNull(own) + jsInternalDependencies(json, names)
            .filterValues { it != version }
            .map { (name, spec) -> "$path depends on $name at $spec" }
    }
}

tasks.register("syncJsVersions") {
    group = "versioning"
    description = "Writes the build's version into every JS package and the versions they depend on one another at."
    val jsRoot = file("js")
    val version = project.version.toString()
    doLast {
        val files = jsPackageFiles(jsRoot)
        val names = files.map { readPackageJson(it)["name"] as String }.toSet()
        fun entry(key: String, value: String) = "\"$key\": \"$value\""
        files.forEach { file ->
            val json = readPackageJson(file)
            val original = file.readText()
            var text = original.replaceFirst(entry("version", json["version"] as String), entry("version", version))
            jsInternalDependencies(json, names).forEach { (name, spec) ->
                text = text.replace(entry(name, spec), entry(name, version))
            }
            if (text != original) {
                file.writeText(text)
                logger.lifecycle("Updated ${file.relativeTo(jsRoot.parentFile)}")
            }
        }
        val left = jsVersionMismatches(jsRoot, version)
        if (left.isNotEmpty()) {
            throw GradleException(
                "Could not rewrite these, so their formatting must be unusual:\n" + left.joinToString("\n") { "  $it" },
            )
        }
    }
}

tasks.register("checkJsVersions") {
    group = "verification"
    description = "Fails unless every JS package, and every dependency they have on one another, is on the build's version."
    val jsRoot = file("js")
    val version = project.version.toString()
    doLast {
        val mismatches = jsVersionMismatches(jsRoot, version)
        if (mismatches.isNotEmpty()) {
            throw GradleException(
                "The JS packages are not on this build's version, $version:\n" +
                    mismatches.joinToString("\n") { "  $it" } +
                    "\nRun ./gradlew syncJsVersions and commit what it changes.",
            )
        }
    }
}
tasks.register("check") {
    dependsOn("detekt", "checkJsVersions")
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
val verificationTasks = listOf(":core:build", ":server:build", ":checkJsVersions")

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
