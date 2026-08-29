package com.spineplayer.app.model

import com.spineplayer.common.SpineVersion

/**
 * Spine 模型信息
 * 一个模型由 骨骼文件(.skel/.json) + 图集文件(.atlas) + 图片(.png) 组成
 */
data class SpineModelInfo(
    /** 模型唯一 ID（基于路径哈希） */
    val id: String,
    /** 模型显示名称（不含扩展名） */
    val name: String,
    /** 骨骼数据文件路径 */
    val skeletonPath: String,
    /** 图集文件路径 */
    val atlasPath: String,
    /** 模型所在目录路径 */
    val directory: String,
    /** 检测到的 Spine 版本 */
    val version: SpineVersion,
    /** 文件类型：json 或 skel */
    val fileType: String,
    /** 导入时间戳 */
    val importTime: Long = System.currentTimeMillis(),
    /** 动画名称列表（加载后填充） */
    val animationNames: List<String> = emptyList(),
    /** 缩略图路径（可选） */
    val thumbnailPath: String? = null
) {
    companion object {
        fun generateId(skeletonPath: String): String {
            return skeletonPath.hashCode().toUInt().toString(16)
        }
    }
}
