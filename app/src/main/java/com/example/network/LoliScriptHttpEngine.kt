package com.example.network

import com.example.model.ProxyItem
import com.example.model.ProxyStatus
import com.example.nativeengine.KeyScanOutcome
import com.example.nativeengine.NativeScannerBridge
import java.net.URLEncoder
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object LoliScriptParser {
    private val requestRegex = Regex("""REQUEST\s+(GET|POST|PUT|DELETE|HEAD)\s+"([^"]+)"""", RegexOption.IGNORE_CASE)
    private val headerRegex = Regex("""HEADER\s+"([^:"]+):\s*([^"]*)"""", RegexOption.IGNORE_CASE)
    private val contentRegex = Regex("""CONTENT\s+"((?:\\.|[^"\\])*)"""", RegexOption.IGNORE_CASE)
    private val keyRegex = Regex("""KEY\s+"((?:\\.|[^"\\])+)"""", RegexOption.IGNORE_CASE)
    private val parseLrRegex = Regex(
        """PARSE\s+"[^"]*"\s+LR\s+"((?:\\.|[^"\\])*)"\s+"((?:\\.|[^"\\])*)"\s*->\s*CAP\s+"([^"]+)"""",
        RegexOption.IGNORE_CASE
    )

    private fun unescape(raw: String): String =
        raw.replace("\\\"", "\"").replace("\\\\", "\\")

    fun parse(script: String): ParsedLoliScript {
        var method = "GET"
        var url = ""
        val headers = linkedMapOf<String, String>()
        var postBody = ""
        val successKeys = mutableListOf<String>()
        val customKeys = mutableListOf<String>()
        val failureKeys = mutableListOf<String>()
        val parseRules = mutableListOf<LoliParseRule>()

        var activeChain = "SUCCESS"
        for (rawLine in script.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            requestRegex.find(line)?.let { match ->
                method = match.groupValues[1].uppercase()
                url = match.groupValues[2]
                continue
            }
            headerRegex.find(line)?.let { match ->
                headers[match.groupValues[1].trim()] = match.groupValues[2].trim()
                continue
            }
            contentRegex.find(line)?.let { match ->
                postBody = unescape(match.groupValues[1])
                continue
            }
            when {
                line.contains("KEYCHAIN Success", ignoreCase = true) -> activeChain = "SUCCESS"
                line.contains("KEYCHAIN Custom", ignoreCase = true) -> activeChain = "CUSTOM"
                line.contains("KEYCHAIN Failure", ignoreCase = true) -> activeChain = "FAILURE"
            }
            keyRegex.find(line)?.let { match ->
                val key = unescape(match.groupValues[1])
                when (activeChain) {
                    "SUCCESS" -> successKeys.add(key)
                    "CUSTOM" -> customKeys.add(key)
                    "FAILURE" -> failureKeys.add(key)
                }
                continue
            }
            parseLrRegex.find(line)?.let { match ->
                parseRules.add(
                    LoliParseRule(
                        left = unescape(match.groupValues[1]),
                        right = unescape(match.groupValues[2]),
                        captureName = match.groupValues[3]
                    )
                )
            }
        }

        return ParsedLoliScript(
            method = method,
            targetUrl = url,
            headers = headers,
            postBody = postBody,
            successKeys = successKeys,
            customKeys = customKeys,
            failureKeys = failureKeys,
            parseRules = parseRules
        )
    }
}

object HttpExecutionEngine {

    fun executeCheck(
        rawLine: String,
        script: ParsedLoliScript,
        timeoutSec: Int,
        proxyItem: ProxyItem?
    ): HttpCheckResult {
        val analysis = NativeScannerBridge.analyzeComboLine(rawLine)
        if (script.targetUrl.isBlank()) {
            // Local structural check when config does not specify an HTTP target URL
            val outcome = when {
                analysis.isEmailLeft && analysis.isValidPair -> "HIT"
                analysis.isValidPair -> "CUSTOM"
                else -> "TO_CHECK"
            }
            return HttpCheckResult(
                status = if (outcome == "HIT") "VALID_PAIR" else outcome,
                outcomeType = outcome,
                capturedData = "FNV1a=${analysis.fnv1aHashHex.take(8)}",
                latencyMs = 0L,
                httpCode = 0
            )
        }

        val encodedLeft = URLEncoder.encode(analysis.left, "UTF-8")
        val encodedRight = URLEncoder.encode(analysis.right, "UTF-8")
        val encodedRaw = URLEncoder.encode(rawLine.trim(), "UTF-8")

        val resolvedUrl = script.targetUrl
            .replace("<USER>", encodedLeft)
            .replace("<PASS>", encodedRight)
            .replace("<INPUT>", encodedRaw)

        val resolvedBody = script.postBody
            .replace("<USER>", analysis.left)
            .replace("<PASS>", analysis.right)
            .replace("<INPUT>", rawLine.trim())

        val client = OpenBulletNetworkClient.buildClient(timeoutSec, proxyItem)
        val reqBuilder = Request.Builder().url(resolvedUrl)
        script.headers.forEach { (k, v) -> reqBuilder.header(k, v) }

        when (script.method) {
            "POST" -> reqBuilder.post(resolvedBody.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            "PUT" -> reqBuilder.put(resolvedBody.toRequestBody("application/json".toMediaType()))
            "DELETE" -> reqBuilder.delete()
            "HEAD" -> reqBuilder.head()
            else -> reqBuilder.get()
        }

        val startMs = System.currentTimeMillis()
        return runCatching {
            client.newCall(reqBuilder.build()).execute().use { resp ->
                val elapsed = System.currentTimeMillis() - startMs
                val bodyString = resp.body?.string().orEmpty()
                val combinedSource = "HTTP/${resp.code}\n$bodyString"

                val (scanOutcome, matchedKey) = NativeScannerBridge.scanResponseKeys(
                    body = combinedSource,
                    successKeys = script.successKeys,
                    customKeys = script.customKeys,
                    failureKeys = script.failureKeys
                )

                val captures = script.parseRules.mapNotNull { rule ->
                    extractBetween(bodyString, rule.left, rule.right)?.let { "${rule.captureName}=$it" }
                }

                val finalType = when (scanOutcome) {
                    KeyScanOutcome.HIT -> "HIT"
                    KeyScanOutcome.CUSTOM -> "CUSTOM"
                    KeyScanOutcome.FAIL -> "BAD"
                    KeyScanOutcome.NONE -> if (resp.isSuccessful) "HIT" else if (resp.code == 429) "RETRY" else "TO_CHECK"
                }

                val captureSummary = buildList {
                    add("HTTP ${resp.code} (${elapsed}ms)")
                    if (matchedKey != null) add("Key=$matchedKey")
                    addAll(captures)
                }.joinToString(" | ")

                HttpCheckResult(
                    status = "$finalType (${resp.code})",
                    outcomeType = finalType,
                    capturedData = captureSummary,
                    latencyMs = elapsed,
                    httpCode = resp.code
                )
            }
        }.getOrElse { err ->
            val elapsed = System.currentTimeMillis() - startMs
            HttpCheckResult(
                status = "RETRY",
                outcomeType = "RETRY",
                capturedData = err.javaClass.simpleName,
                latencyMs = elapsed,
                httpCode = 0
            )
        }
    }

    fun checkProxy(
        proxy: ProxyItem,
        timeoutSec: Int,
        probeUrl: String = "https://httpbin.org/ip"
    ): Triple<ProxyStatus, Int?, String> {
        val client = OpenBulletNetworkClient.buildClient(timeoutSec, proxy)
        val req = Request.Builder().url(probeUrl).get().build()
        val start = System.currentTimeMillis()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                val ping = (System.currentTimeMillis() - start).toInt()
                when {
                    resp.isSuccessful -> Triple(ProxyStatus.AVAILABLE, ping, "Verified")
                    resp.code == 403 || resp.code == 429 -> Triple(ProxyStatus.BANNED, ping, "HTTP ${resp.code}")
                    else -> Triple(ProxyStatus.BAD, ping, "HTTP ${resp.code}")
                }
            }
        }.getOrElse {
            Triple(ProxyStatus.BAD, null, "Unreachable")
        }
    }

    private fun extractBetween(source: String, left: String, right: String): String? {
        if (left.isEmpty() || right.isEmpty()) return null
        val startIdx = source.indexOf(left)
        if (startIdx < 0) return null
        val valueStart = startIdx + left.length
        val endIdx = source.indexOf(right, valueStart)
        if (endIdx < 0) return null
        return source.substring(valueStart, endIdx).trim().take(80)
    }
}
