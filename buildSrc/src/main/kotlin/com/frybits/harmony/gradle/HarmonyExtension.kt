package com.frybits.harmony.gradle

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.domainObjectContainer
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */
open class HarmonyExtension @Inject constructor(objects: ObjectFactory) {

    internal val compileSdkVersion: Property<Int> = objects.property()

    fun compileSdkVersion(version: Int) {
        compileSdkVersion.set(version)
        compileSdkVersion.disallowChanges()
    }

    val defaultConfig: NamedDomainObjectContainer<HarmonyDefaultConfigExtension> = objects.domainObjectContainer(HarmonyDefaultConfigExtension::class)

    fun defaultConfig(action: NamedDomainObjectContainer<HarmonyDefaultConfigExtension>.() -> Unit) {
        defaultConfig.action()
    }
}

open class HarmonyDefaultConfigExtension @Inject constructor(
    private val _name: String,
    objects: ObjectFactory
): Named {

    override fun getName(): String = _name

//    internal val minSdkVersion: Property<Int> = objects.property()
//
//    internal val targetSdkVersion: Property<Int> = objects.property()
//
//    internal val testInstrumentationRunner: Property<String> = objects.property()
}
