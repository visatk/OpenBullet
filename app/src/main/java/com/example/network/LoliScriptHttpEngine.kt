package com.example.network

import android.util.Base64
import com.example.model.ConfigBlock
import com.example.model.ConfigDebugResult
import com.example.model.ParseMode
import com.example.model.ProxyItem
import com.example.model.ProxyStatus
import com.example.model.ScriptFunctionType
import com.example.nativeengine.KeyScanOutcome
import com.example.nativeengine.NativeScannerBridge
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object ConfigTemplates {
    data class TemplatePreset(
        val id: String,
        val title: String,
        val description: String,
        val category: String,
        val wordlistType: String,
        val needsProxies: Boolean,
        val script: String
    )

    val presets: List<TemplatePreset> = listOf(
        TemplatePreset(
            id = "httpbin_get",
            title = "HTTPBin Auth & Origin",
            description = "GET request to httpbin.org, verifies HTTP/200 and captures Origin IP.",
            category = "API",
            wordlistType = "Mail:Pass",
            needsProxies = true,
            script = """
                # LoliScript HTTP Check
                REQUEST GET "https://httpbin.org/get?user=<USER>"
                HEADER "Accept: application/json"
                REDIRECT true
                KEYCHECK
                  KEYCHAIN Success OR
                    KEY "HTTP/200"
                  KEYCHAIN Failure OR
                    KEY "Unauthorized"
                  KEYCHAIN Retry OR
                    KEY "HTTP/429"
                PARSE "<SOURCE>" LR "\"origin\": \"" "\"" -> CAP "Origin"
            """.trimIndent()
        ),
        TemplatePreset(
            id = "httpbin_post_json",
            title = "JSON POST + Capture",
            description = "Base64-encodes credentials, POSTs JSON, parses response JSON field.",
            category = "Auth",
            wordlistType = "User:Pass",
            needsProxies = true,
            script = """
                # Multi-Block JSON Auth Pipeline
                FUNCTION BASE64ENCODE "<USER>:<PASS>" -> VAR "AUTH_TOKEN"
                REQUEST POST "https://httpbin.org/post"
                HEADER "Accept: application/json"
                HEADER "Authorization: Basic <AUTH_TOKEN>"
                CONTENT_TYPE "application/json"
                CONTENT "{\"user\":\"<USER>\",\"pass\":\"<PASS>\"}"
                REDIRECT true
                KEYCHECK
                  KEYCHAIN Success OR
                    KEY "HTTP/200"
                  KEYCHAIN Failure OR
                    KEY "HTTP/401"
                    KEY "Invalid"
                  KEYCHAIN Retry OR
                    KEY "HTTP/429"
                PARSE "<SOURCE>" JSON "json.user" -> CAP "Username"
                PARSE "<SOURCE>" JSON "origin" -> CAP "Origin"
            """.trimIndent()
        ),
        TemplatePreset(
            id = "local_validator",
            title = "Local Combo Validator",
            description = "Offline native structural + FNV-1a combo inspection (no HTTP call).",
            category = "Utility",
            wordlistType = "Mail:Pass",
            needsProxies = false,
            script = """
                # Offline Native Combo Validator
                KEYCHECK
                  KEYCHAIN Success OR
                    KEY "@"
            """.trimIndent()
        )
    )
}

object LoliScriptParser {
    private val requestRegex = Regex("""REQUEST\s+(GET|POST|PUT|DELETE|HEAD)\s+"((?:\\.|[^"\\])+)"""", RegexOption.IGNORE_CASE)
    private val headerRegex = Regex("""HEADER\s+"((?:\\.|[^"\\])+)"""", RegexOption.IGNORE_CASE)
    private val contentTypeRegex = Regex("""CONTENT_TYPE\s+"((?:\\.|[^"\\])+)"""", RegexOption.IGNORE_CASE)
    private val contentRegex = Regex("""CONTENT\s+"((?:\\.|[^"\\])*)"""", RegexOption.IGNORE_CASE)
    private val redirectRegex = Regex("""REDIRECT\s+(true|false)""", RegexOption.IGNORE_CASE)
    private val keyRegex = Regex("""KEY\s+"((?:\\.|[^"\\])+)"""", RegexOption.IGNORE_CASE)
    private val parseLrRegex = Regex(
        """PARSE\s+"([^"]*)"\s+LR\s+"((?:\\.|[^"\\])*)"\s+"((?:\\.|[^"\\])*)"\s*->\s*(CAP|VAR)\s+"([^"]+)"""",
        RegexOption.IGNORE_CASE
    )
    private val parseSingleRegex = Regex(
        """PARSE\s+"([^"]*)"\s+(REGEX|JSON)\s+"((?:\\.|[^"\\])*)"\s*->\s*(CAP|VAR)\s+"([^"]+)"""",
        RegexOption.IGNORE_CASE
    )
    private val functionRegex = Regex(
        """FUNCTION\s+([A-Z0-9_]+)\s+"((?:\\.|[^"\\])*)"\s*->\s*VAR\s+"([^"]+)"""",
        RegexOption.IGNORE_CASE
    )

    private fun unescape(raw: String): String =
        raw.replace("\\\"", "\"").replace("\\\\", "\\")

    private fun escape(raw: String): String =
        raw.replace("\\", "\\\\").replace("\"", "\\\"")

    fun parse(script: String): ParsedLoliScript {
        val blocks = parseToBlocks(script)
        val firstReq = blocks.filterIsInstance<ConfigBlock.RequestBlock>().firstOrNull()
        val keyChecks = blocks.filterIsInstance<ConfigBlock.KeyCheckBlock>()
        val parseBlocks = blocks.filterIsInstance<ConfigBlock.ParseBlock>()

        val headers = linkedMapOf<String, String>()
        firstReq?.headersText?.lineSequence()?.forEach { hLine ->
            val idx = hLine.indexOf(':')
            if (idx > 0) {
                headers[hLine.substring(0, idx).trim()] = hLine.substring(idx + 1).trim()
            }
        }

        return ParsedLoliScript(
            method = firstReq?.method ?: "GET",
            targetUrl = firstReq?.url ?: "",
            headers = headers,
            postBody = firstReq?.postBody ?: "",
            successKeys = keyChecks.flatMap { it.successKeys },
            customKeys = keyChecks.flatMap { it.customKeys },
            failureKeys = keyChecks.flatMap { it.failureKeys },
            retryKeys = keyChecks.flatMap { it.retryKeys },
            parseRules = parseBlocks.map {
                LoliParseRule(
                    left = it.leftOrPattern,
                    right = it.right,
                    captureName = it.outputName,
                    isCapture = it.isCapture
                )
            },
            blocks = blocks
        )
    }

    fun parseToBlocks(script: String): List<ConfigBlock> {
        val result = mutableListOf<ConfigBlock>()
        var currentRequest: ConfigBlock.RequestBlock? = null
        var currentKeyCheck: ConfigBlock.KeyCheckBlock? = null
        var activeChain = "SUCCESS"

        fun flushRequest() {
            currentRequest?.let {
                result.add(it)
                currentRequest = null
            }
        }

        fun flushKeyCheck() {
            currentKeyCheck?.let {
                result.add(it)
                currentKeyCheck = null
            }
        }

        for (rawLine in script.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue

            requestRegex.find(line)?.let { match ->
                flushRequest()
                flushKeyCheck()
                currentRequest = ConfigBlock.RequestBlock(
                    id = UUID.randomUUID().toString(),
                    method = match.groupValues[1].uppercase(),
                    url = unescape(match.groupValues[2]),
                    headersText = "",
                    postBody = ""
                )
                continue
            }

            if (currentRequest != null) {
                headerRegex.find(line)?.let { match ->
                    val rawHeader = unescape(match.groupValues[1])
                    val existing = currentRequest!!.headersText
                    currentRequest = currentRequest!!.copy(
                        headersText = if (existing.isBlank()) rawHeader else "$existing\n$rawHeader"
                    )
                    continue
                }
                contentTypeRegex.find(line)?.let { match ->
                    currentRequest = currentRequest!!.copy(contentType = unescape(match.groupValues[1]))
                    continue
                }
                contentRegex.find(line)?.let { match ->
                    currentRequest = currentRequest!!.copy(postBody = unescape(match.groupValues[1]))
                    continue
                }
                redirectRegex.find(line)?.let { match ->
                    currentRequest = currentRequest!!.copy(followRedirects = match.groupValues[1].equals("true", ignoreCase = true))
                    continue
                }
            }

            if (line.equals("KEYCHECK", ignoreCase = true)) {
                flushRequest()
                flushKeyCheck()
                currentKeyCheck = ConfigBlock.KeyCheckBlock(
                    id = UUID.randomUUID().toString(),
                    successKeys = emptyList(),
                    customKeys = emptyList(),
                    failureKeys = emptyList(),
                    retryKeys = emptyList()
                )
                activeChain = "SUCCESS"
                continue
            }

            if (line.contains("KEYCHAIN", ignoreCase = true)) {
                flushRequest()
                if (currentKeyCheck == null) {
                    currentKeyCheck = ConfigBlock.KeyCheckBlock(
                        id = UUID.randomUUID().toString(),
                        successKeys = emptyList(),
                        customKeys = emptyList(),
                        failureKeys = emptyList(),
                        retryKeys = emptyList()
                    )
                }
                activeChain = when {
                    line.contains("Success", ignoreCase = true) -> "SUCCESS"
                    line.contains("Custom", ignoreCase = true) -> "CUSTOM"
                    line.contains("Failure", ignoreCase = true) -> "FAILURE"
                    line.contains("Retry", ignoreCase = true) || line.contains("Ban", ignoreCase = true) -> "RETRY"
                    else -> "SUCCESS"
                }
                continue
            }

            keyRegex.find(line)?.let { match ->
                flushRequest()
                val kc = currentKeyCheck ?: ConfigBlock.KeyCheckBlock(
                    id = UUID.randomUUID().toString(),
                    successKeys = emptyList(),
                    customKeys = emptyList(),
                    failureKeys = emptyList(),
                    retryKeys = emptyList()
                )
                val key = unescape(match.groupValues[1])
                currentKeyCheck = when (activeChain) {
                    "SUCCESS" -> kc.copy(successKeys = kc.successKeys + key)
                    "CUSTOM" -> kc.copy(customKeys = kc.customKeys + key)
                    "FAILURE" -> kc.copy(failureKeys = kc.failureKeys + key)
                    "RETRY" -> kc.copy(retryKeys = kc.retryKeys + key)
                    else -> kc
                }
                continue
            }

            parseLrRegex.find(line)?.let { match ->
                flushRequest()
                flushKeyCheck()
                result.add(
                    ConfigBlock.ParseBlock(
                        id = UUID.randomUUID().toString(),
                        sourceVar = match.groupValues[1].ifBlank { "<SOURCE>" },
                        mode = ParseMode.LR,
                        leftOrPattern = unescape(match.groupValues[2]),
                        right = unescape(match.groupValues[3]),
                        isCapture = match.groupValues[4].equals("CAP", ignoreCase = true),
                        outputName = match.groupValues[5]
                    )
                )
                continue
            }

            parseSingleRegex.find(line)?.let { match ->
                flushRequest()
                flushKeyCheck()
                val mode = if (match.groupValues[2].equals("JSON", ignoreCase = true)) ParseMode.JSON else ParseMode.REGEX
                result.add(
                    ConfigBlock.ParseBlock(
                        id = UUID.randomUUID().toString(),
                        sourceVar = match.groupValues[1].ifBlank { "<SOURCE>" },
                        mode = mode,
                        leftOrPattern = unescape(match.groupValues[3]),
                        right = "",
                        isCapture = match.groupValues[4].equals("CAP", ignoreCase = true),
                        outputName = match.groupValues[5]
                    )
                )
                continue
            }

            functionRegex.find(line)?.let { match ->
                flushRequest()
                flushKeyCheck()
                val keyword = match.groupValues[1].uppercase()
                val fnType = ScriptFunctionType.entries.firstOrNull {
                    it.loliKeyword.equals(keyword, ignoreCase = true) || it.name.equals(keyword, ignoreCase = true)
                } ?: ScriptFunctionType.BASE64_ENCODE
                result.add(
                    ConfigBlock.FunctionBlock(
                        id = UUID.randomUUID().toString(),
                        functionType = fnType,
                        inputTemplate = unescape(match.groupValues[2]),
                        outputVar = match.groupValues[3]
                    )
                )
            }
        }

        flushRequest()
        flushKeyCheck()
        return result
    }

    fun serializeBlocks(blocks: List<ConfigBlock>): String {
        if (blocks.isEmpty()) return "# Empty LoliScript Config"
        return buildString {
            blocks.forEachIndexed { index, block ->
                if (index > 0) appendLine()
                when (block) {
                    is ConfigBlock.RequestBlock -> {
                        appendLine("REQUEST ${block.method.uppercase()} \"${escape(block.url.trim())}\"")
                        block.headersText.lineSequence()
                            .map { it.trim() }
                            .filter { it.contains(":") }
                            .forEach { headerLine ->
                                appendLine("HEADER \"${escape(headerLine)}\"")
                            }
                        if (block.method.uppercase() in setOf("POST", "PUT")) {
                            if (block.contentType.isNotBlank()) {
                                appendLine("CONTENT_TYPE \"${escape(block.contentType.trim())}\"")
                            }
                            appendLine("CONTENT \"${escape(block.postBody)}\"")
                        }
                        append("REDIRECT ${block.followRedirects}")
                    }

                    is ConfigBlock.FunctionBlock -> {
                        append(
                            "FUNCTION ${block.functionType.loliKeyword} \"${escape(block.inputTemplate)}\" -> VAR \"${block.outputVar.trim()}\""
                        )
                    }

                    is ConfigBlock.ParseBlock -> {
                        val destKind = if (block.isCapture) "CAP" else "VAR"
                        when (block.mode) {
                            ParseMode.LR -> append(
                                "PARSE \"${block.sourceVar}\" LR \"${escape(block.leftOrPattern)}\" \"${escape(block.right)}\" -> $destKind \"${block.outputName.trim()}\""
                            )
                            ParseMode.REGEX -> append(
                                "PARSE \"${block.sourceVar}\" REGEX \"${escape(block.leftOrPattern)}\" -> $destKind \"${block.outputName.trim()}\""
                            )
                            ParseMode.JSON -> append(
                                "PARSE \"${block.sourceVar}\" JSON \"${escape(block.leftOrPattern)}\" -> $destKind \"${block.outputName.trim()}\""
                            )
                        }
                    }

                    is ConfigBlock.KeyCheckBlock -> {
                        appendLine("KEYCHECK")
                        if (block.successKeys.isNotEmpty()) {
                            appendLine("  KEYCHAIN Success OR")
                            block.successKeys.filter { it.isNotBlank() }.forEach { k ->
                                appendLine("    KEY \"${escape(k)}\"")
                            }
                        }
                        if (block.customKeys.isNotEmpty()) {
                            appendLine("  KEYCHAIN Custom OR")
                            block.customKeys.filter { it.isNotBlank() }.forEach { k ->
                                appendLine("    KEY \"${escape(k)}\"")
                            }
                        }
                        if (block.failureKeys.isNotEmpty()) {
                            appendLine("  KEYCHAIN Failure OR")
                            block.failureKeys.filter { it.isNotBlank() }.forEach { k ->
                                appendLine("    KEY \"${escape(k)}\"")
                            }
                        }
                        if (block.retryKeys.isNotEmpty()) {
                            appendLine("  KEYCHAIN Retry OR")
                            block.retryKeys.filter { it.isNotBlank() }.forEach { k ->
                                appendLine("    KEY \"${escape(k)}\"")
                            }
                        }
                    }
                }
            }
        }.trimEnd()
    }
}

object HttpExecutionEngine {

    fun executeCheck(
        rawLine: String,
        script: ParsedLoliScript,
        timeoutSec: Int,
        proxyItem: ProxyItem?
    ): HttpCheckResult {
        val debug = executeWithTrace(
            rawLine = rawLine,
            script = script,
            timeoutSec = timeoutSec,
            proxyItem = proxyItem,
            collectTrace = false
        )
        return HttpCheckResult(
            status = debug.statusLabel,
            outcomeType = debug.outcomeType,
            capturedData = buildString {
                if (debug.httpCode > 0) append("HTTP ${debug.httpCode} (${debug.latencyMs}ms)")
                val capStr = debug.captures.entries.joinToString(" | ") { "${it.key}=${it.value}" }
                if (capStr.isNotEmpty()) {
                    if (isNotEmpty()) append(" | ")
                    append(capStr)
                }
                if (isEmpty()) {
                    val lastTrace = debug.traceLogs.lastOrNull().orEmpty()
                    append(lastTrace.ifBlank { debug.statusLabel })
                }
            },
            latencyMs = debug.latencyMs,
            httpCode = debug.httpCode
        )
    }

    fun executeDebug(
        rawLine: String,
        scriptText: String,
        timeoutSec: Int,
        proxyItem: ProxyItem?
    ): ConfigDebugResult {
        val parsed = LoliScriptParser.parse(scriptText)
        return executeWithTrace(
            rawLine = rawLine,
            script = parsed,
            timeoutSec = timeoutSec,
            proxyItem = proxyItem,
            collectTrace = true
        )
    }

    private fun executeWithTrace(
        rawLine: String,
        script: ParsedLoliScript,
        timeoutSec: Int,
        proxyItem: ProxyItem?,
        collectTrace: Boolean
    ): ConfigDebugResult {
        val analysis = NativeScannerBridge.analyzeComboLine(rawLine)
        val variables = linkedMapOf<String, String>(
            "USER" to analysis.left,
            "PASS" to analysis.right,
            "INPUT" to rawLine.trim(),
            "SOURCE" to "",
            "RESPONSECODE" to "0",
            "ADDRESS" to ""
        )
        val captures = linkedMapOf<String, String>()
        val traces = mutableListOf<String>()
        val cookieJar = InMemoryCookieJar()

        fun logTrace(msg: String) {
            if (collectTrace) traces.add(msg)
        }

        val blocks = script.blocks
        val hasHttpRequest = blocks.any { it is ConfigBlock.RequestBlock && it.url.isNotBlank() }

        if (!hasHttpRequest) {
            // Execute any Function/Parse blocks locally, then run native structural check
            blocks.filterIsInstance<ConfigBlock.FunctionBlock>().forEach { fn ->
                val resolvedInput = substituteVars(fn.inputTemplate, variables, urlEncode = false)
                val computed = runScriptFunction(fn.functionType, resolvedInput)
                variables[fn.outputVar] = computed
                logTrace("[FUNCTION ${fn.functionType.loliKeyword}] ${fn.outputVar}=$computed")
            }
            val outcome = when {
                analysis.isEmailLeft && analysis.isValidPair -> "HIT"
                analysis.isValidPair -> "CUSTOM"
                else -> "TO_CHECK"
            }
            captures["FNV1a"] = analysis.fnv1aHashHex.take(8)
            logTrace("[LOCAL SCAN] Outcome=$outcome | PairValid=${analysis.isValidPair} | FNV1a=${analysis.fnv1aHashHex.take(8)}")
            return ConfigDebugResult(
                outcomeType = outcome,
                statusLabel = if (outcome == "HIT") "VALID_PAIR" else outcome,
                httpCode = 0,
                latencyMs = 0L,
                proxyUsed = "Local",
                captures = captures,
                variables = variables,
                traceLogs = traces,
                responseBodyPreview = rawLine
            )
        }

        val proxyLabel = if (proxyItem != null) "${proxyItem.host}:${proxyItem.port}" else "Direct"
        var totalLatency = 0L
        var lastHttpCode = 0
        var lastBody = ""
        var combinedSource = ""
        var finalOutcome: String? = null
        var matchedKeySummary: String? = null

        for ((idx, block) in blocks.withIndex()) {
            when (block) {
                is ConfigBlock.FunctionBlock -> {
                    val resolvedInput = substituteVars(block.inputTemplate, variables, urlEncode = false)
                    val out = runScriptFunction(block.functionType, resolvedInput)
                    variables[block.outputVar] = out
                    logTrace("#${idx + 1} [FUNCTION ${block.functionType.loliKeyword}] ${block.outputVar} = ${out.take(48)}")
                }

                is ConfigBlock.RequestBlock -> {
                    val resolvedUrl = substituteVars(block.url, variables, urlEncode = true)
                    val resolvedBody = substituteVars(block.postBody, variables, urlEncode = false)
                    val client = OpenBulletNetworkClient.buildClient(
                        timeoutSec = timeoutSec,
                        proxyItem = proxyItem,
                        followRedirects = block.followRedirects,
                        cookieJar = cookieJar
                    )
                    val reqBuilder = Request.Builder().url(resolvedUrl)
                    var explicitContentType: String? = null
                    block.headersText.lineSequence()
                        .map { it.trim() }
                        .filter { it.contains(":") }
                        .forEach { headerLine ->
                            val colonIdx = headerLine.indexOf(':')
                            val k = headerLine.substring(0, colonIdx).trim()
                            val v = substituteVars(headerLine.substring(colonIdx + 1).trim(), variables, urlEncode = false)
                            if (k.equals("Content-Type", ignoreCase = true)) {
                                explicitContentType = v
                            }
                            reqBuilder.header(k, v)
                        }

                    val mediaType = (explicitContentType ?: block.contentType.ifBlank { "application/x-www-form-urlencoded" }).toMediaType()
                    when (block.method.uppercase()) {
                        "POST" -> reqBuilder.post(resolvedBody.toRequestBody(mediaType))
                        "PUT" -> reqBuilder.put(resolvedBody.toRequestBody(mediaType))
                        "DELETE" -> reqBuilder.delete()
                        "HEAD" -> reqBuilder.head()
                        else -> reqBuilder.get()
                    }

                    val startMs = System.currentTimeMillis()
                    val callResult = runCatching {
                        client.newCall(reqBuilder.build()).execute().use { resp ->
                            val elapsed = System.currentTimeMillis() - startMs
                            val bodyStr = resp.body?.string().orEmpty()
                            Triple(resp.code, elapsed, bodyStr)
                        }
                    }

                    if (callResult.isFailure) {
                        val err = callResult.exceptionOrNull()
                        val elapsed = System.currentTimeMillis() - startMs
                        totalLatency += elapsed
                        logTrace("#${idx + 1} [REQUEST ${block.method}] FAILED: ${err?.javaClass?.simpleName}: ${err?.message}")
                        return ConfigDebugResult(
                            outcomeType = "RETRY",
                            statusLabel = "RETRY",
                            httpCode = 0,
                            latencyMs = totalLatency,
                            proxyUsed = proxyLabel,
                            captures = captures,
                            variables = variables,
                            traceLogs = traces,
                            responseBodyPreview = err?.message ?: "Network error"
                        )
                    }

                    val (code, elapsed, bodyStr) = callResult.getOrThrow()
                    totalLatency += elapsed
                    lastHttpCode = code
                    lastBody = bodyStr
                    combinedSource = "HTTP/$code\n$bodyStr"
                    variables["SOURCE"] = bodyStr
                    variables["RESPONSECODE"] = code.toString()
                    variables["ADDRESS"] = resolvedUrl
                    logTrace("#${idx + 1} [REQUEST ${block.method}] $resolvedUrl -> HTTP $code (${elapsed}ms, ${bodyStr.length} bytes)")
                }

                is ConfigBlock.ParseBlock -> {
                    val srcText = if (block.sourceVar.equals("<SOURCE>", ignoreCase = true) || block.sourceVar.isBlank()) {
                        lastBody
                    } else {
                        substituteVars(block.sourceVar, variables, urlEncode = false)
                    }
                    val extracted = when (block.mode) {
                        ParseMode.LR -> extractBetween(srcText, block.leftOrPattern, block.right)
                        ParseMode.REGEX -> extractRegex(srcText, block.leftOrPattern)
                        ParseMode.JSON -> extractJsonPath(srcText, block.leftOrPattern)
                    }
                    if (extracted != null) {
                        variables[block.outputName] = extracted
                        if (block.isCapture) captures[block.outputName] = extracted
                        logTrace("#${idx + 1} [PARSE ${block.mode.label}] ${block.outputName} = $extracted")
                    } else {
                        logTrace("#${idx + 1} [PARSE ${block.mode.label}] ${block.outputName} -> (no match)")
                    }
                }

                is ConfigBlock.KeyCheckBlock -> {
                    val matchedRetry = block.retryKeys.firstOrNull { key ->
                        key.isNotBlank() && combinedSource.contains(key, ignoreCase = true)
                    }
                    if (matchedRetry != null) {
                        finalOutcome = "RETRY"
                        matchedKeySummary = "RetryKey=$matchedRetry"
                        logTrace("#${idx + 1} [KEYCHECK] Matched RETRY key '$matchedRetry'")
                        break
                    }

                    val (scanOutcome, matchedKey) = NativeScannerBridge.scanResponseKeys(
                        body = combinedSource,
                        successKeys = block.successKeys,
                        customKeys = block.customKeys,
                        failureKeys = block.failureKeys
                    )
                    when (scanOutcome) {
                        KeyScanOutcome.FAIL -> {
                            finalOutcome = "BAD"
                            matchedKeySummary = matchedKey
                            logTrace("#${idx + 1} [KEYCHECK] Matched FAILURE key '$matchedKey'")
                            break
                        }
                        KeyScanOutcome.CUSTOM -> {
                            finalOutcome = "CUSTOM"
                            matchedKeySummary = matchedKey
                            logTrace("#${idx + 1} [KEYCHECK] Matched CUSTOM key '$matchedKey'")
                        }
                        KeyScanOutcome.HIT -> {
                            finalOutcome = "HIT"
                            matchedKeySummary = matchedKey
                            logTrace("#${idx + 1} [KEYCHECK] Matched SUCCESS key '$matchedKey'")
                        }
                        KeyScanOutcome.NONE -> {
                            logTrace("#${idx + 1} [KEYCHECK] No keys matched")
                        }
                    }
                }
            }
        }

        val resolvedOutcome = finalOutcome ?: when {
            lastHttpCode in 200..299 -> "HIT"
            lastHttpCode == 403 || lastHttpCode == 429 -> "RETRY"
            lastHttpCode == 401 -> "BAD"
            else -> "TO_CHECK"
        }

        if (matchedKeySummary != null) {
            captures.putIfAbsent("Key", matchedKeySummary)
        }

        return ConfigDebugResult(
            outcomeType = resolvedOutcome,
            statusLabel = "$resolvedOutcome ($lastHttpCode)",
            httpCode = lastHttpCode,
            latencyMs = totalLatency,
            proxyUsed = proxyLabel,
            captures = captures,
            variables = variables,
            traceLogs = traces,
            responseBodyPreview = lastBody.take(4000)
        )
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

    private fun substituteVars(
        template: String,
        variables: Map<String, String>,
        urlEncode: Boolean
    ): String {
        var result = template
        variables.forEach { (key, value) ->
            val token = "<$key>"
            if (result.contains(token, ignoreCase = false)) {
                val replacement = if (urlEncode) URLEncoder.encode(value, "UTF-8") else value
                result = result.replace(token, replacement)
            }
        }
        return result
    }

    private fun runScriptFunction(type: ScriptFunctionType, input: String): String {
        return when (type) {
            ScriptFunctionType.SHA256 -> MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            ScriptFunctionType.MD5 -> MessageDigest.getInstance("MD5")
                .digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            ScriptFunctionType.BASE64_ENCODE ->
                Base64.encodeToString(input.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            ScriptFunctionType.BASE64_DECODE ->
                runCatching { String(Base64.decode(input, Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault("")
            ScriptFunctionType.URL_ENCODE -> URLEncoder.encode(input, "UTF-8")
            ScriptFunctionType.URL_DECODE -> runCatching { URLDecoder.decode(input, "UTF-8") }.getOrDefault(input)
            ScriptFunctionType.UPPERCASE -> input.uppercase()
            ScriptFunctionType.LOWERCASE -> input.lowercase()
        }
    }

    private fun extractBetween(source: String, left: String, right: String): String? {
        if (left.isEmpty() || right.isEmpty()) return null
        val startIdx = source.indexOf(left)
        if (startIdx < 0) return null
        val valueStart = startIdx + left.length
        val endIdx = source.indexOf(right, valueStart)
        if (endIdx < 0) return null
        return source.substring(valueStart, endIdx).trim().take(120)
    }

    private fun extractRegex(source: String, pattern: String): String? {
        if (pattern.isEmpty()) return null
        return runCatching {
            val match = Regex(pattern).find(source) ?: return null
            val group = if (match.groupValues.size > 1) match.groupValues[1] else match.value
            group.trim().take(120)
        }.getOrNull()
    }

    private fun extractJsonPath(source: String, dotPath: String): String? {
        if (dotPath.isBlank()) return null
        return runCatching {
            var current: Any? = JSONObject(source)
            for (segment in dotPath.split(".")) {
                current = when (current) {
                    is JSONObject -> if (current.has(segment)) current.get(segment) else return null
                    is JSONArray -> {
                        val index = segment.toIntOrNull() ?: return null
                        if (index in 0 until current.length()) current.get(index) else return null
                    }
                    else -> return null
                }
            }
            if (current == null || current == JSONObject.NULL) null else current.toString().take(120)
        }.getOrNull()
    }
}
