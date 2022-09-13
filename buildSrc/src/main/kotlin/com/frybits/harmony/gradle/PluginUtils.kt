package com.frybits.harmony.gradle

import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.DefaultConfig
import com.android.build.api.dsl.ProductFlavor
import org.gradle.api.JavaVersion
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions

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
