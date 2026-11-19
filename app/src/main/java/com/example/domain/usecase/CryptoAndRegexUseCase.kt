package com.example.domain.usecase

import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import okio.ByteString.Companion.encodeUtf8

class CryptoAndRegexUseCase {

    data class CryptoOutputs(
        val base64: String,
        val md5: String,
        val sha256: String,
        val urlEncoded: String,
        val urlDecoded: String
    )

    data class RegexExtractResult(
        val matches: List<String>,
        val error: String?,
        val truncated: Boolean
    )

    fun computeCrypto(input: String): CryptoOutputs {
        if (input.isEmpty()) {
            return CryptoOutputs("", "", "", "", "")
        }
        val bytes = input.toByteArray(Charsets.UTF_8)
        val b64 = input.encodeUtf8().base64()
        val md5 = MessageDigest.getInstance("MD5")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val sha256 = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val urlEncoded = runCatching { URLEncoder.encode(input, "UTF-8") }.getOrDefault(input)
        val urlDecoded = runCatching { URLDecoder.decode(input, "UTF-8") }.getOrDefault(input)

        return CryptoOutputs(
            base64 = b64,
            md5 = md5,
            sha256 = sha256,
            urlEncoded = urlEncoded,
            urlDecoded = urlDecoded
        )
    }

    fun extractRegex(
        pattern: String,
        input: String,
        maxMatches: Int = DEFAULT_MAX_MATCHES
    ): RegexExtractResult {
        if (pattern.isEmpty() || input.isEmpty()) {
            return RegexExtractResult(matches = emptyList(), error = null, truncated = false)
        }
        return runCatching {
            val regex = Regex(pattern)
            val collected = ArrayList<String>()
            var truncated = false
            for (match in regex.findAll(input)) {
                if (match.value.isEmpty() && collected.size >= maxMatches) {
                    truncated = true
                    break
                }
                if (collected.size >= maxMatches) {
                    truncated = true
                    break
                }
                collected.add(match.value)
            }
            RegexExtractResult(matches = collected, error = null, truncated = truncated)
        }.getOrElse { err ->
            RegexExtractResult(
                matches = emptyList(),
                error = err.message ?: "Invalid regex pattern",
                truncated = false
            )
        }
    }

    companion object {
        const val DEFAULT_MAX_MATCHES = 500
    }
}
