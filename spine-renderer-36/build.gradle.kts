plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(project(":spine-common"))
    // 3.6 使用本地预重定位 jar（libs/ 下必须存在）：
    // 这是 ASM 将 spine-libgdx 3.6.53.1 的类重定位到 com.spineplayer.spine36 后的成品，
    // 不依赖 mavenCentral，也不依赖 shadowJar 重定位。源码 import 必须用 com.spineplayer.spine36.*。
    implementation(files("libs/spine-libgdx-3.6.53.1-relocated.jar"))
    compileOnly("com.badlogicgames.gdx:gdx:1.12.1")
}

tasks.shadowJar {
    // 本地 jar 已重定位，无需（也无法）再 relocate；此处仅排除公共依赖避免重复打包
    exclude("com/badlogic/**")
    exclude("com/spineplayer/common/**")
    exclude("kotlin/**")
    exclude("kotlinx/**")
    exclude("org/jetbrains/**")
    exclude("org/intellij/**")
    exclude("META-INF/**")
}

configurations {
    create("shadowArtifact") {
        isCanBeConsumed = true
        isCanBeResolved = false
    }
}

artifacts {
    add("shadowArtifact", tasks.shadowJar)
}
