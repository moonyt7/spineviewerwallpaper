package com.spineplayer.app

import android.util.Log
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.viewport.FitViewport
import com.badlogic.gdx.utils.viewport.Viewport
import com.spineplayer.app.model.ModelRepository
import com.spineplayer.app.model.SpineModelInfo
import com.spineplayer.common.SpineRenderer
import com.spineplayer.app.spine.SpineRendererFactory
import com.spineplayer.app.util.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * LibGDX 应用适配器
 * 负责 Spine 模型的加载、渲染、动画切换和触摸处理
 * 同时用于 Activity 内预览和动态壁纸
 */
class SpineGameAdapter(
    private val context: android.content.Context,
    private val preferences: Preferences,
    private val repository: ModelRepository
) : ApplicationAdapter() {

    private lateinit var batch: PolygonSpriteBatch
    private lateinit var camera: OrthographicCamera
    private lateinit var viewport: Viewport

    private var renderer: SpineRenderer? = null
    private var currentModel: SpineModelInfo? = null

    // 帧率控制
    private var lastFrameTime = 0L
    private val minFrameInterval: Long
        get() = if (preferences.fpsLimit > 0) 1000L / preferences.fpsLimit else 0L

    // 模型显示参数（modelX/modelY 表示模型中心点，单位为屏幕像素）
    private var modelX = 0f
    private var modelY = 0f
    private var modelScale = 1f
    private var modelWidth = 0f
    private var modelHeight = 0f
    private var modelValidSize = false

    // 背景颜色
    private var bgR = 0f
    private var bgG = 0f
    private var bgB = 0f
    private var bgA = 1f

    // 背景图片
    private var bgTexture: Texture? = null
    private var bgSprite: Sprite? = null
    private var bgImagePath: String? = null

    // 手势状态：拖动 / 捏合缩放
    private var pointer0Down = false
    private var pointer1Down = false
    private var lastX0 = 0f
    private var lastY0 = 0f
    private var lastX1 = 0f
    private var lastY1 = 0f
    private var lastDist = 0f
    private var gestureMoved = false
    private val touchSlop = 16f

    override fun create() {
        // 统一使用 PolygonSpriteBatch：spine SkeletonRenderer 渲染加权网格（mesh）需要它，
        // 普通 SpriteBatch 遇到 mesh 会抛 "SpriteBatch cannot render meshes" 异常
        batch = PolygonSpriteBatch()
        camera = OrthographicCamera()
        viewport = FitViewport(
            Gdx.graphics.width.toFloat(),
            Gdx.graphics.height.toFloat(),
            camera
        )
        viewport.apply()

        // 设置输入处理器（用于壁纸触摸事件）
        Gdx.input.inputProcessor = SpineInputProcessor()

        // 刷新全部设置：背景颜色、背景图片、模型位置；皮肤/PMA 在 renderer 加载后由 loadModel 应用
        refreshSettings()
        loadCurrentModel()
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
        updateModelPosition()
        updateBackgroundSprite() // 屏幕尺寸变化后重新铺满
    }

    override fun render() {
        // 帧率限制
        val now = System.currentTimeMillis()
        if (minFrameInterval > 0 && now - lastFrameTime < minFrameInterval) {
            return
        }
        lastFrameTime = now

        val delta = Gdx.graphics.deltaTime.coerceAtMost(0.1f)

        // 清屏（有背景图片时用图片铺底，无图片时用背景颜色）
        if (bgSprite == null) {
            Gdx.gl.glClearColor(bgR, bgG, bgB, bgA)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        } else {
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        }

        // 更新和渲染
        renderer?.update(delta)

        camera.update()
        batch.projectionMatrix = camera.combined
        batch.begin()
        bgSprite?.draw(batch)
        // 传入模型左下角（modelX/modelY 是中心点）
        val ox = if (modelValidSize) modelX - modelWidth * modelScale / 2f else modelX
        val oy = if (modelValidSize) modelY - modelHeight * modelScale / 2f else modelY
        renderer?.render(batch, ox, oy, modelScale)
        batch.end()
    }

    override fun dispose() {
        try {
            renderer?.dispose()
            renderer = null
            if (::batch.isInitialized) {
                batch.dispose()
            }
        } catch (e: Exception) {
            // GL 上下文可能已销毁，忽略 dispose 错误
            Log.w(TAG, "Dispose skipped: ${e.message}")
        }
    }

    /**
     * 请求在 GL 线程加载模型（安全的跨线程入口）
     * 从主线程调用时必须用此方法，不能直接调用 loadModel()
     * @param onComplete 加载完成回调（在 GL 线程执行，更新 UI 需 post 到主线程）
     */
    fun requestLoadModel(model: SpineModelInfo, onComplete: ((Boolean) -> Unit)? = null) {
        try {
            Gdx.app.postRunnable {
                val success = loadModel(model)
                onComplete?.invoke(success)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post model load: ${e.message}")
            onComplete?.invoke(false)
        }
    }

    /**
     * 加载当前选中的模型
     */
    fun loadCurrentModel() {
        val modelId = preferences.currentModelId ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val models = repository.loadModels()
            val model = models.firstOrNull { it.id == modelId }
            if (model != null) {
                Gdx.app.postRunnable {
                    loadModel(model)
                }
            }
        }
    }

    /**
     * 加载指定模型
     */
    fun loadModel(model: SpineModelInfo): Boolean {
        val skeletonFile = File(model.skeletonPath)
        val atlasFile = File(model.atlasPath)

        if (!skeletonFile.exists() || !atlasFile.exists()) {
            Log.e(TAG, "Model files not found: ${model.name}")
            return false
        }

        // 创建对应版本的渲染器
        val newRenderer = SpineRendererFactory.createRenderer(skeletonFile)
        if (newRenderer == null) {
            Log.e(TAG, "Unsupported Spine version: ${model.version} for model ${model.name}")
            return false
        }

        // 释放旧渲染器
        renderer?.dispose()
        renderer = newRenderer
        currentModel = model

        val success = newRenderer.load(skeletonFile, atlasFile)
        if (success) {
            // 设置默认动画
            val defaultAnim = preferences.defaultAnimation
            if (!defaultAnim.isNullOrEmpty() && newRenderer.animationNames.contains(defaultAnim)) {
                newRenderer.setAnimation(defaultAnim, loop = true)
            }
            // 应用用户保存的皮肤（若当前模型包含该皮肤）
            val savedSkin = preferences.skinName
            if (!savedSkin.isNullOrEmpty() && newRenderer.skinNames.contains(savedSkin)) {
                newRenderer.setSkin(savedSkin)
            }
            // 应用 pma 设置（用户手动设置时覆盖渲染器的自动检测）
            preferences.premultipliedAlpha?.let { newRenderer.premultipliedAlpha = it }
            updateModelPosition()
            Log.i(TAG, "Loaded model: ${model.name}, version: ${model.version}")
        } else {
            Log.e(TAG, "Failed to load model: ${model.name} (detected version: ${model.version}). " +
                    "If version is Unknown or not 4.2.x, the model may need a different Spine runtime.")
        }
        return success
    }

    /**
     * 处理触摸事件
     * @return 是否命中模型并切换了动画
     */
    fun handleTouch(screenX: Float, screenY: Float): Boolean {
        if (!preferences.tapToSwitch) return false
        val r = renderer ?: return false
        // InputProcessor 回调的 screenY 是 Android 屏幕坐标（Y 向下），
        // 需翻转为 LibGDX 逻辑坐标（Y 向上）再与模型坐标比较
        val gdxY = Gdx.graphics.height - screenY
        return r.handleTouch(screenX, gdxY, modelX, modelY, modelScale)
    }

    /**
     * 手动切换到下一个动画
     */
    fun nextAnimation(): String? {
        return renderer?.nextAnimation()
    }

    /**
     * 设置指定动画
     */
    fun setAnimation(name: String) {
        renderer?.setAnimation(name, loop = true)
    }

    /**
     * 获取当前动画列表
     */
    fun getAnimationNames(): List<String> {
        return renderer?.animationNames ?: emptyList()
    }

    /**
     * 获取当前播放的动画名称
     */
    val currentAnimation: String?
        get() = renderer?.currentAnimation

    /**
     * 更新模型位置和缩放（根据设置）
     */
    fun updateModelPosition() {
        val width = Gdx.graphics.width.toFloat()
        val height = Gdx.graphics.height.toFloat()
        val (mw, mh, _) = renderer?.getBounds() ?: Triple(0f, 0f, 0f)
        modelWidth = mw
        modelHeight = mh

        // 适配因子：把模型主动适配到屏幕——高度约占屏幕 75%、宽度不超过 90% 屏幕。
        // 既放大也缩小，避免小模型显示偏小；基于模型原始尺寸计算一次（与用户缩放无关）。
        // 仅对"有效"的模型尺寸启用适配：部分第三方工具导出的 skel 的 width/height 字段是
        // 垃圾值（NaN / Infinity / 超大数），此时保持 fit=1 原始渲染，避免模型飞出屏幕。
        var fit = 1f
        modelValidSize = mw > 0f && mh > 0f && mw.isFinite() && mh.isFinite()
                && mw < 1e7f && mh < 1e7f
        if (modelValidSize) {
            fit = minOf((height * 0.75f) / mh, (width * 0.9f) / mw)
                    .coerceIn(0.02f, 12f)
        }
        // 用户缩放线性作用于适配结果：调大 scale 模型真实变大
        modelScale = preferences.modelScale * fit

        // modelX/modelY = 模型中心点（屏幕像素）：
        // offsetX: 0=最左, 0.5=水平居中, 1=最右
        // offsetY: 0=底部, 0.5=垂直居中, 1=顶部
        modelX = preferences.modelOffsetX * width
        modelY = preferences.modelOffsetY * height
    }

    private fun updateBackgroundColor() {
        val color = preferences.backgroundColor
        bgA = ((color shr 24) and 0xFF) / 255f
        bgR = ((color shr 16) and 0xFF) / 255f
        bgG = ((color shr 8) and 0xFF) / 255f
        bgB = (color and 0xFF) / 255f
    }

    /**
     * 刷新设置（设置变更后调用）
     */
    fun refreshSettings() {
        updateBackgroundColor()
        loadBackgroundImage()
        updateModelPosition()
        // 皮肤：用户保存的皮肤在当前模型存在时应用
        val savedSkin = preferences.skinName
        val r = renderer
        if (r != null && !savedSkin.isNullOrEmpty()
            && r.skinNames.contains(savedSkin) && r.currentSkin != savedSkin) {
            r.setSkin(savedSkin)
        }
        // pma：用户手动设置时应用
        preferences.premultipliedAlpha?.let { renderer?.premultipliedAlpha = it }
    }

        fun getCurrentModel(): SpineModelInfo? = currentModel

    // ---------- 皮肤 ----------
    fun getSkinNames(): List<String> {
        return renderer?.skinNames ?: emptyList()
    }
    val currentSkin: String?
        get() = renderer?.currentSkin
    /** 设置皮肤（渲染器 + 持久化） */
    fun setSkin(name: String) {
        renderer?.setSkin(name)
        preferences.skinName = name
    }

    // ---------- 预乘 Alpha（PMA） ----------
    fun setPremultipliedAlpha(value: Boolean) {
        renderer?.premultipliedAlpha = value
        preferences.premultipliedAlpha = value
    }
    val isPremultipliedAlpha: Boolean
        get() = renderer?.premultipliedAlpha ?: false

    // ---------- 背景图片 ----------
    /** 设置背景图片路径并加载（GL 线程外调用，实际加载经 postRunnable） */
    fun setBackgroundImage(path: String?) {
        preferences.backgroundImagePath = path
        Gdx.app.postRunnable { loadBackgroundImage() }
    }
    fun getBackgroundImagePath(): String? = preferences.backgroundImagePath
    private fun loadBackgroundImage() {
        val path = preferences.backgroundImagePath
        if (path == bgImagePath && bgTexture != null) return
        // 释放旧背景
        bgTexture?.dispose()
        bgTexture = null
        bgSprite = null
        bgImagePath = path
        if (path.isNullOrEmpty()) return
        try {
            val file = File(path)
            if (!file.exists()) return
            val tex = Texture(FileHandle(file))
            bgTexture = tex
            bgSprite = Sprite(tex)
            updateBackgroundSprite()
            Log.i(TAG, "Background image loaded: $path")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load background image: ${e.message}")
        }
    }
    private fun updateBackgroundSprite() {
        val sp = bgSprite ?: return
        val vw = Gdx.graphics.width.toFloat()
        val vh = Gdx.graphics.height.toFloat()
        if (vw <= 0f || vh <= 0f) return
        // cover：等比放大铺满屏幕（裁切多余部分）
        val scale = maxOf(vw / sp.regionWidth, vh / sp.regionHeight)
        sp.setSize(sp.regionWidth * scale, sp.regionHeight * scale)
        sp.setCenter(vw / 2f, vh / 2f)
    }

    // ---------- 拖动 / 捏合 ----------
    private fun moveModelBy(dx: Float, dy: Float) {
        val width = Gdx.graphics.width.toFloat()
        val height = Gdx.graphics.height.toFloat()
        if (width <= 0f || height <= 0f) return
        modelX += dx
        modelY += dy
        // 写回归一化偏移（允许拖出屏幕一点，限制在 -0.5 ~ 1.5）
        preferences.modelOffsetX = (modelX / width).coerceIn(-0.5f, 1.5f)
        preferences.modelOffsetY = (modelY / height).coerceIn(-0.5f, 1.5f)
    }
    private fun applyPinchScale(factor: Float) {
        val newScale = (preferences.modelScale * factor).coerceIn(0.05f, 8f)
        preferences.modelScale = newScale
        updateModelPosition()
    }
    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    /**
     * LibGDX 输入处理器
     * 处理壁纸和预览中的触摸事件：
     * - 单指拖动：移动模型位置
     * - 双指捏合：缩放模型
     * - 轻点（未移动且开启 tapToSwitch）：切换动画
     */
    private inner class SpineInputProcessor : InputProcessor {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            val gdxY = (Gdx.graphics.height - screenY).toFloat() // 统一为 Y 向上
            if (pointer == 0) {
                pointer0Down = true
                lastX0 = screenX.toFloat()
                lastY0 = gdxY
            } else if (pointer == 1) {
                pointer1Down = true
                lastX1 = screenX.toFloat()
                lastY1 = gdxY
                lastDist = dist(lastX0, lastY0, lastX1, lastY1)
            }
            gestureMoved = false
            return true
        }
        override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
            val x = screenX.toFloat()
            val y = (Gdx.graphics.height - screenY).toFloat() // 统一为 Y 向上
            if (pointer == 0) {
                if (pointer1Down) {
                    // 双指：捏合缩放
                    val p1x = Gdx.input.getX(1).toFloat()
                    val p1y = Gdx.input.getY(1).toFloat()
                    val newDist = dist(x, y, p1x, p1y)
                    if (lastDist > 1f && newDist > 1f) {
                        applyPinchScale(newDist / lastDist)
                        gestureMoved = true
                    }
                    lastDist = newDist
                    lastX0 = x
                    lastY0 = y
                    lastX1 = p1x
                    lastY1 = p1y
                } else {
                    // 单指：拖动（dx/dy 均为 Y 向上，直接累加到模型中心）
                    val dx = x - lastX0
                    val dy = y - lastY0
                    if (Math.abs(dx) + Math.abs(dy) > touchSlop) gestureMoved = true
                    if (gestureMoved) moveModelBy(dx, dy)
                    lastX0 = x
                    lastY0 = y
                }
            } else if (pointer == 1) {
                if (pointer0Down) {
                    val p0x = Gdx.input.getX(0).toFloat()
                    val p0y = Gdx.input.getY(0).toFloat()
                    val newDist = dist(p0x, p0y, x, y)
                    if (lastDist > 1f && newDist > 1f) {
                        applyPinchScale(newDist / lastDist)
                        gestureMoved = true
                    }
                    lastDist = newDist
                    lastX1 = x
                    lastY1 = y
                }
            }
            return true
        }
        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (pointer == 0) {
                pointer0Down = false
                // 未移动的轻点：若开启点击切换动画且无第二指按下，则触发切换
                if (!gestureMoved && !pointer1Down && preferences.tapToSwitch) {
                    handleTouch(screenX.toFloat(), screenY.toFloat())
                }
            } else if (pointer == 1) {
                pointer1Down = false
            }
            if (!pointer0Down && !pointer1Down) gestureMoved = false
            return false
        }
        override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (pointer == 0) pointer0Down = false
            if (pointer == 1) pointer1Down = false
            return false
        }
        override fun mouseMoved(screenX: Int, screenY: Int): Boolean = false
        override fun scrolled(amountX: Float, amountY: Float): Boolean = false
        override fun keyDown(keycode: Int): Boolean = false
        override fun keyUp(keycode: Int): Boolean = false
        override fun keyTyped(character: Char): Boolean = false
    }

    companion object {
        private const val TAG = "SpineGameAdapter"
    }
}
