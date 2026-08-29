package com.spineplayer.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import kotlinx.coroutines.launch
import com.spineplayer.app.MainActivity
import com.spineplayer.app.SpineGameAdapter
import com.spineplayer.app.databinding.FragmentPlayerBinding
import com.spineplayer.app.model.ModelRepository
import com.spineplayer.app.model.SpineModelInfo
import com.spineplayer.app.util.Preferences

/**
 * 播放器 Fragment
 * 使用 LibGDX AndroidFragmentApplication 预览 Spine 模型
 * 支持动画切换、缩放调整
 *
 * 模型加载流程：ModelListFragment 点击模型 → 存 ID 到 Preferences → 切换到此 Fragment
 * → onResume 检测 ID 变化 → 异步加载模型。避免跨 Fragment 直接调用导致视图未就绪 NPE。
 */
class PlayerFragment : Fragment(), AndroidFragmentApplication.Callbacks {

    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    private lateinit var preferences: Preferences
    private lateinit var repository: ModelRepository
    private var gameAdapter: SpineGameAdapter? = null
    private var currentModel: SpineModelInfo? = null
    private var gdxFragment: SpineGdxFragment? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        preferences = Preferences(context)
        repository = ModelRepository(context)
        setupControls()
        setupGdxView()
        // 模型加载由 onResume 统一处理
    }

    override fun onResume() {
        super.onResume()
        // 每次回到播放器页时，检查是否切换了选中的模型
        checkAndLoadSelectedModel()
    }

    /**
     * 检查偏好设置中的当前模型 ID，如果与当前加载的不同则加载新模型
     */
    private fun checkAndLoadSelectedModel() {
        if (_binding == null) return
        val selectedId = preferences.currentModelId ?: return
        if (currentModel?.id == selectedId) return

        viewLifecycleOwner.lifecycleScope.launch {
            val models = repository.loadModels()
            val model = models.firstOrNull { it.id == selectedId }
            if (model != null) {
                applyModel(model)
            }
        }
    }

    private fun setupGdxView() {
        gameAdapter = SpineGameAdapter(requireContext(), preferences, repository)
        val config = com.badlogic.gdx.backends.android.AndroidApplicationConfiguration().apply {
            numSamples = 2
            useWakelock = false
        }
        // 必须使用命名的 SpineGdxFragment，不能用匿名内部类（FragmentManager 无法反射重建）
        gdxFragment = SpineGdxFragment().apply {
            gameListener = gameAdapter
            gameConfig = config
        }
        childFragmentManager.beginTransaction()
            .replace(binding.gdxContainer.id, gdxFragment!!)
            .commit()
    }

    private fun setupControls() {
        binding.spinnerAnimation.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val animName = parent?.getItemAtPosition(position) as? String
                    animName?.let { gameAdapter?.setAnimation(it) }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        binding.btnNextAnim.setOnClickListener {
            val anim = gameAdapter?.nextAnimation()
            anim?.let {
                val pos = (binding.spinnerAnimation.adapter as? ArrayAdapter<String>)?.getPosition(it)
                if (pos != null && pos >= 0) {
                    binding.spinnerAnimation.setSelection(pos)
                }
            }
        }

        binding.seekScale.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val scale = 0.2f + progress / 100f * 3f
                preferences.modelScale = scale
                gameAdapter?.updateModelPosition()
                binding.tvScaleValue.text = String.format("%.1fx", scale)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        binding.btnSetWallpaper.setOnClickListener {
            (activity as? MainActivity)?.openWallpaperSettings()
        }
        // 皮肤选择
        binding.spinnerSkin.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val skinName = parent?.getItemAtPosition(position) as? String
                    skinName?.let { gameAdapter?.setSkin(it) }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        // pma 开关
        binding.switchPma.setOnCheckedChangeListener { _, isChecked ->
            gameAdapter?.setPremultipliedAlpha(isChecked)
        }

        val scaleProgress = ((preferences.modelScale - 0.2f) / 3f * 100).toInt().coerceIn(0, 100)
        binding.seekScale.progress = scaleProgress
        binding.tvScaleValue.text = String.format("%.1fx", preferences.modelScale)
    }

    /**
     * 实际应用模型到 UI 和渲染器（视图必须已创建）
     */
    private fun applyModel(model: SpineModelInfo) {
        val b = _binding ?: return
        currentModel = model
        b.tvModelName.text = model.name
        b.tvModelVersion.text = "Spine ${model.version.displayName} · ${model.fileType.uppercase()}"

        gameAdapter?.requestLoadModel(model) { success ->
            // 在 GL 线程读取动画列表（此时渲染器已加载完成，列表为最新值），
            // 再传递到主线程刷新 UI，避免主线程跨线程读取 GL 对象导致读到空列表
            val anims = gameAdapter?.getAnimationNames().orEmpty()
            activity?.runOnUiThread {
                if (_binding != null) {
                    if (success) {
                        updateAnimationSpinner(anims)
                        updateSkinSpinner()
                        initPmaSwitch()
                    } else {
                        Toast.makeText(requireContext(),
                            "模型加载失败：版本不支持或文件损坏（当前仅支持 Spine 4.2.x）",
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun updateAnimationSpinner(animations: List<String>) {
        val b = _binding ?: return
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            animations
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        b.spinnerAnimation.adapter = adapter

        gameAdapter?.currentAnimation?.let { currentAnim ->
            val pos = animations.indexOf(currentAnim)
            if (pos >= 0) b.spinnerAnimation.setSelection(pos)
        }
    }

    private fun updateSkinSpinner() {
        val b = _binding ?: return
        val skins = gameAdapter?.getSkinNames().orEmpty()
        if (skins.isEmpty()) {
            b.spinnerSkin.adapter = null
            return
        }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            skins
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        b.spinnerSkin.adapter = adapter
        // 恢复当前皮肤选择（初始触发会把 spinner 设为第 0 项，这里纠正到实际皮肤）
        gameAdapter?.currentSkin?.let { cur ->
            val pos = skins.indexOf(cur)
            if (pos >= 0) b.spinnerSkin.setSelection(pos)
        }
    }
    private fun initPmaSwitch() {
        val b = _binding ?: return
        // 初始化时先移除监听，避免把"自动"状态误写为手动设置
        b.switchPma.setOnCheckedChangeListener(null)
        b.switchPma.isChecked = gameAdapter?.isPremultipliedAlpha ?: false
        b.switchPma.setOnCheckedChangeListener { _, isChecked ->
            gameAdapter?.setPremultipliedAlpha(isChecked)
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        // 不手动 dispose gameAdapter：SpineGdxFragment（AndroidFragmentApplication）销毁时
        // 会在 GL 线程自动调用 ApplicationAdapter.dispose()，手动 dispose 会导致双重释放崩溃
        gameAdapter = null
        gdxFragment = null
        currentModel = null // 切回时强制重新加载（gameAdapter 已重建）
        _binding = null
    }

    override fun exit() {
        // LibGDX 回调
    }

    companion object {
        private const val TAG = "PlayerFragment"
    }
}
