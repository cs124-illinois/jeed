@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://maven.google.com/")
        maven("https://maven.codeawakening.com")
    }
}

rootProject.name = "jeed"
include("core", "server", "containerrunner", "leaktest")
