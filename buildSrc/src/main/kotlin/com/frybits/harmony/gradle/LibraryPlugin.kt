package com.frybits.harmony.gradle

import com.android.build.api.dsl.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import io.github.gradlenexus.publishplugin.NexusPublishExtension
import io.github.gradlenexus.publishplugin.NexusPublishPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.hasPlugin
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.repositories
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin
import org.jetbrains.dokka.Platform
import org.jetbrains.dokka.gradle.DokkaPlugin
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper
import java.io.File
import java.net.URI
import java.util.Properties

class LibraryPlugin : Plugin<Project> {

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
//    if (!rootProject.plugins.hasPlugin(NexusPublishPlugin::class)) {
//        rootProject.apply<NexusPublishPlugin>()
//    }

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
            withJavadocJar()
            withSourcesJar()
        }
    }
}

private fun Project.configureDokka() {
    tasks.getByName<DokkaTask>("dokkaHtml") {
        moduleName.set("Harmony") // TODO abstract out
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
}

private fun Project.configurePublishing() {
    extra["signing.keyId"] = ""
    extra["signing.password"] = ""
    extra["signing.secretKeyRingFile"] = ""
    extra["ossrhUsername"] = ""
    extra["ossrhPassword"] = ""

    val secretsPropsFile = rootProject.file("local.properties")
    if (secretsPropsFile.exists()) {
        println("Found secret props file, loading props")
        Properties().apply {
            load(secretsPropsFile.bufferedReader())
            forEach { (name, value) ->
                extra[name.toString()] = value
            }
        }
    } else {
        println("No props file, loading env vars")
        extra["signing.keyId"] = System.getenv("SIGNING_KEY_ID")
        extra["signing.password"] = System.getenv("SIGNING_PASSWORD")
        extra["signing.secretKeyRingFile"] = System.getenv("SIGNING_SECRET_KEY_RING_FILE")
        extra["ossrhUsername"] = System.getenv("MAVEN_USERNAME")
        extra["ossrhPassword"] = System.getenv("MAVEN_PASSWORD")
    }

    afterEvaluate {
        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("releaseHarmony") {
                    from(components["release"])

                    group = "com.frybits.harmony"
                    artifactId = "harmony"
                    version = rootProject.extra["harmony_version_name"].toString()

                    pom {
                        name.set("Harmony")
                        description.set("A process-safe SharedPreferences implementation")
                        url.set("https://github.com/pablobaxter/Harmony")
                        licenses {
                            license {
                                name.set("The Apache License, Version 2.0")
                                url.set("https://opensource.org/licenses/Apache-2.0")
                            }
                        }
                        developers {
                            developer {
                                id.set("soaboz")
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
            }

            repositories {
                maven {
                    name = "sonatype"
                    url = URI("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
                    credentials {
                        username = extra["ossrhUsername"].toString()
                        password = extra["ossrhPassword"].toString()
                    }
                }
            }
        }
    }

    configure<SigningExtension> {
        sign(extensions.getByType<PublishingExtension>().publications)
    }

//    configure<NexusPublishExtension> {
//        packageGroup.set("com.frybits.harmony")
//        repositories {
//            sonatype {
//                stagingProfileId.set("38e43065571d")
//                username.set(extra["ossrhUsername"].toString())
//                password.set(extra["ossrhPassword"].toString())
//            }
//        }
//    }
}
