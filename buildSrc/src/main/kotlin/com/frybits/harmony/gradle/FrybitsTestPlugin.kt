package com.frybits.harmony.gradle

import com.android.build.api.dsl.TestExtension
import com.android.build.gradle.TestPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper
import java.io.File
import java.util.Properties

class FrybitsTestPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.applyTestPlugins()
        target.configure<TestExtension> {
            configureAndroidTest(Properties().apply {
                val propertiesFile = target.rootProject.file("local.properties")
                if (propertiesFile.exists()) {
                    load(propertiesFile.reader())
                }
            })
        }
    }
}

private fun Project.applyTestPlugins() {
    apply<TestPlugin>()
    apply<KotlinAndroidPluginWrapper>()
}

@Suppress("UnstableApiUsage")
private fun TestExtension.configureAndroidTest(properties: Properties) {
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
            storePassword = properties["harmony.keystore.password"] as? String?
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
}
