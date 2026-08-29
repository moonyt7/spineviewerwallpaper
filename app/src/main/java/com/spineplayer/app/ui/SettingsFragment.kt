package com.spineplayer.app.ui

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.spineplayer.app.MainActivity
import com.spineplayer.app.R
import com.spineplayer.app.databinding.FragmentSettingsBinding
import com.spineplayer.app.util.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 设置 Fragment
 * 壁纸参数设置：位置、缩放、背景、触摸交互、帧率
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var preferences: Preferences
    // 从相册选择背景图片
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { copyBackgroundImage(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = Preferences(requireContext())
        setupControls()
        loadValues()
    }

    private fun setupControls() {
        // 水平位置
        binding.seekOffsetX.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                preferences.modelOffsetX = progress / 100f
                binding.tvOffsetXValue.text = "${progress}%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 垂直位置
        binding.seekOffsetY.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                preferences.modelOffsetY = progress / 100f
                binding.tvOffsetYValue.text = "${progress}%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 缩放
        binding.seekScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val scale = 0.2f + progress / 100f * 3f
                preferences.modelScale = scale
                binding.tvScaleValue.text = String.format("%.1fx", scale)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 帧率限制
        binding.seekFps.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val fps = if (progress == 0) 0 else 15 + progress * 5 // 0=不限, 20~65
                preferences.fpsLimit = fps
                binding.tvFpsValue.text = if (fps == 0) "不限" else "${fps} FPS"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 点击切换动画开关
        binding.switchTapToSwitch.setOnCheckedChangeListener { _, isChecked ->
            preferences.tapToSwitch = isChecked
        }

        // 背景颜色预设
        binding.btnBgBlack.setOnClickListener { setBackgroundColor(0xFF000000.toInt()) }
        binding.btnBgWhite.setOnClickListener { setBackgroundColor(0xFFFFFFFF.toInt()) }
        binding.btnBgGray.setOnClickListener { setBackgroundColor(0xFF444444.toInt()) }
        binding.btnBgTransparent.setOnClickListener { setBackgroundColor(0x00000000) }
        binding.btnBgCustom.setOnClickListener { showCustomColorDialog() }
        binding.btnChooseBgImage.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.btnClearBgImage.setOnClickListener {
            preferences.backgroundImagePath = null
            loadValues()
        }

        // 设为壁纸按钮
        binding.btnSetWallpaper.setOnClickListener {
            (activity as? MainActivity)?.openWallpaperSettings()
        }

        // 恢复默认
        binding.btnReset.setOnClickListener {
            resetToDefaults()
            Toast.makeText(requireContext(), "已恢复默认设置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setBackgroundColor(color: Int) {
        preferences.backgroundColor = color
    }

    private fun loadValues() {
        binding.seekOffsetX.progress = (preferences.modelOffsetX * 100).toInt()
        binding.tvOffsetXValue.text = "${(preferences.modelOffsetX * 100).toInt()}%"

        binding.seekOffsetY.progress = (preferences.modelOffsetY * 100).toInt()
        binding.tvOffsetYValue.text = "${(preferences.modelOffsetY * 100).toInt()}%"

        val scaleProgress = ((preferences.modelScale - 0.2f) / 3f * 100).toInt().coerceIn(0, 100)
        binding.seekScale.progress = scaleProgress
        binding.tvScaleValue.text = String.format("%.1fx", preferences.modelScale)

        val fpsProgress = if (preferences.fpsLimit == 0) 0
            else ((preferences.fpsLimit - 15) / 5).coerceIn(1, 10)
        binding.seekFps.progress = fpsProgress
        binding.tvFpsValue.text = if (preferences.fpsLimit == 0) "不限" else "${preferences.fpsLimit} FPS"

        binding.switchTapToSwitch.isChecked = preferences.tapToSwitch
        binding.tvBgHint.text = if (preferences.backgroundImagePath != null)
            getString(R.string.bg_image_hint) else getString(R.string.bg_color_hint)
    }

    private fun resetToDefaults() {
        preferences.modelOffsetX = 0.5f
        preferences.modelOffsetY = 0.5f
        preferences.modelScale = 1.0f
        preferences.fpsLimit = 0
        preferences.tapToSwitch = true
        preferences.backgroundColor = 0xFF000000.toInt()
        preferences.backgroundImagePath = null
        preferences.skinName = null
        preferences.premultipliedAlpha = null
        loadValues()
    }

    /**
     * 自定义背景颜色对话框（R/G/B 三滑块 + 实时预览）
     */
    private fun showCustomColorDialog() {
        val ctx = requireContext()
        val current = preferences.backgroundColor
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(20), dp(28), dp(8))
        }
        val preview = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64))
            setBackgroundColor(current)
        }
        val red = SeekBar(ctx).apply { max = 255; progress = (current shr 16) and 0xFF }
        val green = SeekBar(ctx).apply { max = 255; progress = (current shr 8) and 0xFF }
        val blue = SeekBar(ctx).apply { max = 255; progress = current and 0xFF }
        fun updatePreview() {
            preview.setBackgroundColor(
                0xFF000000.toInt() or (red.progress shl 16) or (green.progress shl 8) or blue.progress)
        }
        fun addRow(label: String, bar: SeekBar) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val tv = android.widget.TextView(ctx).apply {
                text = label
                minWidth = dp(40)
            }
            bar.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(tv)
            row.addView(bar)
            layout.addView(row)
        }
        layout.addView(preview)
        addRow(getString(R.string.red), red)
        addRow(getString(R.string.green), green)
        addRow(getString(R.string.blue), blue)
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { updatePreview() }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        red.setOnSeekBarChangeListener(listener)
        green.setOnSeekBarChangeListener(listener)
        blue.setOnSeekBarChangeListener(listener)
        AlertDialog.Builder(ctx)
            .setTitle(R.string.custom_color)
            .setView(layout)
            .setPositiveButton(R.string.confirm) { _, _ ->
                setBackgroundColor(0xFF000000.toInt() or (red.progress shl 16) or (green.progress shl 8) or blue.progress)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * 从相册选择的图片复制到应用私有目录并设为背景
     */
    private fun copyBackgroundImage(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val destDir = File(requireContext().filesDir, "backgrounds").apply { mkdirs() }
            val destFile = File(destDir, "bg_${System.currentTimeMillis()}.jpg")
            try {
                requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { out -> input.copyTo(out) }
                }
                withContext(Dispatchers.Main) {
                    if (destFile.exists() && destFile.length() > 0) {
                        preferences.backgroundImagePath = destFile.absolutePath
                        Toast.makeText(requireContext(), R.string.bg_image_copied, Toast.LENGTH_SHORT).show()
                        loadValues()
                    } else {
                        Toast.makeText(requireContext(), R.string.bg_image_failed, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), R.string.bg_image_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
