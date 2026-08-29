package com.spineplayer.app

import android.content.SharedPreferences
import android.util.Log
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.badlogic.gdx.backends.android.AndroidLiveWallpaperService
import com.spineplayer.app.model.ModelRepository
import com.spineplayer.app.util.Preferences

/**
 * Spine 动态壁纸服务
 *
 * 基于 LibGDX 官方 AndroidLiveWallpaperService
 * 在 onCreateApplication() 中调用 initialize() 启动渲染
 *
 * 支持：
 * - 触摸点击切换动画（通过 SpineGameAdapter 的 InputProcessor）
 * - 模型位置/缩放自定义
 * - 背景颜色自定义
 * - 帧率限制
 */
class SpineWallpaperService : AndroidLiveWallpaperService() {

    private lateinit var preferences: Preferences
    private lateinit var repository: ModelRepository
    private var adapter: SpineGameAdapter? = null
    // 设置变更 → GL 线程刷新壁纸渲染（背景颜色/图片、皮肤、PMA、位置、缩放等即时生效）
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        try {
            Gdx.app.postRunnable { adapter?.refreshSettings() }
        } catch (e: Exception) {
            Log.w(TAG, "Wallpaper refresh failed: ${e.message}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferences = Preferences(this)
        repository = ModelRepository(this)
        Log.d(TAG, "Wallpaper service created")
    }

    /**
     * LibGDX 壁纸初始化入口
     * 在此方法中调用 initialize(ApplicationListener, AndroidApplicationConfiguration)
     * 参考：https://libgdx.com/wiki/app/starter-classes-and-configuration
     */
    override fun onCreateApplication() {
        val config = AndroidApplicationConfiguration().apply {
            useWakelock = false
            useImmersiveMode = false
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            numSamples = 2 // MSAA 抗锯齿
            // 启用壁纸触摸事件
            getTouchEventsForLiveWallpaper = true
        }

        adapter = SpineGameAdapter(this, preferences, repository)
        initialize(adapter, config)
        preferences.registerOnSharedPreferenceChangeListener(prefsListener)
        Log.d(TAG, "Wallpaper initialized")
    }

    override fun onDestroy() {
        if (::preferences.isInitialized) {
            preferences.unregisterOnSharedPreferenceChangeListener(prefsListener)
        }
        adapter = null
        super.onDestroy()
        Log.d(TAG, "Wallpaper service destroyed")
    }

    companion object {
        private const val TAG = "SpineWallpaper"
    }
}
