plugins {
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // SpriteBatch 等类型仅编译期需要，运行时由 app 模块提供 LibGDX
    compileOnly("com.badlogicgames.gdx:gdx:1.12.1")
}
