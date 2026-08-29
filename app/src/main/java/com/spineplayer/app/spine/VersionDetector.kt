package com.spineplayer.app.spine
import android.util.Log
import com.spineplayer.common.SpineVersion
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
/**
 * Spine 文件版本检测器
 * 支持 .json（文本）和 .skel（二进制）两种格式
 *
 * JSON 格式：文件中有 "version": "x.x.xx" 字段
 * SKEL 二进制格式：模拟 spine-libgdx SkeletonBinary 的读取逻辑：
 *   字符串 = varint 长度前缀(7bit 分组, MSB 标记续段) + UTF-8 字节
 *   第一个字符串是 hash，第二个字符串是 version
 */
object VersionDetector {
    // 匹配 "version": "4.2.12" 或 "version":4.2（兼容有无引号、有无空格）
    // Spine JSON 的版本字段是 skeleton 下的 "spine"（如 "spine": "3.6.53"），
    // 个别工具可能写 "version"，两者都兼容。
    private val JSON_VERSION_REGEX = Regex(""""(?:version|spine)"\s*:\s*"?([0-9]+\.[0-9]+(?:\.[0-9]+)?)"?""")
    /**
     * 检测 Spine 数据文件的版本
     * @return 检测到的版本，检测失败返回 UNKNOWN
     */
    fun detectVersion(file: File): SpineVersion {
        return when (file.extension.lowercase()) {
            "json" -> detectJsonVersion(file)
            "skel" -> detectSkelVersion(file)
            else -> SpineVersion.UNKNOWN
        }
    }
    private fun detectJsonVersion(file: File): SpineVersion {
        return try {
            // 只读前 8KB，version 字段在文件头部
            val header = file.inputStream().use {
                val buffer = ByteArray(8192)
                val read = it.read(buffer)
                String(buffer, 0, read.coerceAtLeast(0), Charsets.UTF_8)
            }
            val match = JSON_VERSION_REGEX.find(header)
            if (match != null) {
                SpineVersion.fromVersionString(match.groupValues[1])
            } else {
                SpineVersion.UNKNOWN
            }
        } catch (e: Exception) {
            SpineVersion.UNKNOWN
        }
    }
    /**
     * 二进制 .skel 格式检测。
     *
     * spine-libgdx SkeletonBinary 的格式（版本相关，重要）：
     * - Spine 3.8 及更早：hash = readString()（字符串）→ version = readString()
     * - Spine 4.0 / 4.1 / 4.2：hash = readLong()（8 字节整数）→ version = readString()
     *
     * 所以检测必须按格式分流：先按 4.x 格式（跳过 8 字节 hash 再读 version 字符串），
     * 解析不出已知版本再回退 3.8 格式（读两个字符串）。
     *
     * readString 实现（spine 的 SkeletonInput.readString）：
     *   int byteCount = readInt(true)  // varint 编码
     *   byteCount == 0 -> null
     *   byteCount == 1 -> ""
     *   否则读取 (byteCount-1) 个字节，按 UTF-8 解码
     */
    // 锚定正则：4.x 文件的 version 字符串以 "4." 开头（4.0/4.1/4.2/4.3）
    private val V4_HEADER = Regex("""^4\.(\d+)""")
    // 3.x 及更早：version 字符串以 "3." 开头（3.6/3.7/3.8 等），按 minor 分流
    private val V3_HEADER = Regex("""^3\.(\d+)""")

    private fun detectSkelVersion(file: File): SpineVersion {
        // 方案一：4.0+ 格式 [long hash(8字节)][string version]。失败（如文件不足/非 4.x 布局）不阻断，继续回退
        val v40 = try {
            BufferedInputStream(FileInputStream(file)).use { input ->
                skipFully(input, 8)
                val version = readSkelString(input)
                val detected = detectVersionFromString(version, V4_HEADER)
                Log.d(TAG, "detectSkelVersion ${file.name} (4.x layout): hash8=${hash8Label(file)} version='$version' -> $detected")
                detected
            }
        } catch (e: Exception) {
            Log.w(TAG, "detectSkelVersion ${file.name} (4.x layout) failed, will fallback to 3.8 layout: ${e.javaClass.simpleName} ${e.message}")
            SpineVersion.UNKNOWN
        }
        if (v40 != SpineVersion.UNKNOWN) return v40
        // 方案二：3.x 及更早格式 [string hash][string version]（3.6/3.7/3.8 共用此布局）
        val v3Layout = try {
            BufferedInputStream(FileInputStream(file)).use { input ->
                readSkelString(input) // hash
                val version = readSkelString(input)
                val detected = detectVersionFromString(version, V3_HEADER)
                Log.d(TAG, "detectSkelVersion ${file.name} (3.x layout): version='$version' -> $detected")
                detected
            }
        } catch (e: Exception) {
            Log.w(TAG, "detectSkelVersion failed: ${file.name}: ${e.javaClass.simpleName} ${e.message}")
            SpineVersion.UNKNOWN
        }
        if (v3Layout != SpineVersion.UNKNOWN) return v3Layout
        // 方案三：头部明文搜索版本子串。
        // 部分工具/游戏资源打包的 skel，版本号（如 "4.1.20"）以 ASCII 明文出现在文件头部
        // （可能打包在第一个字符串或自定义头中），标准布局解析会错位，改用明文搜索兜底。
        val header = readFirstBytes(file, 512)
        val v4 = Regex("""(?<![0-9])4\.([0-9]+)(?:\.[0-9]+)*""").find(header)
        if (v4 != null) {
            val minor = v4.groupValues[1].toIntOrNull()
            if (minor != null && SpineVersion.fromMajorMinor(4, minor) != SpineVersion.UNKNOWN) {
                val v = SpineVersion.fromMajorMinor(4, minor)
                Log.d(TAG, "detectSkelVersion ${file.name} (header scan): found '${v4.value}' -> $v")
                return v
            }
        }
        val v3 = Regex("""(?<![0-9])3\.(6|8)(?:\.[0-9]+)*""").find(header)
        if (v3 != null) {
            val minor = v3.groupValues[1].toIntOrNull()
            val v = when (minor) {
                6 -> SpineVersion.V3_6
                8 -> SpineVersion.V3_8
                else -> null
            }
            if (v != null) {
                Log.d(TAG, "detectSkelVersion ${file.name} (header scan): found '${v3.value}' -> $v")
                return v
            }
        }
        Log.d(TAG, "detectSkelVersion ${file.name} (header scan): no version pattern in head -> UNKNOWN")
        return SpineVersion.UNKNOWN
    }

    /** 读取文件前 maxBytes 字节用于头部搜索（文件不足则读取全部） */
    private fun readFirstBytes(file: File, maxBytes: Int): String {
        return try {
            file.inputStream().use { input ->
                val buffer = ByteArray(maxBytes)
                val read = input.read(buffer)
                String(buffer, 0, read.coerceAtLeast(0), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            ""
        }
    }

    /** 用锚定正则解析版本：4.x 用 V4_HEADER，3.x 用 V3_HEADER（3.6→V3_6，3.8→V3_8，其他→UNKNOWN） */
    private fun detectVersionFromString(version: String?, header: Regex): SpineVersion {
        val text = version?.trim() ?: return SpineVersion.UNKNOWN
        val m = header.find(text) ?: return SpineVersion.UNKNOWN
        return when (header) {
            V4_HEADER -> {
                val minor = m.groupValues[1].toIntOrNull() ?: return SpineVersion.UNKNOWN
                SpineVersion.fromMajorMinor(4, minor)
            }
            V3_HEADER -> {
                val minor = m.groupValues[1].toIntOrNull() ?: return SpineVersion.UNKNOWN
                when (minor) {
                    6 -> SpineVersion.V3_6
                    8 -> SpineVersion.V3_8
                    else -> SpineVersion.UNKNOWN
                }
            }
            else -> SpineVersion.UNKNOWN
        }
    }

    /** 读取前 8 字节的十六进制，仅用于诊断日志 */
    private fun hash8Label(file: File): String {
        return try {
            file.inputStream().use { input ->
                val buf = ByteArray(8)
                var off = 0
                while (off < 8) {
                    val r = input.read(buf, off, 8 - off)
                    if (r < 0) break
                    off += r
                }
                buf.take(off).joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            "?"
        }
    }

    /** 可靠跳过 n 字节（InputStream.skip 可能跳过不足） */
    private fun skipFully(input: BufferedInputStream, n: Int) {
        var remaining = n
        while (remaining > 0) {
            val skipped = input.skip(remaining.toLong())
            if (skipped > 0) {
                remaining -= skipped.toInt()
            } else {
                if (input.read() < 0) throw java.io.EOFException("Unexpected end of file")
                remaining--
            }
        }
    }
    /**
     * 模拟 SkeletonInput.readString()：读取 varint 长度前缀 + UTF-8 字节
     */
    private fun readSkelString(input: BufferedInputStream): String? {
        val byteCount = readVarint(input)
        return when (byteCount) {
            0 -> null
            1 -> ""
            else -> {
                val bytes = ByteArray(byteCount - 1)
                var offset = 0
                while (offset < bytes.size) {
                    val read = input.read(bytes, offset, bytes.size - offset)
                    if (read < 0) throw java.io.EOFException("Unexpected end of file")
                    offset += read
                }
                String(bytes, Charsets.UTF_8)
            }
        }
    }
    /**
     * 模拟 SkeletonInput.readInt(true)：7-bit 分组 varint（无符号，优化为正数）
     */
    private fun readVarint(input: BufferedInputStream): Int {
        var b = input.read()
        if (b < 0) throw java.io.EOFException("Unexpected end of file")
        var result = b and 0x7f
        if ((b and 0x80) != 0) {
            b = input.read()
            if (b < 0) throw java.io.EOFException("Unexpected end of file")
            result = result or ((b and 0x7f) shl 7)
            if ((b and 0x80) != 0) {
                b = input.read()
                if (b < 0) throw java.io.EOFException("Unexpected end of file")
                result = result or ((b and 0x7f) shl 14)
                if ((b and 0x80) != 0) {
                    b = input.read()
                    if (b < 0) throw java.io.EOFException("Unexpected end of file")
                    result = result or ((b and 0x7f) shl 21)
                    if ((b and 0x80) != 0) {
                        b = input.read()
                        if (b < 0) throw java.io.EOFException("Unexpected end of file")
                        result = result or ((b and 0x7f) shl 28)
                    }
                }
            }
        }
        return result
    }
    /**
     * 判断文件是否为 Spine 骨骼数据文件
     */
    fun isSpineDataFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext == "json" || ext == "skel"
    }
    private const val TAG = "VersionDetector"
}
