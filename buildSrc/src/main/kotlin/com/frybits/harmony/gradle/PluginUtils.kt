package com.frybits.harmony.gradle

import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.DefaultConfig
import com.android.build.api.dsl.ProductFlavor
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.extra
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions

private const val CRYPTO = "crypto"
private const val HARMONY = "harmony"

internal fun <BuildFeaturesT : BuildFeatures, BuildTypeT : BuildType, DefaultConfigT : DefaultConfig, ProductFlavorT : ProductFlavor>
        CommonExtension<BuildFeaturesT, BuildTypeT, DefaultConfigT, ProductFlavorT>.configureCommonAndroid() {
    compileSdk = 33

    defaultConfig {
        minSdk = 17

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    (this as? ExtensionAware)?.configure<KotlinJvmOptions> {
        jvmTarget = "11"
    }
}

fun Project.calculateArtifactId(): String {
    return when (name) {
        CRYPTO -> "harmony-crypto"
        HARMONY -> "harmony"
        else -> throw IllegalArgumentException("Unknown project $name")
    }
}

fun Project.calculateArtifactVersion(): String {
    return when (name) {
        CRYPTO -> rootProject.extra["crypto_version_name"].toString()
        HARMONY -> rootProject.extra["harmony_version_name"].toString()
        else -> throw IllegalArgumentException("Unknown project $name")
    }
}

fun Project.calculatePomName(): String {
    return when (name) {
        CRYPTO -> "Harmony Crypto"
        HARMONY -> "Harmony"
        else -> throw IllegalArgumentException("Unknown project $name")
    }
}

fun Project.calculateDescription(): String {
    return when (name) {
        CRYPTO -> "A process-safe Encrypted SharedPreferences implementation"
        HARMONY -> "A process-safe SharedPreferences implementation"
        else -> throw IllegalArgumentException("Unknown project $name")
    }
}
