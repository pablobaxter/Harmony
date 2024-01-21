package com.frybits.harmony.gradle

import com.android.build.api.dsl.TestExtension
import com.android.build.gradle.TestPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper

class FrybitsTestPlugin : Plugin<Project> {

    override fun apply(target: Project) = target.run {
        applyTestPlugins()
        configure<TestExtension> {
            configureAndroidTest()
        }
    }
}

private fun Project.applyTestPlugins() {
    apply<TestPlugin>()
    apply<KotlinAndroidPluginWrapper>()
}

private fun TestExtension.configureAndroidTest() {
    configureCommonAndroid()
}
