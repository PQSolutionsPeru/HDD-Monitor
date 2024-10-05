pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            version("compose-compiler", "1.5.8")
            version("compose-bom", "2023.06.01")

            plugin("android-application", "com.android.application").version("8.2.2")
            plugin("kotlin-android", "org.jetbrains.kotlin.android").version("1.9.22")
            plugin("hilt-android", "com.google.dagger.hilt.android").version("2.50")
            plugin("google-services", "com.google.gms.google-services").version("4.4.2")

            library("androidx-compose-bom", "androidx.compose", "compose-bom").versionRef("compose-bom")
            library("androidx-compose-ui", "androidx.compose.ui", "ui").withoutVersion()
            library("androidx-compose-ui-graphics", "androidx.compose.ui", "ui-graphics").withoutVersion()
            library("androidx-compose-ui-tooling-preview", "androidx.compose.ui", "ui-tooling-preview").withoutVersion()
            library("androidx-compose-material3", "androidx.compose.material3", "material3").withoutVersion()
            library("androidx-compose-runtime", "androidx.compose.runtime", "runtime").withoutVersion()

            library("androidx-core-ktx", "androidx.core", "core-ktx").version("1.12.0")
            library("androidx-lifecycle-runtime-ktx", "androidx.lifecycle", "lifecycle-runtime-ktx").version("2.6.2")
            library("androidx-activity-compose", "androidx.activity", "activity-compose").version("1.8.2")

            library("hilt-android", "com.google.dagger", "hilt-android").version("2.50")
            library("hilt-android-compiler", "com.google.dagger", "hilt-android-compiler").version("2.50")
            library("androidx-hilt-navigation-compose", "androidx.hilt", "hilt-navigation-compose").version("1.1.0")

            library("androidx-test-ext-junit", "androidx.test.ext", "junit").version("1.1.5")
            library("androidx-test-espresso-core", "androidx.test.espresso", "espresso-core").version("3.5.1")
            library("androidx-compose-ui-test-junit4", "androidx.compose.ui", "ui-test-junit4").withoutVersion()
            library("androidx-compose-ui-tooling", "androidx.compose.ui", "ui-tooling").withoutVersion()
            library("androidx-compose-ui-test-manifest", "androidx.compose.ui", "ui-test-manifest").withoutVersion()
        }
    }
}

rootProject.name = "HDD1_2"
include(":app")