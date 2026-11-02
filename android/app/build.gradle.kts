plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

val ortVersion = "1.20.0"
// The ONNX Runtime AAR, unpacked so the native engine can compile against its C++ headers and link its
// libonnxruntime.so (headers/ and jni/<abi>/ inside the AAR).
val ortAar: Configuration by configurations.creating
val ortDir = layout.buildDirectory.dir("onnxruntime")
val extractOrt by tasks.registering(Copy::class) {
  from({ zipTree(ortAar.singleFile) })
  into(ortDir)
}

fun git(vararg args: String): String? =
  runCatching { providers.exec { commandLine("git", *args) }.standardOutput.asText.get().trim() }.getOrNull()?.ifEmpty { null }

// Release signing from the environment (build-apk.sh sets it); without it the release build uses the debug key.
val keystorePath: String? = System.getenv("THIRTEEN_KEYSTORE")

android {
  namespace = "io.github.kennyfs.thirteen"
  compileSdk = 35

  defaultConfig {
    applicationId = "io.github.kennyfs.thirteen"
    minSdk = 26
    targetSdk = 35
    // Minutes from 2026-01-01 to the last commit: rises with every commit on any branch or machine, so a newer
    // build always installs over an older one. The name is the vX.Y.Z tag of a release, or a beta
    // after the latest tag for any other build.
    versionCode = (((git("log", "-1", "--format=%ct")?.toLongOrNull() ?: (System.currentTimeMillis() / 1000)) - 1767225600L) / 60).toInt()
    versionName = System.getenv("THIRTEEN_VERSION")
      ?: git("describe", "--tags", "--match", "v[0-9]*", "--abbrev=0")?.let { tag ->
        val since = git("rev-list", "--count", "$tag..HEAD") ?: "0"
        tag.removePrefix("v") + if (since == "0") "" else "+beta.$since"
      } ?: "0.0.${git("rev-list", "--count", "HEAD") ?: "0"}"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    externalNativeBuild {
      cmake {
        arguments += listOf("-DORT_DIR=${ortDir.get().asFile.absolutePath}", "-DANDROID_STL=c++_shared")
      }
    }
  }

  signingConfigs {
    if (keystorePath != null) {
      create("release") {
        storeFile = file(keystorePath)
        storePassword = System.getenv("THIRTEEN_KEYSTORE_PASSWORD")
        keyAlias = System.getenv("THIRTEEN_KEY_ALIAS")
        keyPassword = System.getenv("THIRTEEN_KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    debug {
      // x86_64 for the emulator tests.
      ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    release {
      ndk { abiFilters += "arm64-v8a" }
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName(if (keystorePath != null) "release" else "debug")
    }
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
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
  }
  androidResources {
    // Settings opens the built-in network with openFd, which needs it stored uncompressed.
    noCompress += "onnx"
  }
  packaging {
    // The AAR and the imported library in CMake both bring it.
    jniLibs.pickFirsts += "**/libonnxruntime.so"
  }
}

tasks.named("preBuild") { dependsOn(extractOrt) }

dependencies {
  ortAar("com.microsoft.onnxruntime:onnxruntime-android:$ortVersion@aar")
  implementation("com.microsoft.onnxruntime:onnxruntime-android:$ortVersion")

  val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
  implementation(composeBom)
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.activity:activity-compose:1.9.3")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
  implementation(project(":protocol"))
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("com.google.android.gms:play-services-nearby:19.3.0")

  testImplementation("junit:junit:4.13.2")
  androidTestImplementation(composeBom)
  androidTestImplementation("androidx.compose.ui:ui-test-junit4")
  androidTestImplementation("androidx.test.ext:junit:1.2.1")
  androidTestImplementation("androidx.test:runner:1.6.2")
  androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
