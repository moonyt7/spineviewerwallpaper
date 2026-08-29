import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Top-level build file
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.22" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

// 统一所有模块的 Kotlin/JVM 编译目标为 17（与各模块 Java 编译目标 sourceCompatibility=17 保持一致）。
// 修复：在 JDK 24/25 等高版本 JDK 上，Kotlin 编译器自动检测到的 JVM target（如 24/25）与
// Java 编译目标（17）不一致，导致构建报 "Inconsistent JVM Target Compatibility Between Java and Kotlin Tasks"。
subprojects {
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions.jvmTarget = "17"
    }
}
