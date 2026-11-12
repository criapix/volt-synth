plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.criapix.volt"
  compileSdk = 36
  ndkVersion = "30.0.16248370"

  defaultConfig {
    applicationId = "com.criapix.volt"
    minSdk = 26
    targetSdk = 36
    versionCode = 3
    versionName = "0.3.0"
    ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_shared" } }
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }

  buildTypes {
    release { isMinifyEnabled = false }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    prefab = true      // Oboe ships as a prefab package
    buildConfig = false
  }
  packaging {
    resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
  }
}

kotlin { jvmToolchain(17) }

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.material3)
  debugImplementation(libs.androidx.compose.ui.tooling)
  implementation(libs.oboe)
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

  testImplementation(libs.junit)
}
