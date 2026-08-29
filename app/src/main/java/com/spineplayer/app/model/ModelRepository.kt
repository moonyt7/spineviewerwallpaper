package com.spineplayer.app.model

import android.content.Context
import com.spineplayer.common.SpineVersion
import com.spineplayer.app.spine.VersionDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 模型仓库：管理所有已导入的 Spine 模型
 * 持久化模型列表到 JSON 文件
 */
class ModelRepository(private val context: Context) {

    private val modelsFile: File
        get() = File(context.filesDir, "models.json")

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    /** 获取模型存储根目录 */
    fun getModelsDirectory(): File = modelsDir

    /**
     * 加载所有已保存的模型
     */
    suspend fun loadModels(): List<SpineModelInfo> = withContext(Dispatchers.IO) {
        if (!modelsFile.exists()) return@withContext emptyList()
        return@withContext try {
            val content = modelsFile.readText()
            val array = JSONArray(content)
            (0 until array.length()).map { i ->
                parseModel(array.getJSONObject(i))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 保存模型列表
     */
    suspend fun saveModels(models: List<SpineModelInfo>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        models.forEach { array.put(serializeModel(it)) }
        modelsFile.writeText(array.toString(2))
    }

    /**
     * 添加模型
     */
    suspend fun addModel(model: SpineModelInfo) {
        val current = loadModels().toMutableList()
        // 去重：同路径只保留一个
        current.removeAll { it.skeletonPath == model.skeletonPath }
        current.add(model)
        saveModels(current)
    }

    /**
     * 删除模型（同时删除文件）
     */
    suspend fun deleteModel(model: SpineModelInfo): Boolean = withContext(Dispatchers.IO) {
        val current = loadModels().toMutableList()
        current.removeAll { it.id == model.id }
        saveModels(current)

        // 删除模型目录（如果是该模型独有的目录）
        val modelDir = File(model.directory)
        if (modelDir.exists() && modelDir.parentFile == modelsDir) {
            modelDir.deleteRecursively()
        }
        true
    }

    /**
     * 扫描目录，查找所有 Spine 模型
     * 一个模型 = 一个 .skel/.json 文件 + 同名 .atlas 文件
     */
    fun scanForModels(directory: File): List<SpineModelInfo> {
        val models = mutableListOf<SpineModelInfo>()
        if (!directory.exists()) return models

        // 递归查找所有 .skel 和 .json 文件
        val skeletonFiles = directory.walkTopDown()
            .filter { it.isFile && VersionDetector.isSpineDataFile(it) }
            .toList()

        for (skeletonFile in skeletonFiles) {
            // 查找同名 .atlas 文件
            val baseName = skeletonFile.nameWithoutExtension
            val atlasFile = findAtlasFile(skeletonFile.parentFile!!, baseName)

            if (atlasFile != null) {
                val version = VersionDetector.detectVersion(skeletonFile)
                val model = SpineModelInfo(
                    id = SpineModelInfo.generateId(skeletonFile.absolutePath),
                    name = baseName,
                    skeletonPath = skeletonFile.absolutePath,
                    atlasPath = atlasFile.absolutePath,
                    directory = skeletonFile.parentFile!!.absolutePath,
                    version = version,
                    fileType = skeletonFile.extension.lowercase()
                )
                models.add(model)
            }
        }

        return models
    }

    /**
     * 查找图集文件：优先同名 .atlas，其次目录中任意 .atlas
     */
    private fun findAtlasFile(dir: File, baseName: String): File? {
        // 1. 同名 .atlas
        val exact = File(dir, "$baseName.atlas")
        if (exact.exists()) return exact

        // 2. 同名 .atlas.txt
        val exactTxt = File(dir, "$baseName.atlas.txt")
        if (exactTxt.exists()) return exactTxt

        // 3. 目录中任意 .atlas 文件（单模型场景）
        val anyAtlas = dir.listFiles { _, name ->
            name.endsWith(".atlas", ignoreCase = true) || name.endsWith(".atlas.txt", ignoreCase = true)
        }?.firstOrNull()
        return anyAtlas
    }

    // ---- JSON 序列化 ----

    private fun serializeModel(model: SpineModelInfo): JSONObject {
        return JSONObject().apply {
            put("id", model.id)
            put("name", model.name)
            put("skeletonPath", model.skeletonPath)
            put("atlasPath", model.atlasPath)
            put("directory", model.directory)
            put("versionMajor", model.version.major)
            put("versionMinor", model.version.minor)
            put("fileType", model.fileType)
            put("importTime", model.importTime)
            put("animationNames", JSONArray(model.animationNames))
            put("thumbnailPath", model.thumbnailPath ?: JSONObject.NULL)
        }
    }

    private fun parseModel(obj: JSONObject): SpineModelInfo {
        val animNames = mutableListOf<String>()
        val animArray = obj.optJSONArray("animationNames")
        if (animArray != null) {
            for (i in 0 until animArray.length()) {
                animNames.add(animArray.getString(i))
            }
        }
        return SpineModelInfo(
            id = obj.getString("id"),
            name = obj.getString("name"),
            skeletonPath = obj.getString("skeletonPath"),
            atlasPath = obj.getString("atlasPath"),
            directory = obj.getString("directory"),
            version = SpineVersion.fromMajorMinor(
                obj.getInt("versionMajor"),
                obj.getInt("versionMinor")
            ),
            fileType = obj.getString("fileType"),
            importTime = obj.optLong("importTime", System.currentTimeMillis()),
            animationNames = animNames,
            thumbnailPath = obj.optString("thumbnailPath", null).takeIf { it.isNotEmpty() }
        )
    }
}
