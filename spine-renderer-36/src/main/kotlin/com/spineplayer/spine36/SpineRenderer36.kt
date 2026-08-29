package com.spineplayer.spine36

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.utils.Array
import com.spineplayer.spine36.Animation
import com.spineplayer.spine36.AnimationState
import com.spineplayer.spine36.AnimationStateData
import com.spineplayer.spine36.Skin
import com.spineplayer.spine36.Skeleton
import com.spineplayer.spine36.SkeletonBinary
import com.spineplayer.spine36.SkeletonData
import com.spineplayer.spine36.SkeletonJson
import com.spineplayer.spine36.SkeletonRenderer
import com.spineplayer.common.SpineRenderer
import com.spineplayer.common.SpineVersion
import java.io.File

/**
 * Spine 3.6 版本渲染器实现
 * 使用 spine-libgdx 3.6.53.1 runtime（通过 shadow plugin 重定位到 com.spineplayer.spine36）
 *
 * 与 3.8/4.x 的关键 API 差异：
 * - 字段不公开，全部走 getter/setter：binary.setScale(1f)（非 binary.scale）、
 *   data.getAnimations()/getWidth()/getHeight()、skel.setPosition()、无模型级 scale（用 rootBone.setScale）
 * - skeleton.updateWorldTransform() 无参数（Physics 是 4.2 才引入的）
 *
 * 注意：3.6 二进制格式与 3.8 不兼容（3.6 无 strings 表、attachmentName 用普通字符串、
 * header 无 x/y、bones 无 skinRequired），必须使用独立 runtime，不能复用 3.8 渲染器。
 */
class SpineRenderer36 : SpineRenderer {
    override val supportedVersion = SpineVersion.V3_6

    private var atlas: TextureAtlas? = null
    private var skeleton: Skeleton? = null
    private var skeletonData: SkeletonData? = null
    private var animationState: AnimationState? = null
    private var skeletonRenderer: SkeletonRenderer? = null
    private var animationList: List<String> = emptyList()
    private var currentAnimIndex = 0
    /** 已确认 apply 时崩溃的动画名：播放会触发 AnimationState.apply 越界，需跳过避免每帧崩溃 */
    private val brokenAnimations = mutableSetOf<String>()

    override val isLoaded: Boolean
        get() = skeleton != null && animationState != null

    override val animationNames: List<String>
        get() = animationList

    override val currentAnimation: String?
        get() = animationState?.getCurrent(0)?.animation?.name

    override val skinNames: List<String>
        get() = skeletonData?.getSkins()?.map { it.getName() } ?: emptyList()

    override val currentSkin: String?
        get() = skeleton?.getSkin()?.getName()

    override var premultipliedAlpha: Boolean
        get() = skeletonRenderer?.getPremultipliedAlpha() ?: false
        set(value) {
            skeletonRenderer?.setPremultipliedAlpha(value)
        }

    override fun load(skeletonFile: File, atlasFile: File): Boolean {
        return try {
            dispose()
            val skeletonHandle = FileHandle(skeletonFile)
            val atlasHandle = FileHandle(atlasFile)
            atlas = TextureAtlas(atlasHandle)

            val data: SkeletonData = when (skeletonFile.extension.lowercase()) {
                "json" -> {
                    val json = SkeletonJson(atlas)
                    json.setScale(1f)
                    json.readSkeletonData(skeletonHandle)
                }
                "skel" -> {
                    val binary = SkeletonBinary(atlas)
                    binary.setScale(1f)
                    binary.readSkeletonData(skeletonHandle)
                }
                else -> throw IllegalArgumentException("Unsupported file type: ${skeletonFile.extension}")
            }
            skeletonData = data
            skeleton = Skeleton(data)
            skeleton!!.setToSetupPose()

            val stateData = AnimationStateData(data)
            stateData.setDefaultMix(0.2f)
            animationState = AnimationState(stateData)

            val animations: Array<Animation> = data.getAnimations()
            animationList = animations.map { it.name }

            skeletonRenderer = SkeletonRenderer()
            // atlas 标记 pma:true 时自动启用预乘 alpha，否则贴图会偏暗/黑边
            if (atlasFile.readText(Charsets.UTF_8).lines().any { it.trim().startsWith("pma:") && it.trim().substringAfter(':').trim().equals("true", ignoreCase = true) }) {
                skeletonRenderer?.setPremultipliedAlpha(true)
                Gdx.app.log(TAG, "atlas pma:true, premultipliedAlpha enabled")
            }

            if (animationList.isNotEmpty()) {
                setAnimation(animationList[0], loop = true)
                currentAnimIndex = 0
            }
            Gdx.app.log(TAG, "Loaded 3.6 model: ${skeletonFile.name}, animations: ${animationList.size}")
            true
        } catch (e: Exception) {
            Gdx.app.error(TAG, "Failed to load 3.6 skeleton: ${skeletonFile.name}", e)
            dispose()
            false
        }
    }

    override fun update(delta: Float) {
        val skel = skeleton ?: return
        val state = animationState
        if (state != null) {
            try {
                state.update(delta)
                state.apply(skel)
            } catch (e: Exception) {
                // 该动画数据不完整（文件被重打包/版本错位），apply 会反复越界。
                // 记录后停用动画，显示静态模型，避免每帧崩溃把进程杀掉。
                val animName = state.getCurrent(0)?.animation?.name
                if (animName != null) brokenAnimations.add(animName)
                Gdx.app.error(TAG, "AnimationState.apply failed on '$animName', stopping animation to prevent crash", e)
                animationState = null
                skel.setToSetupPose()
            }
        }
        // 3.6: updateWorldTransform 无参数
        skel.updateWorldTransform()
    }
    override fun render(batch: PolygonSpriteBatch, x: Float, y: Float, scale: Float) {
        val skel = skeleton ?: return
        val renderer = skeletonRenderer ?: return
        // 3.6 无模型级 scale 字段，缩放设置在根骨骼上
        skel.setPosition(x, y)
        skel.getRootBone().setScale(scale, scale)
        skel.updateWorldTransform()
        renderer.draw(batch, skel)
    }

    override fun setAnimation(name: String, loop: Boolean) {
        // 跳过已知 apply 崩溃的动画，避免再次触发崩溃
        val safeName = if (name in brokenAnimations) {
            animationList.firstOrNull { it !in brokenAnimations } ?: return
        } else name
        // 若动画状态因崩溃被停用，切换时重建，允许从安全动画重新播放
        var state = animationState
        if (state == null) {
            val data = skeletonData ?: return
            val stateData = AnimationStateData(data)
            stateData.setDefaultMix(0.2f)
            state = AnimationState(stateData)
            animationState = state
        }
        state.setAnimation(0, safeName, loop)
        val idx = animationList.indexOf(safeName)
        if (idx >= 0) currentAnimIndex = idx
    }

    override fun nextAnimation(): String? {
        if (animationList.isEmpty()) return null
        if (animationList.all { it in brokenAnimations }) return null
        var attempts = animationList.size
        while (attempts-- > 0) {
            currentAnimIndex = (currentAnimIndex + 1) % animationList.size
            val name = animationList[currentAnimIndex]
            if (name !in brokenAnimations) {
                setAnimation(name, loop = true)
                return name
            }
        }
        return null
    }

    override fun setSkin(name: String) {
        val skel = skeleton ?: return
        try {
            skel.setSkin(name)
            // 切换皮肤后重置到 setup pose，当前动画会基于新皮肤继续播放
            skel.setToSetupPose()
            Gdx.app.log(TAG, "Skin set: $name")
        } catch (e: Exception) {
            Gdx.app.error(TAG, "Failed to set skin: $name", e)
        }
    }

    override fun handleTouch(
        screenX: Float, screenY: Float,
        modelX: Float, modelY: Float, scale: Float
    ): Boolean {
        val (width, height, _) = getBounds()
        val scaledWidth = width * scale
        val scaledHeight = height * scale
        val left = modelX - scaledWidth / 2
        val right = modelX + scaledWidth / 2
        val bottom = modelY
        val top = modelY + scaledHeight
        val hit = screenX in left..right && screenY in bottom..top
        if (hit) nextAnimation()
        return hit
    }

    override fun getBounds(): Triple<Float, Float, Float> {
        val data = skeletonData ?: return Triple(0f, 0f, 0f)
        return Triple(data.getWidth(), data.getHeight(), 0f)
    }

    override fun dispose() {
        atlas?.dispose()
        atlas = null
        skeleton = null
        skeletonData = null
        animationState = null
        skeletonRenderer = null
        animationList = emptyList()
        currentAnimIndex = 0
        brokenAnimations.clear()
    }

    companion object {
        private const val TAG = "SpineRenderer36"
    }
}
