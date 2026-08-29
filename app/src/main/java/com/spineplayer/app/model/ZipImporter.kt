package com.spineplayer.app.model

import android.content.Context
import android.net.Uri
import com.spineplayer.app.spine.VersionDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * ZIP 模型导入器
 * 支持一个 ZIP 中包含多个 Spine 模型
 *
 * 支持的 ZIP 内部结构：
 * 1. 扁平结构：
 *    model1.skel + model1.atlas + model1.png
 *    model2.json + model2.atlas + model2.png
 *
 * 2. 子目录结构：
 *    model1/model1.skel + model1/model1.atlas + model1/model1.png
 *    model2/model2.json + model2/model2.atlas + model2/model2.png
 *
 * 3. 混合结构
 */
class ZipImporter(
    private val context: Context,
    private val repository: ModelRepository
) {

    /**
     * 导入结果
     */
    data class ImportResult(
        val success: Boolean,
        val models: List<SpineModelInfo> = emptyList(),
        val errorMessage: String? = null,
        val extractedDir: File? = null
    )

    /**
     * 从 Uri 导入 ZIP 文件
     * @param zipUri ZIP 文件的 Content Uri
     * @return 导入结果，包含所有找到的模型
     */
    suspend fun importFromUri(zipUri: Uri): ImportResult = withContext(Dispatchers.IO) {
        return@withContext try {
            // 创建解压目录（以时间戳命名，避免冲突）
            val extractDirName = "import_${System.currentTimeMillis()}"
            val extractDir = File(repository.getModelsDirectory(), extractDirName).apply {
                mkdirs()
            }

            // 解压 ZIP
            val extracted = extractZip(zipUri, extractDir)
            if (!extracted) {
                extractDir.deleteRecursively()
                return@withContext ImportResult(false, errorMessage = "ZIP 解压失败")
            }

            // 扫描模型
            val models = repository.scanForModels(extractDir)

            if (models.isEmpty()) {
                extractDir.deleteRecursively()
                return@withContext ImportResult(
                    false,
                    errorMessage = "未在 ZIP 中找到有效的 Spine 模型（需要 .skel/.json + .atlas 组合）"
                )
            }

            // 保存模型到仓库
            models.forEach { repository.addModel(it) }

            ImportResult(
                success = true,
                models = models,
                extractedDir = extractDir
            )
        } catch (e: Exception) {
            ImportResult(false, errorMessage = "导入失败: ${e.message}")
        }
    }

    /**
     * 解压 ZIP 文件到目标目录
     * 处理路径遍历安全问题
     */
    private fun extractZip(zipUri: Uri, targetDir: File): Boolean {
        return try {
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val entryFile = File(targetDir, entry.name)

                        // 安全检查：防止路径遍历
                        val canonicalPath = entryFile.canonicalPath
                        val canonicalTarget = targetDir.canonicalPath
                        if (!canonicalPath.startsWith(canonicalTarget + File.separator) &&
                            canonicalPath != canonicalTarget) {
                            // 跳过不安全的路径
                            entry = zis.nextEntry
                            continue
                        }

                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                        } else {
                            entryFile.parentFile?.mkdirs()
                            FileOutputStream(entryFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }

                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 从本地文件导入 ZIP
     */
    suspend fun importFromFile(zipFile: File): ImportResult {
        // 将 File 转为 Uri 后复用逻辑
        val uri = Uri.fromFile(zipFile)
        return importFromUri(uri)
    }

    /**
     * 验证 ZIP 文件是否可能包含 Spine 模型
     * 快速检查：ZIP 中是否有 .skel/.json 文件
     */
    suspend fun validateZip(zipUri: Uri): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            var hasSkeleton = false
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val name = entry.name.lowercase()
                            if (name.endsWith(".skel") || name.endsWith(".json")) {
                                hasSkeleton = true
                                break
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            }
            hasSkeleton
        } catch (e: Exception) {
            false
        }
    }
}
