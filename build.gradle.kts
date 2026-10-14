// AGP 9 heeft ingebouwde Kotlin-ondersteuning: de plugin org.jetbrains.kotlin.android wordt niet meer
// toegepast. De Kotlin Gradle-plugin moet wel op het classpath staan (voor de compiler en de Compose-plugin).
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
