pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        val kotlinVersion = extra["kotlin.version"] as String
        kotlin("plugin.serialization").version(kotlinVersion)
        id("com.android.library") version "8.2.0-rc03"
        id("org.jetbrains.kotlin.android") version "1.9.0"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "iGamingKit"
include(":iGamingKit")
