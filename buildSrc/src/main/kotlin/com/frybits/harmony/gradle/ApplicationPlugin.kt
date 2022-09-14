package com.frybits.harmony.gradle

import androidx.navigation.safeargs.gradle.SafeArgsKotlinPlugin
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.AppPlugin
import dagger.hilt.android.plugin.HiltGradlePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.internal.Kapt3GradleSubplugin
import org.jetbrains.kotlin.gradle.internal.ParcelizeSubplugin
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper
import java.io.File
import java.util.Properties

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

class ApplicationPlugin: Plugin<Project> {

    override fun apply(target: Project) {
        target.applyAppPlugins()

        target.configure<ApplicationExtension> {
            configureAndroidApplication(Properties().apply {
                val propertiesFile = target.rootProject.file("local.properties")
                if (propertiesFile.exists()) {
                    load(propertiesFile.reader())
                }
            })
        }
    }
}

private fun Project.applyAppPlugins() {
    apply<AppPlugin>()
    apply<KotlinAndroidPluginWrapper>()
    apply<ParcelizeSubplugin>()
    apply<SafeArgsKotlinPlugin>()
    apply<Kapt3GradleSubplugin>()
    apply<HiltGradlePlugin>()
}

private fun ApplicationExtension.configureAndroidApplication(properties: Properties) {
    configureCommonAndroid()

    signingConfigs {
        maybeCreate("debug").apply {
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            val keystoreFile = File("./keystore.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
            }
            storePassword = "android"
        }

        maybeCreate("release").apply {
            keyAlias = properties["harmony.keystore.alias"] as? String?
            keyPassword = properties["harmony.keystore.alias.password"] as? String?
            val fileProperty = properties["harmony.keystore.file"] as? String?
            if (!fileProperty.isNullOrBlank()) {
                storeFile = File(fileProperty)
            }
            storePassword = properties["harmony.keystore.property"] as? String?
        }
    }

    buildTypes {
        maybeCreate("release").apply {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(name)
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        maybeCreate("debug").apply {
            signingConfig = signingConfigs.getByName(name)
        }
    }

    buildFeatures.viewBinding = true
}
