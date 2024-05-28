import java.io.File
import java.io.StringWriter
import java.util.Properties
import org.gradle.internal.os.OperatingSystem
import org.jmailen.gradle.kotlinter.tasks.FormatTask
import org.jmailen.gradle.kotlinter.tasks.LintTask

plugins {
    kotlin("jvm")
    antlr
    java
    `maven-publish`
    signing
    id("org.jmailen.kotlinter")
    id("io.gitlab.arturbosch.detekt")
    id("com.google.devtools.ksp")
    id("com.ryandens.javaagent-test") version "0.5.1"
}
val agentVersion: String by rootProject.extra
dependencies {
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")

    antlr("org.antlr:antlr4:4.13.1")

    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.puppycrawl.tools:checkstyle:10.17.0")
    implementation("org.codehaus.plexus:plexus-container-default:2.1.1")
    implementation("com.pinterest.ktlint:ktlint-rule-engine:1.2.1")
    implementation("com.pinterest.ktlint:ktlint-ruleset-standard:1.2.1")
    implementation("com.github.jknack:handlebars:4.4.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-tree:9.7")
    implementation("org.ow2.asm:asm-util:9.7")
    implementation("ch.qos.logback:logback-classic:1.5.6")
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    implementation("io.github.classgraph:classgraph:4.8.172")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")
    implementation("com.google.googlejavaformat:google-java-format:1.22.0")
    implementation("net.sf.extjwnl:extjwnl:2.0.5")
    implementation("net.sf.extjwnl:extjwnl-data-wn31:1.2")

    api("org.jacoco:org.jacoco.core:0.8.12")
    api("com.github.ben-manes.caffeine:caffeine:3.1.8")

    testImplementation("io.kotest:kotest-runner-junit5:5.9.0")
    testImplementation("com.beyondgrader.resource-agent:agent:$agentVersion")
    testJavaagent("com.beyondgrader.resource-agent:agent:$agentVersion")
}
tasks.test {
    useJUnitPlatform()
    if (JavaVersion.current() >= JavaVersion.VERSION_15) {
        jvmArgs("-ea", "-Xmx4G", "-Xss256k", "--enable-preview", "-XX:+UseZGC")
    } else if (JavaVersion.current() >= JavaVersion.VERSION_11) {
        jvmArgs("-ea", "-Xmx4G", "-Xss256k", "--enable-preview")
    } else {
        jvmArgs("-ea", "-Xmx4G", "-Xss256k")
    }
    systemProperties["logback.configurationFile"] = File(projectDir, "src/test/resources/logback-test.xml").absolutePath
    @Suppress("MagicNumber")
    environment["JEED_MAX_THREAD_POOL_SIZE"] = 4
    if (!OperatingSystem.current().isWindows) {
        environment["PATH"] = "${environment["PATH"]}:/usr/local/bin/"
        environment["JEED_CONTAINER_TMP_DIR"] = "/tmp/"
    }

    if (OperatingSystem.current().isWindows) {
        exclude("**/TestContainer.class")
    }
}
tasks.generateGrammarSource {
    dependsOn("copyJavaGrammar")
    exclude("src/main/antlr/edu/illinois/cs/cs125/jeed/antlr/lib")
    outputDirectory = File(projectDir, "src/main/java/edu/illinois/cs/cs125/jeed/core/antlr")
    arguments.addAll(
        listOf(
            "-visitor",
            "-package", "edu.illinois.cs.cs125.jeed.core.antlr",
            "-Xexact-output-dir",
            "-lib", "src/main/antlr/edu/illinois/cs/cs125/jeed/antlr/lib/"
        )
    )
}
tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource, "createProperties")
}
tasks.compileTestKotlin {
    dependsOn(tasks.generateTestGrammarSource)
}
afterEvaluate {
    tasks.named("kspKotlin") {
        dependsOn(tasks.generateGrammarSource)
    }
    tasks.named("kspTestKotlin") {
        dependsOn(tasks.generateTestGrammarSource)
    }
    tasks.named("formatKotlinTest") {
        dependsOn(tasks.generateTestGrammarSource)
    }
    tasks.named("lintKotlinTest") {
        dependsOn(tasks.generateTestGrammarSource)
    }
}
task("createProperties") {
    dependsOn(tasks.processResources)
    doLast {
        val properties = Properties().also {
            it["version"] = project.version.toString()
        }
        File(projectDir, "src/main/resources/edu.illinois.cs.cs125.jeed.core.version")
            .printWriter().use { printWriter ->
                printWriter.print(
                    StringWriter().also { properties.store(it, null) }.buffer.toString()
                        .lines().drop(1).joinToString(separator = "\n").trim()
                )
            }
    }
}
tasks.register<Copy>("copyJavaGrammar") {
    from(project.file("src/main/antlr/edu/illinois/cs/cs125/jeed/antlr/java"))
    include("*.g4")
    into(project.file("src/main/antlr/edu/illinois/cs/cs125/jeed/antlr/lib"))
    doLast {
        // ANTLR doesn't notice if library grammars are updated, even if the main grammar's modified date is changed
        // When the Java grammar was modified (i.e. this task has to run), delete the dependent Snippet outputs
        project.file("src/main/java/edu/illinois/cs/cs125/jeed/core/antlr").listFiles()?.filter {
            it.name.startsWith("Snippet")
        }?.forEach {
            it.delete()
        }
    }
}
tasks {
    val sourcesJar by creating(Jar::class) {
        archiveClassifier.set("sources")
        from(sourceSets["main"].allSource)
    }
    artifacts {
        add("archives", sourcesJar)
    }
}
tasks.detekt {
    dependsOn(tasks.generateGrammarSource)
}

tasks.lintKotlinMain {
    dependsOn(tasks.generateGrammarSource)
}
tasks.formatKotlinMain {
    dependsOn(tasks.generateGrammarSource)
}
kotlin {
    kotlinDaemonJvmArgs = listOf("-Dfile.encoding=UTF-8")
}
java {
    withJavadocJar()
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
tasks.withType<FormatTask> {
    this.source = this.source.minus(fileTree("build")).asFileTree
}
tasks.withType<LintTask> {
    this.source = this.source.minus(fileTree("build")).asFileTree
}
publishing {
    publications {
        create<MavenPublication>("core") {
            artifactId = "core"
            from(components["java"])
            pom {
                name = "jeed"
                description = "Sandboxing and code analysis toolkit for CS 124."
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
        gradle.taskGraph.allTasks.any { it is PublishToMavenRepository }
    }
    sign(publishing.publications["core"])
    setRequired {
        gradle.taskGraph.hasTask("publishCorePublicationToSonatype")
    }
}
tasks.withType<Javadoc> {
    exclude("edu/illinois/cs/cs125/jeed/core/antlr/**")
}
