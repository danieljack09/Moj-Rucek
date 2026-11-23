plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// The Firebase config is kept out of this public repository (see README, "Firebase setup").
// Debug builds without it still build and run, with accounts unavailable (development fallback).
// Release builds without a valid one FAIL: see verifyFirebaseConfigRelease below.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.mojrucek.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mojrucek.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schema export, useful for migration debugging.
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        // Safe by default: only the debug build type opts in. Any new build type or flavor inherits
        // false, so it can never open the app without a configured account system.
        buildConfigField("boolean", "ALLOW_UNCONFIGURED_AUTH", "false")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            // Development fallback: a debug build without google-services.json runs without accounts.
            buildConfigField("boolean", "ALLOW_UNCONFIGURED_AUTH", "true")
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
        compose = true
        buildConfig = true
    }

    // Room's exported schema files (app/schemas) let the migration tests build a database of an OLD version.
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// A release build must never ship without working accounts. Runs before every release-variant task
// (assembleRelease, bundleRelease, lintRelease, ...) and fails the build unless google-services.json
// is present and complete for the release application id. Debug builds and IDE sync are unaffected.
val verifyFirebaseConfigRelease = tasks.register("verifyFirebaseConfigRelease") {
    group = "verification"
    description = "Fails if google-services.json is missing or incomplete (release builds require accounts)."
    val configFile = layout.projectDirectory.file("google-services.json").asFile
    val releaseApplicationId = "com.mojrucek.app"
    val pluginApplied = pluginManager.hasPlugin("com.google.gms.google-services")
    inputs.files(configFile).optional()
    doLast {
        val problems = mutableListOf<String>()
        if (!configFile.isFile) {
            problems += "app/google-services.json is missing"
        } else {
            @Suppress("UNCHECKED_CAST")
            val root = try {
                groovy.json.JsonSlurper().parse(configFile) as? Map<String, Any?>
            } catch (e: Exception) {
                null
            }
            if (root == null) {
                problems += "app/google-services.json is not valid JSON"
            } else {
                val projectInfo = root["project_info"] as? Map<*, *>
                if ((projectInfo?.get("project_id") as? String).isNullOrBlank()) problems += "project_info.project_id is missing"
                val clients = (root["client"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
                val client = clients.firstOrNull {
                    (((it["client_info"] as? Map<*, *>)?.get("android_client_info") as? Map<*, *>)?.get("package_name")) == releaseApplicationId
                }
                if (client == null) {
                    problems += "no client for package $releaseApplicationId (register the release app in Firebase and download the file again)"
                } else {
                    if ((((client["client_info"] as Map<*, *>)["mobilesdk_app_id"]) as? String).isNullOrBlank()) problems += "client for $releaseApplicationId has no mobilesdk_app_id"
                    val keys = (client["api_key"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
                    if (keys.none { !(it["current_key"] as? String).isNullOrBlank() }) problems += "client for $releaseApplicationId has no api_key"
                    val oauthTypes = (client["oauth_client"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map { it["client_type"] }
                    if (oauthTypes.none { (it as? Number)?.toInt() == 3 }) problems += "client for $releaseApplicationId has no Web OAuth client (type 3): Google sign-in would not work"
                    if (oauthTypes.none { (it as? Number)?.toInt() == 1 }) logger.warn("WARNING: no Android OAuth client (SHA-1) registered for $releaseApplicationId; Google sign-in needs the release/Play signing SHA-1 in Firebase.")
                }
            }
        }
        if (!pluginApplied) problems += "the google-services plugin was not applied"
        if (problems.isNotEmpty()) {
            throw GradleException(
                "Release build refused: Firebase configuration is not valid.\n" +
                    problems.joinToString("\n") { "  - $it" } +
                    "\nSee README, \"Firebase setup\". Debug builds are not affected."
            )
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyFirebaseConfigRelease) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Accounts: Firebase Auth (identity only) + Google sign-in through Credential Manager.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
}
