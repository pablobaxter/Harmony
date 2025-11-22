package com.frybits.harmony.gradle

import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.vanniktech.maven.publish.MavenPublishPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper
import org.jetbrains.kotlinx.serialization.gradle.SerializationGradleSubplugin

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

class FrybitsLibraryPlugin : Plugin<Project> {

    override fun apply(target: Project) = target.run {
        applyLibraryPlugins()

        configure<LibraryExtension> {
            configureAndroidLibrary()
        }

        extensions.configure<KotlinAndroidExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }

        configureDokka()
    }
}

private fun Project.applyLibraryPlugins() {
    apply<LibraryPlugin>()
    apply<KotlinAndroidPluginWrapper>()
    apply<SerializationGradleSubplugin>()
    apply<DokkaPlugin>()
    apply<MavenPublishPlugin>()
}

private fun LibraryExtension.configureAndroidLibrary() {
    configureCommonAndroid()

    buildTypes {
        maybeCreate("release").apply {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

private fun Project.configureDokka() {
    extensions.configure<DokkaExtension> {
        dokkaPublications.named("html") {
            suppressInheritedMembers.set(true)
            failOnWarning.set(true)
        }

        dokkaSourceSets.named("main") {
            reportUndocumented.set(true)
            sourceRoots.setFrom(this@configureDokka.file("src/main/java"))
        }
    }
}
