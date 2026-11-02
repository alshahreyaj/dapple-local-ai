plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.localai.cutout"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xenevastudio.dapple"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources { noCompress += "tflite" }
    buildFeatures { compose = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.pickFirsts += "**/libc++_shared.so"
        jniLibs.pickFirsts += "**/libonnxruntime.so"
        jniLibs.useLegacyPackaging = true
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    val camerax = "1.6.2"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    // Match sherpa-onnx 1.13.8's bundled ONNX Runtime so both APIs share one ABI-compatible library.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.28.2")
    // Official OpenCV AAR. 4.5.3's native library was linked for 4 KB pages.
    implementation("org.opencv:opencv:4.12.0")
    // Overrides the older Fragment version pulled in through LiteRT's Play AI Delivery dependency.
    implementation("androidx.fragment:fragment:1.9.1")
    implementation("com.google.ai.edge.litert:litert:2.3.0")
    implementation("com.google.ai.edge.litert:litert-gpu:2.3.0")
    // ExecuTorch 1.4.1 is published as a direct XNNPACK AAR, not as a Maven
    // coordinate. Use the published Maven Central 1.4.0 runtime for these 1.4 PTEs.
    implementation("org.pytorch:executorch-android:1.4.0")
    implementation("com.qualcomm.qti:qnn-runtime:2.50.0")
    implementation("com.qualcomm.qti:qnn-litert-delegate:2.50.0")
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("com.github.wendykierp:JTransforms:3.2")
    implementation("com.github.k2-fsa.sherpa-onnx:sherpa-onnx:v1.13.8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.04.01"))
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
