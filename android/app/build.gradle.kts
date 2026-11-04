import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
}

val ortVersion = "1.31.1"
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
  compileSdk = 37
  ndkVersion = "30.0.16248370"

  defaultConfig {
    applicationId = "io.github.kennyfs.thirteen"
    minSdk = 26
    targetSdk = 37
    // Minutes from 2026-01-01 to the last commit: rises with every commit on any branch or machine, so a newer
    // build always installs over an older one. The name is the vX.Y.Z tag of a release, or a beta
    // after the latest tag for any other build.
    versionCode = (((git("log", "-1", "--format=%ct")?.toLongOrNull() ?: (System.currentTimeMillis() / 1000)) - 1767225600L) / 60).toInt()
    versionName = System.getenv("THIRTEEN_VERSION")
      ?: git("describe", "--tags", "--match", "v[0-9]*", "--abbrev=0")?.let { tag ->
        val since = git("rev-list", "--count", "$tag..HEAD") ?: "0"
        tag.removePrefix("v") + if (since == "0") "" else "+beta.$since"
      } ?: "0.0.${git("rev-list", "--count", "HEAD") ?: "0"}"
    // A build for testing beside the store's (signed with another key) takes another id, e.g. THIRTEEN_ID_SUFFIX=.test.
    System.getenv("THIRTEEN_ID_SUFFIX")?.let { applicationIdSuffix = it }
    // The Google Cloud project for Play Integrity, for the build uploaded to Google Play only: a build from elsewhere
    // (a beta, a test build) cannot pass the check, so it does not try (0).
    buildConfigField("long", "PLAY_PROJECT", (System.getenv("THIRTEEN_PLAY_PROJECT")?.toLongOrNull() ?: 0L).toString() + "L")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    externalNativeBuild {
      cmake {
        // 16 KB pages, which Google Play requires.
        arguments += listOf("-DORT_DIR=${ortDir.get().asFile.absolutePath}", "-DANDROID_STL=c++_shared", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
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
      // arm64 only; THIRTEEN_ABIS=x86_64 makes a release build the emulator runs natively (to try R8 on).
      ndk { abiFilters += (System.getenv("THIRTEEN_ABIS")?.split(",") ?: listOf("arm64-v8a")) }
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName(if (keystorePath != null) "release" else "debug")
    }
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "4.1.2"
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  androidResources {
    // Settings opens the built-in network with openFd, which needs it stored uncompressed.
    noCompress += "onnx"
  }
  packaging {
    // The AAR and the imported library in CMake both bring it.
    jniLibs.pickFirsts += "**/libonnxruntime.so"
    // The Java API's JNI library: unused (the engine calls the C API), and not aligned to 16 KB pages.
    jniLibs.excludes += "**/libonnxruntime4j_jni.so"
  }
}

kotlin {
  compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

tasks.named("preBuild") { dependsOn(extractOrt) }

dependencies {
  ortAar("com.microsoft.onnxruntime:onnxruntime-android:$ortVersion@aar")
  implementation("com.microsoft.onnxruntime:onnxruntime-android:$ortVersion")

  val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
  implementation(composeBom)
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
  implementation("androidx.compose.material:material-icons-extended:1.7.8")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  debugImplementation("androidx.compose.ui:ui-tooling")
  implementation("androidx.compose.material3.adaptive:adaptive:1.3.0")
  implementation("androidx.navigation3:navigation3-runtime:1.2.0")
  implementation("androidx.navigation3:navigation3-ui:1.2.0")
  implementation("androidx.activity:activity-compose:1.13.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
  implementation("androidx.core:core-ktx:1.19.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
  implementation(project(":protocol"))
  implementation("com.squareup.okhttp3:okhttp:5.5.1")
  implementation("com.google.android.gms:play-services-nearby:19.5.1")
  implementation("com.google.android.play:integrity:1.6.0")

  testImplementation("junit:junit:4.13.2")
  androidTestImplementation(composeBom)
  androidTestImplementation("androidx.compose.ui:ui-test-junit4")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
  androidTestImplementation("androidx.test.ext:junit:1.3.0")
  androidTestImplementation("androidx.test:runner:1.7.0")
  androidTestImplementation("androidx.test:core:1.7.0")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
  androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}
