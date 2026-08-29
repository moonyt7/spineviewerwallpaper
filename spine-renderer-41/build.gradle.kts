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
    implementation("com.esotericsoftware.spine:spine-libgdx:4.1.0")
    compileOnly("com.badlogicgames.gdx:gdx:1.12.1")
}

tasks.shadowJar {
    relocate("com.esotericsoftware.spine", "com.spineplayer.spine41")
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
