package com.spineplayer.spine41

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.utils.Array
import com.esotericsoftware.spine.Animation
import com.esotericsoftware.spine.AnimationState
import com.esotericsoftware.spine.AnimationStateData
import com.esotericsoftware.spine.Skin
import com.esotericsoftware.spine.Skeleton
import com.esotericsoftware.spine.SkeletonBinary
import com.esotericsoftware.spine.SkeletonData
import com.esotericsoftware.spine.SkeletonJson
import com.esotericsoftware.spine.SkeletonRenderer
import com.spineplayer.common.SpineRenderer
import com.spineplayer.common.SpineVersion
import java.io.File

/**
 * Spine 4.1 版本渲染器实现
 * 使用 spine-libgdx 4.1.0 runtime（通过 shadow plugin 重定位到 com.spineplayer.spine41）
 *
 * 与 4.2 的关键 API 差异：
 * - skeleton.updateWorldTransform() 无参数（Physics 是 4.2 才引入的）
 */
class SpineRenderer41 : SpineRenderer {
    override val supportedVersion = SpineVersion.V4_1

    private var atlas: TextureAtlas? = null
    private var skeleton: Skeleton? = null
    private var skeletonData: SkeletonData? = null
    private var animationState: AnimationState? = null
    private var skeletonRenderer: SkeletonRenderer? = null
    private var animationList: List<String> = emptyList()
    private var currentAnimIndex = 0
    /** 已确认 apply 时崩溃的动画名：这些动画数据不完整（通常是文件被工具重打包导致），
     *  播放会触发 AnimationState.apply 越界，需跳过避免每帧崩溃。 */
    private val brokenAnimations = mutableSetOf<String>()

    override val isLoaded: Boolean
        get() = skeleton != null && animationState != null

    override val animationNames: List<String>
        get() = animationList

    override val currentAnimation: String?
        get() = animationState?.getCurrent(0)?.animation?.name

    override val skinNames: List<String>
        get() = skeletonData?.skins?.map { it.name } ?: emptyList()

    override val currentSkin: String?
        get() = skeleton?.getSkin()?.name

    override var premultipliedAlpha: Boolean
        get() = skeletonRenderer?.getPremultipliedAlphaColors() ?: false
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
                    json.scale = 1f
                    json.readSkeletonData(skeletonHandle)
                }
                "skel" -> {
                    val binary = SkeletonBinary(atlas)
                    binary.scale = 1f
                    binary.readSkeletonData(skeletonHandle)
                }
                else -> throw IllegalArgumentException("Unsupported file type: ${skeletonFile.extension}")
            }
            skeletonData = data
            skeleton = Skeleton(data)
            skeleton!!.setToSetupPose()

            val stateData = AnimationStateData(data)
            stateData.defaultMix = 0.2f
            animationState = AnimationState(stateData)

            val animations: Array<Animation> = data.animations
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
            Gdx.app.log(TAG, "Loaded 4.1 model: ${skeletonFile.name}, animations: ${animationList.size}")
            true
        } catch (e: Exception) {
            Gdx.app.error(TAG, "Failed to load 4.1 skeleton: ${skeletonFile.name}", e)
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
                // 该动画数据不完整（文件被重打包/版本错位导致），apply 会反复越界。
                // 记录后停用动画，显示静态模型，避免每帧崩溃把进程杀掉。
                val animName = state.getCurrent(0)?.animation?.name
                if (animName != null) brokenAnimations.add(animName)
                Gdx.app.error(TAG, "AnimationState.apply failed on '$animName', stopping animation to prevent crash", e)
                animationState = null
                skel.setToSetupPose()
            }
        }
        // 4.1: updateWorldTransform 无参数（Physics 在 4.2 才加入）
        skel.updateWorldTransform()
    }

    override fun render(batch: PolygonSpriteBatch, x: Float, y: Float, scale: Float) {
        val skel = skeleton ?: return
        val renderer = skeletonRenderer ?: return
        skel.x = x
        skel.y = y
        skel.scaleX = scale
        skel.scaleY = scale
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
            stateData.defaultMix = 0.2f
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
        return Triple(data.width, data.height, 0f)
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
        private const val TAG = "SpineRenderer41"
    }
}
