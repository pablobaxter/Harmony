plugins {
    `kotlin-dsl`
    kotlin("jvm") version "1.9.10"
}

dependencies {
    implementation(gradleApi())
    implementation(gradleKotlinDsl())
    implementation(libs.bundles.android.build)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.dokka.plugin)
    implementation(libs.kotlin.ksp)
    implementation(libs.androidx.navigation.safeargs.plugin)
    implementation(libs.hilt.plugin)
    implementation(libs.vanniktech)
}

gradlePlugin {
    plugins {
        create("frybitsAppPlugin") {
            id = "frybits-application"
            implementationClass = "com.frybits.harmony.gradle.FrybitsApplicationPlugin"
        }

        create("frybitsLibraryPlugin") {
            id = "frybits-library"
            implementationClass = "com.frybits.harmony.gradle.FrybitsLibraryPlugin"
        }

        create("frybitsTestPlugin") {
            id = "frybits-test"
            implementationClass = "com.frybits.harmony.gradle.FrybitsTestPlugin"
        }
    }
}
