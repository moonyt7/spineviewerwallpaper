# Keep LibGDX classes
-keep class com.badlogic.gdx.** { *; }
-dontwarn com.badlogic.gdx.**

# Keep Spine runtime classes
-keep class com.esotericsoftware.spine.** { *; }
-dontwarn com.esotericsoftware.spine.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}
