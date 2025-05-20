// buildscript block is needed to fetch plugin from JitPack repository
// comment for using from localMaven
buildscript {
    repositories {
        google()
        maven("https://jitpack.io")
        mavenCentral()
    }

    dependencies{
        classpath("com.github.tafilovic:central-portal-publisher:2.0.4")
    }
}

plugins{
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
}

