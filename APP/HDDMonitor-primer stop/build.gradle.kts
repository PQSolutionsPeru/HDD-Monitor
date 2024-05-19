// build.gradle.kts a nivel de proyecto

// Eliminé las configuraciones de repositorios para evitar conflictos
buildscript {
    dependencies {
        classpath("com.android.tools.build:gradle:8.4.0")
        classpath("com.google.gms:google-services:4.3.10")
        classpath(kotlin("gradle-plugin", version = "1.8.10"))
    }
}

plugins {
    id("com.android.application") version "8.4.0" apply false
    id("org.jetbrains.kotlin.android") version "1.8.10" apply false
    id("com.google.gms.google-services") version "4.3.10" apply false
}
