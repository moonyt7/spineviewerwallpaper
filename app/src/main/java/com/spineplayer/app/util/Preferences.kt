package com.spineplayer.app.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用设置管理
 */
class Preferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("spine_player_prefs", Context.MODE_PRIVATE)

    // 当前选中的模型 ID
    var currentModelId: String?
        get() = prefs.getString(KEY_CURRENT_MODEL, null)
        set(value) = prefs.edit().putString(KEY_CURRENT_MODEL, value).apply()

    // 模型缩放比例
    var modelScale: Float
        get() = prefs.getFloat(KEY_SCALE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_SCALE, value).apply()

    // 模型水平偏移（0~1，0.5 为居中）
    var modelOffsetX: Float
        get() = prefs.getFloat(KEY_OFFSET_X, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_OFFSET_X, value).apply()

    // 模型垂直偏移（0~1，0.5 为居中）
    var modelOffsetY: Float
        get() = prefs.getFloat(KEY_OFFSET_Y, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_OFFSET_Y, value).apply()

    // 背景颜色（ARGB）
    var backgroundColor: Int
        get() = prefs.getInt(KEY_BG_COLOR, 0xFF000000.toInt())
        set(value) = prefs.edit().putInt(KEY_BG_COLOR, value).apply()
    // 背景图片路径（null=无图片背景，使用背景颜色）
    var backgroundImagePath: String?
        get() = prefs.getString(KEY_BG_IMAGE, null)
        set(value) = prefs.edit().putString(KEY_BG_IMAGE, value).apply()
    // 当前皮肤名称（null=默认皮肤）
    var skinName: String?
        get() = prefs.getString(KEY_SKIN, null)
        set(value) = prefs.edit().putString(KEY_SKIN, value).apply()
    // 是否启用预乘 Alpha（null=按 atlas 自动）
    var premultipliedAlpha: Boolean?
        get() = if (prefs.contains(KEY_PMA)) prefs.getBoolean(KEY_PMA, false) else null
        set(value) {
            val e = prefs.edit()
            if (value == null) e.remove(KEY_PMA) else e.putBoolean(KEY_PMA, value)
            e.apply()
        }

    // 点击切换动画是否启用
    var tapToSwitch: Boolean
        get() = prefs.getBoolean(KEY_TAP_SWITCH, true)
        set(value) = prefs.edit().putBoolean(KEY_TAP_SWITCH, value).apply()

    // 壁纸帧率限制（0 = 不限制）
    var fpsLimit: Int
        get() = prefs.getInt(KEY_FPS_LIMIT, 0)
        set(value) = prefs.edit().putInt(KEY_FPS_LIMIT, value).apply()

    // 默认动画名称
    var defaultAnimation: String?
        get() = prefs.getString(KEY_DEFAULT_ANIM, null)
        set(value) = prefs.edit().putString(KEY_DEFAULT_ANIM, value).apply()

    /** 注册设置变更监听（用于壁纸等常驻场景即时刷新） */
    fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(l)
    }
    fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(l)
    }
    fun getRawSharedPreferences(): SharedPreferences = prefs

    companion object {
        private const val KEY_CURRENT_MODEL = "current_model_id"
        private const val KEY_SCALE = "model_scale"
        private const val KEY_OFFSET_X = "model_offset_x"
        private const val KEY_OFFSET_Y = "model_offset_y"
        private const val KEY_BG_COLOR = "bg_color"
        private const val KEY_BG_IMAGE = "bg_image_path"
        private const val KEY_SKIN = "skin_name"
        private const val KEY_PMA = "premultiplied_alpha"
        private const val KEY_TAP_SWITCH = "tap_to_switch"
        private const val KEY_FPS_LIMIT = "fps_limit"
        private const val KEY_DEFAULT_ANIM = "default_animation"
    }
}
