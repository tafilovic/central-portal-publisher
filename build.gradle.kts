// buildscript block is needed to fetch plugin from JitPack repository
// Comment out when using from local Maven (e.g. id("central.portal.publisher") version "2.0.8")
// buildscript {
//     repositories {
//         google()
//         maven("https://jitpack.io")
//         mavenCentral()
//     }
//
//     dependencies{
//         classpath("com.github.tafilovic:central-portal-publisher:2.0.4")
//     }
// }

plugins{
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
}

tasks.register("publishPluginPortal") {
    group = "publishing"
    description = "Publish plugin to Gradle Plugin Portal (skips :publishTest)"
    dependsOn(":plugin:publishPlugins")
    doFirst {
        project.extensions.extraProperties["skipPublishTest"] = true
    }
}

