package com.example.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class IpifyResponse(
    val ip: String? = null
)

@JsonClass(generateAdapter = true)
data class HttpProbeResponse(
    val url: String? = null,
    val origin: String? = null,
    val args: Map<String, String>? = null,
    val headers: Map<String, String>? = null
)

data class LoliParseRule(
    val left: String,
    val right: String,
    val captureName: String,
    val isCapture: Boolean = true
)

data class ParsedLoliScript(
    val method: String = "GET",
    val targetUrl: String = "",
    val headers: Map<String, String> = emptyMap(),
    val postBody: String = "",
    val successKeys: List<String> = emptyList(),
    val customKeys: List<String> = emptyList(),
    val failureKeys: List<String> = emptyList(),
    val retryKeys: List<String> = emptyList(),
    val parseRules: List<LoliParseRule> = emptyList(),
    val blocks: List<com.example.model.ConfigBlock> = emptyList()
)

data class HttpCheckResult(
    val status: String,
    val outcomeType: String, // HIT, CUSTOM, BAD, RETRY, TO_CHECK
    val capturedData: String,
    val latencyMs: Long,
    val httpCode: Int
)
