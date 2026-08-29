package com.spineplayer.app.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.spineplayer.app.MainActivity
import com.spineplayer.app.databinding.FragmentModelListBinding
import com.spineplayer.app.databinding.ItemModelBinding
import com.spineplayer.app.model.ModelRepository
import com.spineplayer.app.model.SpineModelInfo
import com.spineplayer.app.model.ZipImporter
import com.spineplayer.app.util.Preferences
import kotlinx.coroutines.launch

/**
 * 模型列表 Fragment
 * 显示所有已导入的 Spine 模型，支持导入 ZIP、选择模型、删除模型
 */
class ModelListFragment : Fragment() {

    private var _binding: FragmentModelListBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: ModelRepository
    private lateinit var importer: ZipImporter
    private lateinit var preferences: Preferences
    private val models = mutableListOf<SpineModelInfo>()
    private lateinit var adapter: ModelAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentModelListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        repository = ModelRepository(context)
        importer = ZipImporter(context, repository)
        preferences = Preferences(context)

        setupRecyclerView()
        setupButtons()
        loadModels()
    }

    private fun setupRecyclerView() {
        adapter = ModelAdapter(
            models = models,
            onItemClick = { model -> onModelSelected(model) },
            onDeleteClick = { model -> onModelDeleted(model) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        binding.fabImport.setOnClickListener {
            (activity as? MainActivity)?.requestImportZip()
        }

        binding.btnSetWallpaper.setOnClickListener {
            (activity as? MainActivity)?.openWallpaperSettings()
        }
    }

    private fun loadModels() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val loaded = repository.loadModels()
            models.clear()
            models.addAll(loaded)
            adapter.notifyDataSetChanged()
            binding.progressBar.visibility = View.GONE
            binding.emptyView.visibility = if (models.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /**
     * 处理 ZIP 导入（由 MainActivity 调用）
     */
    fun handleZipImport(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val result = importer.importFromUri(uri)
            binding.progressBar.visibility = View.GONE

            if (result.success) {
                Toast.makeText(
                    requireContext(),
                    "成功导入 ${result.models.size} 个模型",
                    Toast.LENGTH_SHORT
                ).show()
                loadModels()
            } else {
                Toast.makeText(
                    requireContext(),
                    result.errorMessage ?: "导入失败",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun onModelSelected(model: SpineModelInfo) {
        preferences.currentModelId = model.id
        Toast.makeText(requireContext(), "已选择: ${model.name}", Toast.LENGTH_SHORT).show()
        // 只切换到播放器页，模型加载由 PlayerFragment.onResume 负责
        // 避免跨 Fragment 直接调用导致视图未就绪的 NPE
        (activity as? MainActivity)?.switchToPlayer()
    }

    private fun onModelDeleted(model: SpineModelInfo) {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.deleteModel(model)
            // 如果删除的是当前模型，清除选择
            if (preferences.currentModelId == model.id) {
                preferences.currentModelId = null
            }
            loadModels()
        }
    }

    override fun onResume() {
        super.onResume()
        loadModels()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * 模型列表适配器
     */
    private class ModelAdapter(
        private val models: List<SpineModelInfo>,
        private val onItemClick: (SpineModelInfo) -> Unit,
        private val onDeleteClick: (SpineModelInfo) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.Adapter<ModelAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemModelBinding) :
            androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemModelBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val model = models[position]
            holder.binding.tvModelName.text = model.name
            holder.binding.tvModelInfo.text = "v${model.version.displayName} · ${model.fileType.uppercase()}"
            holder.binding.root.setOnClickListener { onItemClick(model) }
            holder.binding.btnDelete.setOnClickListener { onDeleteClick(model) }
        }

        override fun getItemCount() = models.size
    }
}
