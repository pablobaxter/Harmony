plugins {
    id("org.gradle.kotlin.kotlin-dsl") version "3.1.0"
    kotlin("jvm") version "1.7.10"
}

repositories {
    mavenCentral()
    google()
    maven("https://plugins.gradle.org/m2/")
}

dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation("com.android.tools.build:gradle:7.2.2")
    implementation("com.android.tools.build:gradle-api:7.2.2")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.10")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:1.7.10")
    implementation("androidx.navigation:navigation-safe-args-gradle-plugin:2.5.1")
    implementation("com.google.dagger:hilt-android-gradle-plugin:2.43.2")
    implementation("io.github.gradle-nexus:publish-plugin:1.1.0")
}

repositories {
    google()
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("frybitsAppPlugin") {
            id = "frybits-application"
            implementationClass = "com.frybits.harmony.gradle.ApplicationPlugin"
        }

        create("frybitsLibraryPlugin") {
            id = "frybits-library"
            implementationClass = "com.frybits.harmony.gradle.LibraryPlugin"
        }
    }
}
