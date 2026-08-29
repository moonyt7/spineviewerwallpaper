plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// LibGDX 原生库配置：用于提取 gdx-platform 的 .so 文件
configurations {
    create("natives")
}

android {
    namespace = "com.spineplayer.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.spineplayer.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
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
        viewBinding = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// 强制所有 AndroidX 依赖到兼容 compileSdk 34 / AGP 8.2.2 的版本
// 防止传递依赖解析到需要 compileSdk 35+ 的最新版本
configurations.all {
    resolutionStrategy {
        force(
            "androidx.core:core-ktx:1.12.0",
            "androidx.core:core:1.12.0",
            "androidx.appcompat:appcompat:1.6.1",
            "androidx.activity:activity-ktx:1.8.2",
            "androidx.activity:activity:1.8.2",
            "androidx.fragment:fragment-ktx:1.6.2",
            "androidx.fragment:fragment:1.6.2",
            "androidx.recyclerview:recyclerview:1.3.2",
            "androidx.constraintlayout:constraintlayout:2.1.4",
            "androidx.preference:preference-ktx:1.2.1",
            "androidx.preference:preference:1.2.1",
            "androidx.documentfile:documentfile:1.0.1",
            "androidx.lifecycle:lifecycle-runtime:2.7.0",
            "androidx.lifecycle:lifecycle-viewmodel:2.7.0",
            "androidx.lifecycle:lifecycle-livedata:2.7.0",
            "androidx.lifecycle:lifecycle-common:2.7.0",
            "androidx.savedstate:savedstate:1.2.1",
            "androidx.collection:collection:1.3.0",
            "androidx.collection:collection-ktx:1.3.0",
            "androidx.annotation:annotation:1.7.1",
            "androidx.annotation:annotation-jvm:1.7.1",
            "com.google.android.material:material:1.10.0",
            "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3",
            "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3",
            // 强制 Compose 相关传递依赖到兼容 compileSdk 34 的版本（项目本身不使用 Compose）。
            // 注意：Compose 1.6+ 起 Google 同时发布带 "-android" 后缀的坐标（如 material-android），
            // 若只 force 不带后缀的坐标，传递依赖仍可能解析到需要 compileSdk 35+ 的极新版本
            // （如 1.10.4），因此这里 -android 与非 -android 两套坐标统一 force 到 1.6.8。
            "androidx.activity:activity-compose:1.8.2",
            "androidx.compose.ui:ui:1.6.8",
            "androidx.compose.ui:ui-android:1.6.8",
            "androidx.compose.ui:ui-graphics:1.6.8",
            "androidx.compose.ui:ui-graphics-android:1.6.8",
            "androidx.compose.ui:ui-text:1.6.8",
            "androidx.compose.ui:ui-text-android:1.6.8",
            "androidx.compose.ui:ui-tooling:1.6.8",
            "androidx.compose.ui:ui-tooling-android:1.6.8",
            "androidx.compose.ui:ui-tooling-data:1.6.8",
            "androidx.compose.ui:ui-tooling-data-android:1.6.8",
            "androidx.compose.foundation:foundation:1.6.8",
            "androidx.compose.foundation:foundation-android:1.6.8",
            "androidx.compose.foundation:foundation-layout:1.6.8",
            "androidx.compose.foundation:foundation-layout-android:1.6.8",
            "androidx.compose.material:material:1.6.8",
            "androidx.compose.material:material-android:1.6.8",
            "androidx.compose.material:material-ripple:1.6.8",
            "androidx.compose.material:material-ripple-android:1.6.8",
            "androidx.compose.material3:material3:1.1.2",
            "androidx.compose.material3:material3-android:1.1.2",
            "androidx.compose.animation:animation:1.6.8",
            "androidx.compose.animation:animation-android:1.6.8",
            "androidx.compose.animation:animation-core:1.6.8",
            "androidx.compose.animation:animation-core-android:1.6.8",
            "androidx.compose.runtime:runtime:1.6.8",
            "androidx.compose.runtime:runtime-android:1.6.8",
            "androidx.compose.runtime:runtime-saveable:1.6.8",
            "androidx.compose.runtime:runtime-saveable-android:1.6.8",
            "androidx.lifecycle:lifecycle-runtime-compose:2.7.0",
            "androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0",
            "androidx.transition:transition:1.4.1"
        )
    }
}

dependencies {
    // AndroidX — 所有版本均兼容 compileSdk 34 / AGP 8.2.2
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.activity:activity-ktx:1.8.2")

    // LibGDX
    val gdxVersion = "1.12.1"
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    // gdx-platform 原生库（.so），必须通过 natives 配置提取
    "natives"("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    "natives"("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    "natives"("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    "natives"("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")

    // Spine 多版本支持模块（3.8/4.0/4.1 通过 shadow plugin relocate 包名）
    implementation(project(":spine-common"))
    implementation(project(":spine-renderer-36", configuration = "shadowArtifact"))
    implementation(project(":spine-renderer-38", configuration = "shadowArtifact"))
    implementation(project(":spine-renderer-40", configuration = "shadowArtifact"))
    implementation(project(":spine-renderer-41", configuration = "shadowArtifact"))
    // Spine 4.2 runtime（直接依赖，无需 relocate）
    implementation("com.esotericsoftware.spine:spine-libgdx:4.2.12")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}

/**
 * 提取 gdx-platform 原生库（.so）到 src/main/jniLibs/<abi>/
 * LibGDX 的 gdx-platform 以 JAR 形式分发 .so 文件，需要手动解压
 */
tasks.register("copyAndroidNatives") {
    doLast {
        val jniLibs = file("src/main/jniLibs")
        jniLibs.mkdirs()
        configurations.getByName("natives").files.forEach { jar ->
            val abi = when {
                jar.name.contains("armeabi-v7a") -> "armeabi-v7a"
                jar.name.contains("arm64-v8a") -> "arm64-v8a"
                jar.name.contains("x86_64") -> "x86_64"
                jar.name.contains("x86") -> "x86"
                else -> return@forEach
            }
            val outputDir = file("${jniLibs.absolutePath}/$abi")
            copy {
                from(zipTree(jar))
                into(outputDir)
                include("*.so")
            }
        }
    }
}

// 在合并 JNI 库之前执行提取
tasks.whenTaskAdded {
    if (name == "mergeDebugJniLibFolders" || name == "mergeReleaseJniLibFolders") {
        dependsOn("copyAndroidNatives")
    }
}
