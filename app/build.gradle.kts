import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val keystoreProps = Properties().apply {
  val f = rootProject.file("keystore.properties")
  if (f.exists()) f.inputStream().use { load(it) }
}

android {
  namespace = "com.criapix.volt"
  compileSdk = 36
  ndkVersion = "30.0.16248370"

  defaultConfig {
    applicationId = "com.criapix.volt"
    minSdk = 26
    targetSdk = 36
    versionCode = 100
    versionName = "1.0.0"
    manifestPlaceholders["appLabel"] = "Volt"
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

  signingConfigs {
    if (keystoreProps.getProperty("storeFile") != null) {
      create("release") {
        storeFile = file(keystoreProps.getProperty("storeFile"))
        storePassword = keystoreProps.getProperty("storePassword")
        keyAlias = keystoreProps.getProperty("keyAlias")
        keyPassword = keystoreProps.getProperty("keyPassword")
      }
    }
  }

  buildTypes {
    debug {
      applicationIdSuffix = ".debug"
      manifestPlaceholders["appLabel"] = "Volt dev"
    }
    release {
      isMinifyEnabled = false   // side-loaded; R8 adds JNI/serialization keep-rule risk for little gain
      signingConfig = signingConfigs.findByName("release")
    }
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
