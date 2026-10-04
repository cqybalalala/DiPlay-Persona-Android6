import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Optional local-only input. CI and ordinary source builds contain no accessory identity.
val localAuthenticationAssets = providers.environmentVariable("DIPLAY_AUTH_ASSETS_DIR")
    .orNull?.let { file(it).canonicalFile }

// Optional build stamp. CI sets this to the workflow run number so every APK and every
// exported diagnostic report can be traced back to one exact build.
val buildNumber = providers.environmentVariable("DIPLAY_BUILD_NUMBER").orNull

val personaSideBySide = providers.environmentVariable("DIPLAY_PERSONA_SIDE_BY_SIDE").orNull == "1"

android {
    namespace = "com.shilapi.xcertplay"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = if (personaSideBySide) "com.shihab.diplay.persona" else "com.shihab.diplay"
        manifestPlaceholders["diplayLabel"] = if (personaSideBySide) "DiPlay USB" else "@string/app_name"
        minSdk = 23
        targetSdk = 37
        versionCode = 20
        versionName = buildNumber?.let { "2.0（$it）" } ?: "2.0"

    }


    localAuthenticationAssets?.let { dir ->
        sourceSets.getByName("main").assets.directories.add(dir.absolutePath)
    }

    signingConfigs {
        create("release") {
            storeFile = file(
                providers.environmentVariable("ANDROID_KEYSTORE_PATH")
                    .getOrElse("missing-release-keystore.jks"),
            )
            storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").getOrElse("")
            keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").getOrElse("")
            keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").getOrElse("")
        }
    }

    buildTypes {
        debug {
            // The BYD HUD bridges gate on this suffix, so it stays. The launcher label and the
            // version name no longer carry any "test" marker.
            applicationIdSuffix = ".hudtest"
        }
        release {
            optimization {
                enable = false
            }
            // The BYD HUD bridges gate on the runtime package name (5 hard checks), so the
            // release build must keep the same ".hudtest" suffix as debug or those gates fail.
            applicationIdSuffix = ".hudtest"
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // Supplies java.time and java.util.Base64 on Android 7/7.1 (API 24/25).
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(platform(libs.androidx.compose.bom))
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.app.projected)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// No implicit import. Only the two explicitly selected local runtime assets are allowed.
//
// Scanning strategy (Gradle 9.x / configuration-cache safe):
//   • localAuthenticationAssets is captured as a String? (serializable) rather than
//     as a File? Gradle-script reference, which the configuration cache cannot serialize.
//   • Only the module's own static src/main/assets is scanned for rogue credential
//     containers.  The external auth-assets dir is verified by existence check only so
//     that we never hand Gradle a fileTree rooted at D:\ (which covers build outputs).
val authAssetsDirPath: String? = localAuthenticationAssets?.canonicalPath
val ownAssetsDirPath: String = projectDir.resolve("src/main/assets").canonicalPath
val rejectBundledCredentials by tasks.registering {
    group = "verification"
    description = "Reject unexpected credential files in APK assets."
    // Always run: no tracked inputs, so the task is never UP-TO-DATE.
    outputs.upToDateWhen { false }
    mustRunAfter(
        ":common:mergeDebugAssets", ":common:mergeReleaseAssets",
        ":common:processDebugNavigationResources", ":common:processReleaseNavigationResources",
        ":shared:mergeDebugAssets", ":shared:mergeReleaseAssets",
        ":shared:processDebugNavigationResources", ":shared:processReleaseNavigationResources",
    )
    doLast {
        // 1. Verify the two explicitly allowed auth files are present and readable.
        val authDir: File? = authAssetsDirPath?.let { p: String -> File(p) }
        if (authDir != null) {
            val paths = listOf("offline-mfi/identity.pk8", "offline-mfi/certificate.p7b")
            val missing: List<File> = paths
                .map { rel: String -> authDir.resolve(rel) }
                .filter { f: File -> !f.isFile }
            check(missing.isEmpty()) {
                "Explicit local authentication assets are incomplete: missing ${
                    missing.map { f: File -> f.name }}"
            }
        }
        // 2. Scan only the module's own static source assets dir for unexpected
        //    credential containers.  Use pure stdlib walkTopDown() to avoid
        //    capturing a Gradle script/project reference (config-cache incompatible).
        val ownDir: File = File(ownAssetsDirPath)
        if (ownDir.exists()) {
            val credExts = setOf("pk8", "p7b", "key", "pem", "p12", "pfx", "jks", "keystore")
            val allowed: Set<File> = if (authDir != null) {
                listOf("offline-mfi/identity.pk8", "offline-mfi/certificate.p7b")
                    .map { rel: String -> authDir.resolve(rel).canonicalFile }
                    .toSet()
            } else emptySet()
            val sep = File.separatorChar
            val unexpected: List<File> = ownDir.walkTopDown()
                .filter { f: File ->
                    f.isFile && (
                        f.extension.lowercase() in credExts ||
                        f.canonicalPath.contains("${sep}offline-mfi${sep}") ||
                        f.canonicalPath.endsWith("${sep}offline-mfi")
                    ) && f.canonicalFile !in allowed
                }
                .toList()
            check(unexpected.isEmpty()) {
                "Unexpected credential files in APK assets: $unexpected"
            }
        }
    }
}
tasks.named("preBuild") { dependsOn(rejectBundledCredentials) }
