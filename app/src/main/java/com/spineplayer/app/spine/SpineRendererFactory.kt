package com.spineplayer.app.spine

import com.spineplayer.common.SpineRenderer
import com.spineplayer.common.SpineVersion
import com.spineplayer.spine36.SpineRenderer36
import com.spineplayer.spine38.SpineRenderer38
import com.spineplayer.spine40.SpineRenderer40
import com.spineplayer.spine41.SpineRenderer41
import java.io.File

/**
 * Spine 渲染器工厂
 * 根据模型文件的版本自动选择对应的渲染器实现
 *
 * 各版本 runtime 通过 Gradle Shadow Plugin relocate 到独立包名：
 * - 3.8.x → com.spineplayer.spine38（spine-libgdx 3.8.99.1）
 * - 4.0.x → com.spineplayer.spine40（spine-libgdx 4.0.18.1）
 * - 4.1.x → com.spineplayer.spine41（spine-libgdx 4.1.0）
 * - 4.2.x → com.esotericsoftware.spine（spine-libgdx 4.2.12，直接依赖）
 */
object SpineRendererFactory {

    /**
     * 根据骨骼文件创建对应版本的渲染器
     * @param skeletonFile 骨骼数据文件
     * @return 对应版本的渲染器，若版本不支持则返回 null
     */
    fun createRenderer(skeletonFile: File): SpineRenderer? {
        val version = VersionDetector.detectVersion(skeletonFile)
        return createRenderer(version)
    }

    /**
     * 根据版本创建渲染器
     */
    fun createRenderer(version: SpineVersion): SpineRenderer? {
        return when (version) {
            SpineVersion.V3_6 -> SpineRenderer36()
            SpineVersion.V3_8 -> SpineRenderer38()
            SpineVersion.V4_0 -> SpineRenderer40()
            SpineVersion.V4_1 -> SpineRenderer41()
            SpineVersion.V4_2 -> SpineRenderer42()
            SpineVersion.V4_3 -> {
                // 4.3 与 4.2 API 兼容，尝试用 4.2 渲染器加载
                SpineRenderer42()
            }
            SpineVersion.UNKNOWN -> {
                // 版本未知时尝试用默认 4.2 渲染器
                SpineRenderer42()
            }
        }
    }

    /**
     * 获取当前支持的版本列表
     */
    fun supportedVersions(): List<SpineVersion> {
        return listOf(
            SpineVersion.V3_6,
            SpineVersion.V3_8,
            SpineVersion.V4_0,
            SpineVersion.V4_1,
            SpineVersion.V4_2,
            SpineVersion.V4_3
        )
    }

    /**
     * 检查版本是否受支持
     */
    fun isVersionSupported(version: SpineVersion): Boolean {
        return createRenderer(version) != null
    }
}
