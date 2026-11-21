package com.example.network

import com.example.model.ConfigBlock
import com.example.model.ConfigDebugResult
import com.example.model.ParseMode
import com.example.model.ProxyItem
import com.example.model.ProxyStatus
import com.example.model.ScriptFunctionType
import com.example.nativeengine.KeyScanOutcome
import com.example.nativeengine.NativeScannerBridge
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8

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
    private val requestRegex = Regex("""REQUEST\s+(GET|POST|PUT|PATCH|DELETE|HEAD)\s+"((?:\\.|[^"\\])+)"""", RegexOption.IGNORE_CASE)
    private val headerRegex = Regex("""HEADER\s+"((?:\\.|[^"\\])+)"""", RegexOption.IGNORE_CASE)
    private val contentTypeRegex = Regex("""CONTENT_TYPE\s+"((?:\\.|[^"\\])+)"""", RegexOption.IGNORE_CASE)
    private val contentRegex = Regex("""CONTENT\s+"((?:\\.|[^"\\])*)"""", RegexOption.IGNORE_CASE)
    private val redirectRegex = Regex("""REDIRECT\s+(true|false)""", RegexOption.IGNORE_CASE)
    private val keyRegex = Regex("""KEY\s+"((?:\\.|[^"\\])+)"""", RegexOption.IGNORE_CASE)
    private val parseLrRegex = Regex(
        """PARSE\s+"((?:\\.|[^"\\])*)"\s+LR\s+"((?:\\.|[^"\\])*)"\s+"((?:\\.|[^"\\])*)"\s*->\s*(CAP|VAR)\s+"((?:\\.|[^"\\])+)"""",
        RegexOption.IGNORE_CASE
    )
    private val parseSingleRegex = Regex(
        """PARSE\s+"((?:\\.|[^"\\])*)"\s+(REGEX|JSON)\s+"((?:\\.|[^"\\])*)"\s*->\s*(CAP|VAR)\s+"((?:\\.|[^"\\])+)"""",
        RegexOption.IGNORE_CASE
    )
    private val functionRegex = Regex(
        """FUNCTION\s+([A-Z0-9_]+)\s+"((?:\\.|[^"\\])*)"\s*->\s*VAR\s+"((?:\\.|[^"\\])+)"""",
        RegexOption.IGNORE_CASE
    )

    private fun unescape(raw: String): String {
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == '\\' && i + 1 < raw.length) {
                when (val next = raw[i + 1]) {
                    '\\', '"' -> {
                        out.append(next)
                        i += 2
                    }
                    else -> {
                        out.append(ch)
                        i++
                    }
                }
            } else {
                out.append(ch)
                i++
            }
        }
        return out.toString()
    }

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
                        sourceVar = unescape(match.groupValues[1]).ifBlank { "<SOURCE>" },
                        mode = ParseMode.LR,
                        leftOrPattern = unescape(match.groupValues[2]),
                        right = unescape(match.groupValues[3]),
                        isCapture = match.groupValues[4].equals("CAP", ignoreCase = true),
                        outputName = unescape(match.groupValues[5])
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
                        sourceVar = unescape(match.groupValues[1]).ifBlank { "<SOURCE>" },
                        mode = mode,
                        leftOrPattern = unescape(match.groupValues[3]),
                        right = "",
                        isCapture = match.groupValues[4].equals("CAP", ignoreCase = true),
                        outputName = unescape(match.groupValues[5])
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
                        outputVar = unescape(match.groupValues[3])
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
                        val upperMethod = block.method.uppercase()
                        if (upperMethod in setOf("POST", "PUT", "PATCH") ||
                            (upperMethod == "DELETE" && block.postBody.isNotBlank())
                        ) {
                            if (block.contentType.isNotBlank()) {
                                appendLine("CONTENT_TYPE \"${escape(block.contentType.trim())}\"")
                            }
                            appendLine("CONTENT \"${escape(block.postBody)}\"")
                        }
                        append("REDIRECT ${block.followRedirects}")
                    }

                    is ConfigBlock.FunctionBlock -> {
                        append(
                            "FUNCTION ${block.functionType.loliKeyword} \"${escape(block.inputTemplate)}\" -> VAR \"${escape(block.outputVar.trim())}\""
                        )
                    }

                    is ConfigBlock.ParseBlock -> {
                        val destKind = if (block.isCapture) "CAP" else "VAR"
                        when (block.mode) {
                            ParseMode.LR -> append(
                                "PARSE \"${escape(block.sourceVar)}\" LR \"${escape(block.leftOrPattern)}\" \"${escape(block.right)}\" -> $destKind \"${escape(block.outputName.trim())}\""
                            )
                            ParseMode.REGEX -> append(
                                "PARSE \"${escape(block.sourceVar)}\" REGEX \"${escape(block.leftOrPattern)}\" -> $destKind \"${escape(block.outputName.trim())}\""
                            )
                            ParseMode.JSON -> append(
                                "PARSE \"${escape(block.sourceVar)}\" JSON \"${escape(block.leftOrPattern)}\" -> $destKind \"${escape(block.outputName.trim())}\""
                            )
                        }
                    }

                    is ConfigBlock.KeyCheckBlock -> {
                        appendLine("KEYCHECK")
                        val validSuccess = block.successKeys.filter { it.isNotBlank() }
                        if (validSuccess.isNotEmpty()) {
                            appendLine("  KEYCHAIN Success OR")
                            validSuccess.forEach { k ->
                                appendLine("    KEY \"${escape(k)}\"")
                            }
                        }
                        val validCustom = block.customKeys.filter { it.isNotBlank() }
                        if (validCustom.isNotEmpty()) {
                            appendLine("  KEYCHAIN Custom OR")
                            validCustom.forEach { k ->
                                appendLine("    KEY \"${escape(k)}\"")
                            }
                        }
                        val validFailure = block.failureKeys.filter { it.isNotBlank() }
                        if (validFailure.isNotEmpty()) {
                            appendLine("  KEYCHAIN Failure OR")
                            validFailure.forEach { k ->
                                appendLine("    KEY \"${escape(k)}\"")
                            }
                        }
                        val validRetry = block.retryKeys.filter { it.isNotBlank() }
                        if (validRetry.isNotEmpty()) {
                            appendLine("  KEYCHAIN Retry OR")
                            validRetry.forEach { k ->
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

    private const val MAX_RESPONSE_BODY_BYTES = 256L * 1024L // 256 KB cap per response for high-concurrency stability

    private val lenientJsonAdapter: JsonAdapter<Any> by lazy {
        Moshi.Builder().build().adapter(Any::class.java).lenient()
    }

    private val varTokenRegex = Regex("""<([A-Za-z0-9_]+)>""")
    private val jsonPathSegmentRegex = Regex("""([^.\[\]]+)|\[(\d+)]""")

    private val regexCacheLock = Any()
    private val regexCache = object : LinkedHashMap<String, Regex>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Regex>?): Boolean =
            size > 64
    }

    private fun compiledRegex(pattern: String): Regex {
        synchronized(regexCacheLock) {
            regexCache[pattern]?.let { return it }
        }
        val compiled = Regex(pattern)
        synchronized(regexCacheLock) {
            regexCache[pattern] = compiled
        }
        return compiled
    }

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
        val trimmedInput = rawLine.trim()
        val analysis = NativeScannerBridge.analyzeComboLine(trimmedInput)
        val variables = linkedMapOf(
            "USER" to analysis.left,
            "PASS" to analysis.right,
            "INPUT" to trimmedInput,
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
            variables["SOURCE"] = trimmedInput
            var finalOutcome: String? = null
            var matchedKeySummary: String? = null

            for ((idx, block) in blocks.withIndex()) {
                when (block) {
                    is ConfigBlock.FunctionBlock -> {
                        executeFunctionBlock(idx, block, variables, ::logTrace)
                    }
                    is ConfigBlock.ParseBlock -> {
                        executeParseBlock(idx, block, trimmedInput, variables, captures, ::logTrace)
                    }
                    is ConfigBlock.KeyCheckBlock -> {
                        val (outcome, matched, shouldStop) = evaluateKeyCheckBlock(
                            idx = idx,
                            block = block,
                            combinedSource = trimmedInput,
                            logTrace = ::logTrace
                        )
                        if (outcome != null) {
                            finalOutcome = outcome
                            matchedKeySummary = matched
                        }
                        if (shouldStop) break
                    }
                    is ConfigBlock.RequestBlock -> Unit
                }
            }

            val resolvedOutcome = finalOutcome ?: when {
                analysis.isEmailLeft && analysis.isValidPair -> "HIT"
                analysis.isValidPair -> "CUSTOM"
                else -> "TO_CHECK"
            }
            captures.putIfAbsent("FNV1a", analysis.fnv1aHashHex.take(8))
            if (matchedKeySummary != null) {
                captures.putIfAbsent("Key", matchedKeySummary)
            }
            logTrace("[LOCAL SCAN] Outcome=$resolvedOutcome | PairValid=${analysis.isValidPair} | FNV1a=${analysis.fnv1aHashHex.take(8)}")
            return ConfigDebugResult(
                outcomeType = resolvedOutcome,
                statusLabel = if (resolvedOutcome == "HIT") "VALID_PAIR" else resolvedOutcome,
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
                    executeFunctionBlock(idx, block, variables, ::logTrace)
                }

                is ConfigBlock.RequestBlock -> {
                    val resolvedUrl = substituteUrlVars(block.url, variables)
                    val resolvedBody = substituteVars(block.postBody, variables, urlEncode = false)
                    val startMs = System.currentTimeMillis()
                    val callResult = runCatching {
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
                                val v = substituteVars(
                                    headerLine.substring(colonIdx + 1).trim(),
                                    variables,
                                    urlEncode = false
                                )
                                if (k.equals("Content-Type", ignoreCase = true)) {
                                    explicitContentType = v
                                }
                                if (k.isNotEmpty()) {
                                    reqBuilder.header(k, v)
                                }
                            }

                        val rawMediaType = explicitContentType
                            ?: block.contentType.ifBlank { "application/x-www-form-urlencoded" }
                        val mediaType = rawMediaType.toMediaType()
                        when (block.method.uppercase()) {
                            "POST" -> reqBuilder.post(resolvedBody.toRequestBody(mediaType))
                            "PUT" -> reqBuilder.put(resolvedBody.toRequestBody(mediaType))
                            "PATCH" -> reqBuilder.patch(resolvedBody.toRequestBody(mediaType))
                            "DELETE" -> if (resolvedBody.isNotEmpty()) {
                                reqBuilder.delete(resolvedBody.toRequestBody(mediaType))
                            } else {
                                reqBuilder.delete()
                            }
                            "HEAD" -> reqBuilder.head()
                            else -> reqBuilder.get()
                        }

                        client.newCall(reqBuilder.build()).execute().use { resp ->
                            val elapsed = System.currentTimeMillis() - startMs
                            val bodyStr = resp.body?.let { body ->
                                val source = body.source()
                                source.request(MAX_RESPONSE_BODY_BYTES)
                                val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                                source.buffer.clone().readString(
                                    minOf(source.buffer.size, MAX_RESPONSE_BODY_BYTES),
                                    charset
                                )
                            }.orEmpty()
                            Triple(resp.code, elapsed, bodyStr)
                        }
                    }

                    if (callResult.isFailure) {
                        val err = callResult.exceptionOrNull()
                        val elapsed = System.currentTimeMillis() - startMs
                        totalLatency += elapsed
                        val isConfigError = err is IllegalArgumentException
                        val failOutcome = if (isConfigError) "BAD" else "RETRY"
                        logTrace("#${idx + 1} [REQUEST ${block.method}] FAILED: ${err?.javaClass?.simpleName}: ${err?.message}")
                        return ConfigDebugResult(
                            outcomeType = failOutcome,
                            statusLabel = failOutcome,
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
                    executeParseBlock(idx, block, lastBody, variables, captures, ::logTrace)
                }

                is ConfigBlock.KeyCheckBlock -> {
                    val (outcome, matched, shouldStop) = evaluateKeyCheckBlock(
                        idx = idx,
                        block = block,
                        combinedSource = combinedSource,
                        logTrace = ::logTrace
                    )
                    if (outcome != null) {
                        finalOutcome = outcome
                        matchedKeySummary = matched
                    }
                    if (shouldStop) break
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

    private fun executeFunctionBlock(
        idx: Int,
        block: ConfigBlock.FunctionBlock,
        variables: MutableMap<String, String>,
        logTrace: (String) -> Unit
    ) {
        val resolvedInput = substituteVars(block.inputTemplate, variables, urlEncode = false)
        val out = runScriptFunction(block.functionType, resolvedInput)
        if (block.outputVar.isNotBlank()) {
            variables[block.outputVar] = out
        }
        logTrace("#${idx + 1} [FUNCTION ${block.functionType.loliKeyword}] ${block.outputVar} = ${out.take(48)}")
    }

    private fun executeParseBlock(
        idx: Int,
        block: ConfigBlock.ParseBlock,
        defaultSourceBody: String,
        variables: MutableMap<String, String>,
        captures: MutableMap<String, String>,
        logTrace: (String) -> Unit
    ) {
        val srcText = if (block.sourceVar.equals("<SOURCE>", ignoreCase = true) || block.sourceVar.isBlank()) {
            defaultSourceBody
        } else {
            substituteVars(block.sourceVar, variables, urlEncode = false)
        }
        val extracted = when (block.mode) {
            ParseMode.LR -> extractBetween(srcText, block.leftOrPattern, block.right)
            ParseMode.REGEX -> extractRegex(srcText, block.leftOrPattern)
            ParseMode.JSON -> extractJsonPath(srcText, block.leftOrPattern)
        }
        if (extracted != null) {
            if (block.outputName.isNotBlank()) {
                variables[block.outputName] = extracted
                if (block.isCapture) captures[block.outputName] = extracted
            }
            logTrace("#${idx + 1} [PARSE ${block.mode.label}] ${block.outputName} = $extracted")
        } else {
            logTrace("#${idx + 1} [PARSE ${block.mode.label}] ${block.outputName} -> (no match)")
        }
    }

    private fun evaluateKeyCheckBlock(
        idx: Int,
        block: ConfigBlock.KeyCheckBlock,
        combinedSource: String,
        logTrace: (String) -> Unit
    ): Triple<String?, String?, Boolean> {
        val matchedRetry = block.retryKeys.firstOrNull { key ->
            key.isNotBlank() && combinedSource.contains(key, ignoreCase = true)
        }
        if (matchedRetry != null) {
            logTrace("#${idx + 1} [KEYCHECK] Matched RETRY key '$matchedRetry'")
            return Triple("RETRY", "RetryKey=$matchedRetry", true)
        }

        val (scanOutcome, matchedKey) = NativeScannerBridge.scanResponseKeys(
            body = combinedSource,
            successKeys = block.successKeys,
            customKeys = block.customKeys,
            failureKeys = block.failureKeys
        )
        return when (scanOutcome) {
            KeyScanOutcome.FAIL -> {
                logTrace("#${idx + 1} [KEYCHECK] Matched FAILURE key '$matchedKey'")
                Triple("BAD", matchedKey, true)
            }
            KeyScanOutcome.CUSTOM -> {
                logTrace("#${idx + 1} [KEYCHECK] Matched CUSTOM key '$matchedKey'")
                Triple("CUSTOM", matchedKey, false)
            }
            KeyScanOutcome.HIT -> {
                logTrace("#${idx + 1} [KEYCHECK] Matched SUCCESS key '$matchedKey'")
                Triple("HIT", matchedKey, false)
            }
            KeyScanOutcome.NONE -> {
                logTrace("#${idx + 1} [KEYCHECK] No keys matched")
                Triple(null, null, false)
            }
        }
    }

    fun checkProxy(
        proxy: ProxyItem,
        timeoutSec: Int,
        probeUrl: String = "https://httpbin.org/ip"
    ): Triple<ProxyStatus, Int?, String> {
        val start = System.currentTimeMillis()
        return runCatching {
            val client = OpenBulletNetworkClient.buildClient(timeoutSec, proxy)
            val req = Request.Builder().url(probeUrl).get().build()
            client.newCall(req).execute().use { resp ->
                val ping = (System.currentTimeMillis() - start).toInt()
                when {
                    resp.isSuccessful -> Triple(ProxyStatus.AVAILABLE, ping, "Verified")
                    resp.code == 403 || resp.code == 429 -> Triple(ProxyStatus.BANNED, ping, "HTTP ${resp.code}")
                    else -> Triple(ProxyStatus.BAD, ping, "HTTP ${resp.code}")
                }
            }
        }.getOrElse { err ->
            val reason = if (err is IllegalArgumentException) "Invalid URL/Proxy" else "Unreachable"
            Triple(ProxyStatus.BAD, null, reason)
        }
    }

    private fun substituteUrlVars(
        template: String,
        variables: Map<String, String>
    ): String {
        val queryIdx = template.indexOf('?')
        if (queryIdx < 0) {
            return substituteVars(template, variables, urlEncode = false)
        }
        val prefix = template.substring(0, queryIdx)
        val queryAndFragment = template.substring(queryIdx)
        return substituteVars(prefix, variables, urlEncode = false) +
            substituteVars(queryAndFragment, variables, urlEncode = true)
    }

    private fun substituteVars(
        template: String,
        variables: Map<String, String>,
        urlEncode: Boolean
    ): String {
        if (!template.contains('<')) return template
        return varTokenRegex.replace(template) { match ->
            val key = match.groupValues[1]
            val value = variables[key] ?: return@replace match.value
            if (urlEncode) URLEncoder.encode(value, "UTF-8") else value
        }
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
                input.encodeUtf8().base64()
            ScriptFunctionType.BASE64_DECODE ->
                runCatching { input.trim().decodeBase64()?.utf8().orEmpty() }.getOrDefault("")
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
            val match = compiledRegex(pattern).find(source) ?: return null
            val group = if (match.groupValues.size > 1) match.groupValues[1] else match.value
            group.trim().take(120)
        }.getOrNull()
    }

    private fun extractJsonPath(source: String, dotPath: String): String? {
        val trimmedSource = source.trim()
        if (dotPath.isBlank() || trimmedSource.isEmpty()) return null
        return runCatching {
            var current: Any? = lenientJsonAdapter.fromJson(trimmedSource) ?: return null
            val segments = jsonPathSegmentRegex.findAll(dotPath)
                .map { it.groupValues[1].ifEmpty { it.groupValues[2] } }
                .toList()
            if (segments.isEmpty()) return null

            for (segment in segments) {
                current = when (current) {
                    is Map<*, *> -> {
                        if (!current.containsKey(segment)) return null
                        current[segment]
                    }
                    is List<*> -> {
                        val index = segment.toIntOrNull() ?: return null
                        if (index in current.indices) current[index] else return null
                    }
                    else -> return null
                }
            }
            formatJsonScalar(current)?.take(120)
        }.getOrNull()
    }

    private fun formatJsonScalar(value: Any?): String? {
        return when (value) {
            null -> null
            is Double -> {
                if (value.isFinite() && value == value.toLong().toDouble()) {
                    value.toLong().toString()
                } else {
                    value.toString()
                }
            }
            else -> value.toString()
        }
    }
}
