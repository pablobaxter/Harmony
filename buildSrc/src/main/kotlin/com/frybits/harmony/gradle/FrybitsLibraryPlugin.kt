package com.frybits.harmony.gradle

import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.tasks.JavaDocGenerationTask
import dagger.hilt.android.plugin.util.capitalize
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.DocsType
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin
import org.jetbrains.dokka.Platform
import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper
import java.io.File

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

    override fun apply(target: Project) {
        target.applyLibraryPlugins()

        target.configure<LibraryExtension> {
            configureAndroidLibrary()
        }

        target.configureDokka()
        target.configurePublishing()
    }
}

private fun Project.applyLibraryPlugins() {
    apply<LibraryPlugin>()
    apply<KotlinAndroidPluginWrapper>()
    apply<DokkaPlugin>()
    apply<MavenPublishPlugin>()
    apply<SigningPlugin>()
}

private fun LibraryExtension.configureAndroidLibrary() {
    configureCommonAndroid()

    buildTypes {
        maybeCreate("release").apply {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

private fun Project.configureDokka() {
    val dokka = tasks.getByName<DokkaTask>("dokkaHtml") {
        moduleName.set(this@configureDokka.name.capitalize())
        dokkaSourceSets.maybeCreate("main").apply {
            noAndroidSdkLink.set(false)
            outputDirectory.set(File("${buildDir}/dokka"))
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
            from("$buildDir/dokka")
            archiveClassifier.set(DocsType.JAVADOC)
        }
        tasks.withType<JavaDocGenerationTask>().forEach { it.enabled = false }
    }
}

private fun Project.configurePublishing() {
    afterEvaluate {
        configure<PublishingExtension> {
            publications {
                whenObjectAdded {
                    if (this is MavenPublication) {
                        group = "com.frybits.harmony"
                        artifactId = harmonyArtifactId()
                        configurePom(harmonyPomName(), harmonyDescription())
                    }
                }
                create<MavenPublication>("release") {
                    from(components["release"])
                }
            }
        }
    }

    configure<SigningExtension> {
        sign(extensions.getByType<PublishingExtension>().publications)
    }
}

private fun MavenPublication.configurePom(
    projectName: String,
    projectDescription: String
) {
    pom {
        name.set(projectName)
        description.set(projectDescription)
        url.set("https://github.com/pablobaxter/Harmony")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://opensource.org/licenses/Apache-2.0")
            }
        }
        developers {
            developer {
                id.set("pablobaxter")
                name.set("Pablo Baxter")
                email.set("pablo@frybits.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/pablobaxter/Harmony.git")
            developerConnection.set("git:ssh://github.com/pablobaxter/Harmony.git")
            url.set("https://github.com/pablobaxter/Harmony")
        }
    }
}
