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
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions

/*
 *  Copyright 2022 Pablo Baxter
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * Created by Pablo Baxter (Github: pablobaxter)
 * https://github.com/pablobaxter/Harmony
 */

private const val CRYPTO = "crypto"
private const val HARMONY = "harmony"

@Suppress("UnstableApiUsage")
internal fun <BuildFeaturesT : BuildFeatures, BuildTypeT : BuildType, DefaultConfigT : DefaultConfig, ProductFlavorT : ProductFlavor>
        CommonExtension<BuildFeaturesT, BuildTypeT, DefaultConfigT, ProductFlavorT>.configureCommonAndroid() {
    compileSdk = 33

    defaultConfig {
        minSdk = 17

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
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

internal fun Project.harmonyArtifactId(): String {
    return when (name) {
        CRYPTO -> "harmony-crypto"
        HARMONY -> "harmony"
        else -> throw IllegalArgumentException("Unknown project $name")
    }
}

internal fun Project.harmonyPomName(): String {
    return when (name) {
        CRYPTO -> "Harmony Crypto"
        HARMONY -> "Harmony"
        else -> throw IllegalArgumentException("Unknown project $name")
    }
}

internal fun Project.harmonyDescription(): String {
    return when (name) {
        CRYPTO -> "A process-safe Encrypted SharedPreferences implementation"
        HARMONY -> "A process-safe SharedPreferences implementation"
        else -> throw IllegalArgumentException("Unknown project $name")
    }
}
