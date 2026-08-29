package com.spineplayer.app.spine

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
 * Spine 4.2 版本渲染器实现
 * 使用 spine-libgdx 4.2.12 runtime
 *
 * 对应其他版本的实现类（如 SpineRenderer38）需使用 relocate 后的 runtime，
 * 代码结构与此类基本一致，仅 import 包名不同。
 */
class SpineRenderer42 : SpineRenderer {

    override val supportedVersion = SpineVersion.V4_2

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

            // 加载图集（spine 4.2 使用 LibGDX TextureAtlas）
            atlas = TextureAtlas(atlasHandle)

            // 根据文件类型选择解析器
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

            // 创建骨骼实例
            skeleton = Skeleton(data)
            skeleton!!.setToSetupPose()

            // 创建动画状态
            val stateData = AnimationStateData(data)
            stateData.defaultMix = 0.2f
            animationState = AnimationState(stateData)

            // 收集所有动画名称
            val animations: Array<Animation> = data.animations
            animationList = animations.map { it.name }

            // 渲染器
            skeletonRenderer = SkeletonRenderer()
            // atlas 标记 pma:true 时自动启用预乘 alpha，否则贴图会偏暗/黑边
            if (atlasFile.readText(Charsets.UTF_8).lines().any { it.trim().startsWith("pma:") && it.trim().substringAfter(':').trim().equals("true", ignoreCase = true) }) {
                skeletonRenderer?.setPremultipliedAlpha(true)
                Gdx.app.log(TAG, "atlas pma:true, premultipliedAlpha enabled")
            }

            // 默认播放第一个动画
            if (animationList.isNotEmpty()) {
                setAnimation(animationList[0], loop = true)
                currentAnimIndex = 0
            }

            Gdx.app.log(TAG, "Loaded model: ${skeletonFile.name}, animations: ${animationList.size}")
            true
        } catch (e: Exception) {
            Gdx.app.error(TAG, "Failed to load skeleton: ${skeletonFile.name}", e)
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
        // spine 4.2: updateWorldTransform 需要 Physics 参数
        skel.updateWorldTransform(Skeleton.Physics.update)
    }

    override fun render(batch: PolygonSpriteBatch, x: Float, y: Float, scale: Float) {
        val skel = skeleton ?: return
        val renderer = skeletonRenderer ?: return

        skel.x = x
        skel.y = y
        skel.scaleX = scale
        skel.scaleY = scale
        skel.updateWorldTransform(Skeleton.Physics.update)

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
        if (hit) {
            nextAnimation()
        }
        return hit
    }

    override fun getBounds(): Triple<Float, Float, Float> {
        val data = skeletonData ?: return Triple(0f, 0f, 0f)
        // 使用 SkeletonData 的 width/height，稳定可靠
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
        private const val TAG = "SpineRenderer42"
    }
}
