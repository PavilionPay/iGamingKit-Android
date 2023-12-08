plugins {
    id("com.android.application") version "8.2.0-rc03" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
    id("org.jetbrains.dokka") version "1.9.10"
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")
}
