package com.spineplayer.common

import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch
import java.io.File

/**
 * Spine 渲染器统一接口
 * 不同版本的 Spine runtime 通过实现此接口来提供渲染能力
 *
 * 多版本架构：
 * - [com.spineplayer.spine38.SpineRenderer38] 使用 spine-libgdx 3.8.99.1（shadow relocate）
 * - [com.spineplayer.spine40.SpineRenderer40] 使用 spine-libgdx 4.0.18.1（shadow relocate）
 * - [com.spineplayer.spine41.SpineRenderer41] 使用 spine-libgdx 4.1.0（shadow relocate）
 * - SpineRenderer42（app 模块内）使用 spine-libgdx 4.2.12
 * - Factory 根据检测到的版本选择对应的实现
 */
interface SpineRenderer {
    /** 该渲染器支持的 Spine 版本 */
    val supportedVersion: SpineVersion

    /** 当前模型所有动画名称列表 */
    val animationNames: List<String>

    /** 当前播放的动画名称 */
    val currentAnimation: String?

    /** 当前模型所有皮肤名称列表 */
    val skinNames: List<String>

    /** 当前皮肤名称（未单独设置时为 null，表示默认皮肤） */
    val currentSkin: String?

    /** 是否使用预乘 Alpha（PMA）渲染：true=预乘，false=非预乘 */
    var premultipliedAlpha: Boolean

    /** 模型是否已加载 */
    val isLoaded: Boolean

    /**
     * 加载 Spine 模型
     * @param skeletonFile 骨骼数据文件（.json 或 .skel）
     * @param atlasFile 图集文件（.atlas）
     * @return 是否加载成功
     */
    fun load(skeletonFile: File, atlasFile: File): Boolean

    /**
     * 每帧更新动画状态
     * @param delta 距上一帧的时间（秒）
     */
    fun update(delta: Float)

    /**
     * 渲染模型到 PolygonSpriteBatch
     * 调用前需 batch.begin()，调用后 batch.end()
     *
     * 统一使用 PolygonSpriteBatch（而非 SpriteBatch）：
     * spine 各版本 SkeletonRenderer 渲染加权网格（MeshAttachment / weighted mesh）时
     * 都要求 batch 为 PolygonSpriteBatch 或 TwoColorPolygonBatch，普通 SpriteBatch 会抛异常。
     */
    fun render(batch: PolygonSpriteBatch, x: Float, y: Float, scale: Float)

    /** 设置动画 */
    fun setAnimation(name: String, loop: Boolean = true)

    /** 切换到下一个动画（循环切换） */
    fun nextAnimation(): String?

    /** 设置皮肤（name 必须是 skinNames 中的值，切换后重置到 setup pose 并保持当前动画） */
    fun setSkin(name: String)

    /** 处理触摸事件，用于点击切换动画 */
    fun handleTouch(screenX: Float, screenY: Float, modelX: Float, modelY: Float, scale: Float): Boolean

    /** 获取模型边界（用于命中检测和居中），Triple(宽度, 高度, 底部偏移) */
    fun getBounds(): Triple<Float, Float, Float>

    /** 释放资源 */
    fun dispose()
}
