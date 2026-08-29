pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // 阿里云镜像：加速 mavenCentral 依赖下载（3.6/3.8 等 spine-libgdx 均在此）。
        // 若你的网络访问 mavenCentral 稳定，可删除此行。
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        mavenCentral()
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
    }
}
rootProject.name = "SpinePlayer"
include(":app")
include(":spine-common")
include(":spine-renderer-36")
include(":spine-renderer-38")
include(":spine-renderer-40")
include(":spine-renderer-41")
