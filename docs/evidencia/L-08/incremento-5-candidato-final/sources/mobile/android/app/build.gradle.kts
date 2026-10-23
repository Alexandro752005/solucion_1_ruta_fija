plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// Opt-in package isolation for the personal-device L08 validation. Ordinary
// builds retain their application ID; this flag never enables release signing.
val rutaFijaL08Isolated = providers.gradleProperty("rutaFijaL08Isolated").orNull == "true"
// Explicit local candidate signing. Secrets come from the builder process and
// are never placed in project properties or substituted by the debug key.
val rutaFijaLocalRelease = providers.gradleProperty("rutaFijaLocalRelease").orNull == "true"
val candidateStorePath = providers.environmentVariable("RF_ANDROID_CANDIDATE_STORE").orNull
val candidateStorePassword = providers.environmentVariable("RF_ANDROID_CANDIDATE_STORE_PASSWORD").orNull
val candidateKeyPassword = providers.environmentVariable("RF_ANDROID_CANDIDATE_KEY_PASSWORD").orNull
val candidateKeyAlias = "rutafija-candidato-local"

tasks.register("rutaFijaPublicBuildIdentity") {
    doLast {
        println("RUTA_FIJA_ISOLATED_BUILD=" + rutaFijaL08Isolated)
        println("RUTA_FIJA_APPLICATION_ID=" + android.defaultConfig.applicationId)
        println("RUTA_FIJA_MIN_SDK=" + android.defaultConfig.minSdk)
    }
}

android {
    namespace = "pe.rutafija.conductor"
    compileSdk = flutter.compileSdkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = if (rutaFijaL08Isolated) "pe.rutafija.conductor.l08" else "pe.rutafija.conductor"
        manifestPlaceholders["rutaFijaAppLabel"] = if (rutaFijaL08Isolated) "Ruta Fija Pruebas" else "Ruta Fija Conductor"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        // Android 10 is the approved provisional product boundary. Installation
        // eligibility is separate from physical-device/GPS validation in L08.
        minSdk = 29
        targetSdk = flutter.targetSdkVersion
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter. (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P force-version-code-ignoring-abi=true`
        // flag during build.
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        if (rutaFijaLocalRelease && !candidateStorePath.isNullOrBlank() &&
            !candidateStorePassword.isNullOrBlank() && !candidateKeyPassword.isNullOrBlank()) {
            create("localCandidate") {
                storeFile = file(candidateStorePath)
                storePassword = candidateStorePassword
                keyAlias = candidateKeyAlias
                keyPassword = candidateKeyPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("localCandidate")
        }
    }
}

// A signed local candidate does not enable deployment or represent custody by
// the real titleholder. Every ordinary release stays blocked without opt-in.
val rutaFijaAppProject = project
gradle.taskGraph.whenReady {
    val deliveryRequested = allTasks.any {
        it.project == rutaFijaAppProject && it.name.contains("Release", ignoreCase = true) &&
            (it.name.startsWith("assemble") || it.name.startsWith("bundle") ||
                it.name.startsWith("package") || it.name.startsWith("sign"))
    }
    if (deliveryRequested && (!rutaFijaLocalRelease || rutaFijaL08Isolated ||
            candidateStorePath.isNullOrBlank() || candidateStorePassword.isNullOrBlank() ||
            candidateKeyPassword.isNullOrBlank())) {
        throw GradleException("RUTA_FIJA_RELEASE_REQUIRES_EXPLICIT_LOCAL_CANDIDATE_SIGNING")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
