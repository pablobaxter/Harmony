package com.frybits.harmony.gradle

import androidx.navigation.safeargs.gradle.SafeArgsKotlinPlugin
import com.android.build.gradle.AppPlugin
import dagger.hilt.android.plugin.HiltGradlePlugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.jetbrains.kotlin.gradle.internal.Kapt3GradleSubplugin
import org.jetbrains.kotlin.gradle.internal.ParcelizeSubplugin
import org.jetbrains.kotlin.gradle.plugin.KotlinAndroidPluginWrapper

fun Project.applyAppPlugins() {
    apply<AppPlugin>()
    apply<KotlinAndroidPluginWrapper>()
    apply<ParcelizeSubplugin>()
    apply<SafeArgsKotlinPlugin>()
    apply<Kapt3GradleSubplugin>()
    apply<HiltGradlePlugin>()
}
