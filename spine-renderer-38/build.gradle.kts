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
    implementation("com.esotericsoftware.spine:spine-libgdx:3.8.99.1")
    compileOnly("com.badlogicgames.gdx:gdx:1.12.1")
}

tasks.shadowJar {
    // 将 spine runtime 重定位到独立包名，避免与其他版本冲突
    relocate("com.esotericsoftware.spine", "com.spineplayer.spine38")
    // LibGDX、Kotlin、公共接口、JetBrains 注解由 app 模块统一提供，不打入 shadow JAR
    // 注解类若打入会与其它模块重复，导致 D8 "Type defined multiple times"
    exclude("com/badlogic/**")
    exclude("com/spineplayer/common/**")
    exclude("kotlin/**")
    exclude("kotlinx/**")
    exclude("org/jetbrains/**")
    exclude("org/intellij/**")
    exclude("META-INF/**")
}

// 自定义配置：让 app 模块消费 shadow JAR（而非普通 JAR）
configurations {
    create("shadowArtifact") {
        isCanBeConsumed = true
        isCanBeResolved = false
    }
}

artifacts {
    add("shadowArtifact", tasks.shadowJar)
}
