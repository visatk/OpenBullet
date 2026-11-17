package com.example.nativeengine

enum class KeyScanOutcome(val code: Int, val label: String) {
    NONE(0, "TO_CHECK"),
    HIT(1, "HIT"),
    CUSTOM(2, "CUSTOM"),
    FAIL(3, "FAIL");

    companion object {
        fun fromCode(code: Int): KeyScanOutcome =
            entries.firstOrNull { it.code == code } ?: NONE
    }
}

data class ComboLineAnalysis(
    val left: String,
    val right: String,
    val isEmailLeft: Boolean,
    val isValidPair: Boolean,
    val fnv1aHashHex: String
)

object NativeScannerBridge {

    val isNativeLoaded: Boolean = runCatching {
        System.loadLibrary("openbullet_scanner")
        true
    }.getOrDefault(false)

    private external fun nativeClassifyResponse(
        body: String,
        successKeys: Array<String>,
        customKeys: Array<String>,
        failureKeys: Array<String>
    ): Int

    private external fun nativeFastHashLine(line: String): Long

    private external fun nativeGetEngineInfo(): String

    fun engineBanner(): String {
        return if (isNativeLoaded) {
            runCatching { nativeGetEngineInfo() }.getOrDefault("NDK C++17 Scanner Active")
        } else {
            "JVM Fast-Scan Engine (NDK C++17 source ready)"
        }
    }

    fun scanResponseKeys(
        body: String,
        successKeys: List<String>,
        customKeys: List<String>,
        failureKeys: List<String>
    ): Pair<KeyScanOutcome, String?> {
        if (isNativeLoaded) {
            val code = runCatching {
                nativeClassifyResponse(
                    body = body,
                    successKeys = successKeys.toTypedArray(),
                    customKeys = customKeys.toTypedArray(),
                    failureKeys = failureKeys.toTypedArray()
                )
            }.getOrNull()
            if (code != null) {
                val outcome = KeyScanOutcome.fromCode(code)
                val matched = when (outcome) {
                    KeyScanOutcome.FAIL -> failureKeys.firstOrNull { body.contains(it, ignoreCase = true) }
                    KeyScanOutcome.HIT -> successKeys.firstOrNull { body.contains(it, ignoreCase = true) }
                    KeyScanOutcome.CUSTOM -> customKeys.firstOrNull { body.contains(it, ignoreCase = true) }
                    KeyScanOutcome.NONE -> null
                }
                return outcome to matched
            }
        }

        // Optimized JVM fallback matching identical precedence (FAIL -> HIT -> CUSTOM -> NONE)
        failureKeys.firstOrNull { it.isNotEmpty() && body.contains(it, ignoreCase = true) }?.let {
            return KeyScanOutcome.FAIL to it
        }
        successKeys.firstOrNull { it.isNotEmpty() && body.contains(it, ignoreCase = true) }?.let {
            return KeyScanOutcome.HIT to it
        }
        customKeys.firstOrNull { it.isNotEmpty() && body.contains(it, ignoreCase = true) }?.let {
            return KeyScanOutcome.CUSTOM to it
        }
        return KeyScanOutcome.NONE to null
    }

    fun fastHashLine(line: String): Long {
        if (isNativeLoaded) {
            val nativeVal = runCatching { nativeFastHashLine(line) }.getOrNull()
            if (nativeVal != null) return nativeVal
        }
        var hash = -3750763034362895579L // 14695981039346656037UL signed
        for (ch in line) {
            hash = hash xor ch.code.toLong()
            hash *= 1099511628211L
        }
        return hash
    }

    fun analyzeComboLine(rawLine: String): ComboLineAnalysis {
        val trimmed = rawLine.trim()
        val sepIndex = trimmed.indexOfAny(charArrayOf(':', ';', '|'))
        val left = if (sepIndex > 0) trimmed.substring(0, sepIndex).trim() else trimmed
        val right = if (sepIndex > 0 && sepIndex + 1 < trimmed.length) trimmed.substring(sepIndex + 1).trim() else ""
        val isEmail = left.contains("@") && left.substringAfter("@").contains(".")
        val hashHex = java.lang.Long.toUnsignedString(fastHashLine(trimmed), 16).padStart(16, '0')
        return ComboLineAnalysis(
            left = left,
            right = right,
            isEmailLeft = isEmail,
            isValidPair = left.isNotEmpty() && right.isNotEmpty(),
            fnv1aHashHex = hashHex
        )
    }
}
