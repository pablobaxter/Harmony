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
import org.jetbrains.kotlin.gradle.plugin.KaptExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper

class ApplicationPlugin: Plugin<Project> {

    override fun apply(target: Project) {
        target.applyAppPlugins()

        target.configure<ApplicationExtension> {

        }
    }
}
