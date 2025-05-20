plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)

    // for localMaven
    // id("central.portal.publisher") version "2.0.4"

    // for JitPack
    id("central.portal.publisher")
}

android {
    namespace = "io.github.tafilovic.publishtest"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        flavorDimensions += listOf("main")
    }

    productFlavors {
        create("prod") {
            dimension = flavorDimensions[0]
            minSdk = 24
        }
        create("tst") {
            dimension = flavorDimensions[0]
            minSdk = 32
        }
    }
}

dependencies {
    //implementation(libs.core.common)
    implementation(libs.annotation)
    implementation(libs.appcompat.v7)
    testImplementation(libs.junit)
    androidTestImplementation(libs.runner)
    androidTestImplementation(libs.espresso.core)
}

centralPortalPublisher {
    componentName = "prodRelease"
    groupId = project.property("GROUP").toString()
    artifactId = project.property("POM_ARTIFACT_ID").toString()
    version = project.property("VERSION_NAME").toString()
    flavorName="prod-release"
}

//afterEvaluate {
//    publishing {
//        publications {
//            register<MavenPublication>("Test") {
//                from(components["release"])
//                group = "io.github.tafilovic"
//                artifactId = "test-publish"
//                version = "0.0.1"
//            }
//        }
//    }
//}