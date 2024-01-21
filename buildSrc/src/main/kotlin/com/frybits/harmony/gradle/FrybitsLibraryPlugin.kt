package com.frybits.harmony.gradle

import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.tasks.JavaDocGenerationTask
import com.vanniktech.maven.publish.MavenPublishPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.DocsType
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.jetbrains.dokka.Platform
import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper
import org.jetbrains.kotlinx.serialization.gradle.SerializationGradleSubplugin
import java.io.File
import java.util.Locale

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
    val dokka = tasks.getByName<DokkaTask>("dokkaHtml") {
        notCompatibleWithConfigurationCache("https://github.com/Kotlin/dokka/issues/2231")
        moduleName.set(this@configureDokka.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() })
        dokkaSourceSets.maybeCreate("main").apply {
            noAndroidSdkLink.set(false)
            outputDirectory.set(layout.buildDirectory.get().file("dokka").asFile)
            reportUndocumented.set(true)
            platform.set(Platform.jvm)
            sourceRoots.setFrom(File("src/main"))
            jdkVersion.set(11)

            perPackageOption {
                matchingRegex.set("kotlin($|\\.).*")
                skipDeprecated.set(false)
                reportUndocumented.set(true)
                includeNonPublic.set(false)
            }
        }
    }

    afterEvaluate {
        tasks.named<Jar>("javaDocReleaseJar") {
            dependsOn(dokka)
            from(layout.buildDirectory.get().file("dokka"))
            archiveClassifier.set(DocsType.JAVADOC)
        }

        // This avoids the NoSuchMethodError described in https://github.com/Kotlin/dokka/issues/2472
        tasks.withType<JavaDocGenerationTask>().forEach { it.enabled = false }
    }
}
