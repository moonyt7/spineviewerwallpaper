package com.spineplayer.common

/**
 * Spine 版本枚举，对应 runtime 的 major.minor
 * 每个版本需要独立的 runtime（通过包名 relocate 隔离）
 */
enum class SpineVersion(val major: Int, val minor: Int, val displayName: String) {
    V3_6(3, 6, "3.6.x"),
    V3_8(3, 8, "3.8.x"),
    V4_0(4, 0, "4.0.x"),
    V4_1(4, 1, "4.1.x"),
    V4_2(4, 2, "4.2.x"),
    V4_3(4, 3, "4.3.x"),
    UNKNOWN(0, 0, "Unknown");

    companion object {
        // 宽容匹配 "x.y" 版本号：容忍首尾空白、引号、BOM、以及 "v4.1" 这类前后缀
        private val VERSION_PATTERN = Regex("""(\d+)\.(\d+)""")
        fun fromVersionString(version: String): SpineVersion {
            val match = VERSION_PATTERN.find(version.trim()) ?: return UNKNOWN
            val major = match.groupValues[1].toIntOrNull() ?: return UNKNOWN
            val minor = match.groupValues[2].toIntOrNull() ?: return UNKNOWN
            return values().firstOrNull { it.major == major && it.minor == minor } ?: UNKNOWN
        }

        fun fromMajorMinor(major: Int, minor: Int): SpineVersion {
            return values().firstOrNull { it.major == major && it.minor == minor } ?: UNKNOWN
        }
    }
}
