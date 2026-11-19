package com.example

import com.example.nativeengine.KeyScanOutcome
import com.example.nativeengine.NativeScannerBridge
import com.example.network.ConfigExportFormat
import com.example.network.ConfigFileCodec
import com.example.network.HttpExecutionEngine
import com.example.network.LoliScriptParser
import com.example.viewmodel.RunnerPacketHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreEngineUnitTest {

    @Test
    fun `loliScriptParser parses request headers keychains and parse rules`() {
        val script = """
            # Sample HTTP check
            REQUEST GET "https://httpbin.org/get?user=<USER>"
            HEADER "Accept: application/json"
            KEYCHECK
              KEYCHAIN Success OR
                KEY "HTTP/200"
              KEYCHAIN Failure OR
                KEY "Unauthorized"
            PARSE "<SOURCE>" LR "\"origin\": \"" "\"" -> CAP "Origin"
        """.trimIndent()

        val parsed = LoliScriptParser.parse(script)
        assertEquals("GET", parsed.method)
        assertEquals("https://httpbin.org/get?user=<USER>", parsed.targetUrl)
        assertEquals("application/json", parsed.headers["Accept"])
        assertEquals(listOf("HTTP/200"), parsed.successKeys)
        assertEquals(listOf("Unauthorized"), parsed.failureKeys)
        assertEquals(1, parsed.parseRules.size)
        assertEquals("Origin", parsed.parseRules.first().captureName)
    }

    @Test
    fun `nativeScannerBridge classifies response keys with failure precedence`() {
        val (hitOutcome, hitKey) = NativeScannerBridge.scanResponseKeys(
            body = "HTTP/200 {\"status\":\"ok\"}",
            successKeys = listOf("HTTP/200"),
            customKeys = listOf("2fa"),
            failureKeys = listOf("Unauthorized")
        )
        assertEquals(KeyScanOutcome.HIT, hitOutcome)
        assertEquals("HTTP/200", hitKey)

        val (failOutcome, failKey) = NativeScannerBridge.scanResponseKeys(
            body = "HTTP/200 Unauthorized",
            successKeys = listOf("HTTP/200"),
            customKeys = emptyList(),
            failureKeys = listOf("Unauthorized")
        )
        assertEquals(KeyScanOutcome.FAIL, failOutcome)
        assertEquals("Unauthorized", failKey)

        val (emptyOutcome, emptyMatched) = NativeScannerBridge.scanResponseKeys(
            body = "some response text",
            successKeys = listOf("", "   "),
            customKeys = emptyList(),
            failureKeys = listOf("")
        )
        assertEquals(KeyScanOutcome.NONE, emptyOutcome)
        assertEquals(null, emptyMatched)
    }

    @Test
    fun `nativeScannerBridge analyzes combo pairs and hashes utf8 deterministically`() {
        val valid = NativeScannerBridge.analyzeComboLine("alice@example.com:secret123")
        assertTrue(valid.isEmailLeft)
        assertTrue(valid.isValidPair)
        assertEquals("alice@example.com", valid.left)
        assertEquals("secret123", valid.right)
        assertEquals(16, valid.fnv1aHashHex.length)

        val invalid = NativeScannerBridge.analyzeComboLine("single_token_only")
        assertFalse(invalid.isValidPair)
        assertEquals(
            NativeScannerBridge.fastHashLine("alice@example.com:secret123"),
            NativeScannerBridge.fastHashLine("alice@example.com:secret123")
        )
        // UTF-8 multi-byte stability check
        assertEquals(
            NativeScannerBridge.fastHashLine("ব্যবহারকারী:পাসওয়ার্ড"),
            NativeScannerBridge.fastHashLine("ব্যবহারকারী:পাসওয়ার্ড")
        )
    }

    @Test
    fun `loliScriptParser roundtrips multi-block Stacker script with Function JSON Regex and Retry`() {
        val raw = """
            FUNCTION BASE64ENCODE "<USER>:<PASS>" -> VAR "AUTH_TOKEN"
            REQUEST POST "https://httpbin.org/post"
            HEADER "Authorization: Basic <AUTH_TOKEN>"
            CONTENT_TYPE "application/json"
            CONTENT "{\"user\":\"<USER>\"}"
            REDIRECT false
            PARSE "<SOURCE>" JSON "json.user" -> CAP "Username"
            PARSE "<SOURCE>" REGEX "origin\":\s*\"([^\"]+)\"" -> VAR "IP"
            KEYCHECK
              KEYCHAIN Success OR
                KEY "HTTP/200"
              KEYCHAIN Failure OR
                KEY "Unauthorized"
              KEYCHAIN Retry OR
                KEY "HTTP/429"
        """.trimIndent()

        val blocks = LoliScriptParser.parseToBlocks(raw)
        assertEquals(5, blocks.size)
        val serialized = LoliScriptParser.serializeBlocks(blocks)
        val reparsed = LoliScriptParser.parse(serialized)
        assertEquals("POST", reparsed.method)
        assertEquals("https://httpbin.org/post", reparsed.targetUrl)
        assertEquals("Basic <AUTH_TOKEN>", reparsed.headers["Authorization"])
        assertEquals(listOf("HTTP/429"), reparsed.retryKeys)
        assertEquals(2, reparsed.parseRules.size)
        assertEquals("Username", reparsed.parseRules[0].captureName)
        assertTrue(reparsed.parseRules[0].isCapture)
        assertEquals("IP", reparsed.parseRules[1].captureName)
        assertFalse(reparsed.parseRules[1].isCapture)
    }

    @Test
    fun `loliScriptParser supports PATCH method roundtrip`() {
        val raw = """
            REQUEST PATCH "https://api.example.com/profile"
            CONTENT_TYPE "application/json"
            CONTENT "{\"name\":\"<USER>\"}"
            REDIRECT true
        """.trimIndent()
        val parsed = LoliScriptParser.parse(raw)
        assertEquals("PATCH", parsed.method)
        assertEquals("https://api.example.com/profile", parsed.targetUrl)
        val reserialized = LoliScriptParser.serializeBlocks(parsed.blocks)
        assertTrue(reserialized.contains("REQUEST PATCH"))
    }

    @Test
    fun `configFileCodec roundtrips OpenBullet loli and JSON formats`() {
        val sample = com.example.model.ConfigItem(
            id = "cfg-1",
            name = "GitHub API Check",
            author = "DevTester",
            category = "API",
            wordlistType = "Mail:Pass",
            needsProxies = true,
            lastModified = "2026-11-18",
            scriptPreview = """
                REQUEST GET "https://httpbin.org/get?user=<USER>"
                HEADER "Accept: application/json"
                KEYCHECK
                  KEYCHAIN Success OR
                    KEY "HTTP/200"
            """.trimIndent()
        )

        val loliText = ConfigFileCodec.serializeToLoli(sample)
        assertTrue(loliText.contains("[SETTINGS]"))
        assertTrue(loliText.contains("[SCRIPT]"))
        assertEquals("OpenBullet .loli", ConfigFileCodec.detectFormatLabel(loliText))

        val parsedFromLoli = ConfigFileCodec.parseImportPayload(loliText)
        assertEquals(1, parsedFromLoli.size)
        assertEquals("GitHub API Check", parsedFromLoli.first().name)
        assertEquals("DevTester", parsedFromLoli.first().author)
        assertEquals("API", parsedFromLoli.first().category)
        assertEquals("Mail:Pass", parsedFromLoli.first().wordlistType)
        assertTrue(parsedFromLoli.first().scriptPreview.contains("https://httpbin.org/get?user=<USER>"))

        val jsonBundle = ConfigFileCodec.serializeListToJson(listOf(sample))
        val parsedFromJson = ConfigFileCodec.parseImportPayload(jsonBundle)
        assertEquals(1, parsedFromJson.size)
        assertEquals("GitHub API Check", parsedFromJson.first().name)
    }

    @Test
    fun `httpExecutionEngine runs offline pipeline with functions parse and keycheck cleanly`() {
        val script = """
            FUNCTION BASE64ENCODE "<USER>:<PASS>" -> VAR "B64"
            FUNCTION BASE64DECODE "<B64>" -> VAR "ROUNDTRIP"
            FUNCTION SHA256 "<USER>" -> VAR "HASH"
            FUNCTION MD5 "<USER>" -> VAR "MD5HASH"
            FUNCTION UPPERCASE "<USER>" -> VAR "UPPER"
            PARSE "{\"items\":[{\"code\":200,\"role\":\"admin\"}]}" JSON "items[0].code" -> CAP "Code"
            PARSE "{\"items\":[{\"code\":200,\"role\":\"admin\"}]}" JSON "items[0].role" -> CAP "Role"
            PARSE "<SOURCE>" LR "alice@" ":" -> CAP "Domain"
            KEYCHECK
              KEYCHAIN Success OR
                KEY "@example.com"
        """.trimIndent()

        val result = HttpExecutionEngine.executeDebug(
            rawLine = "alice@example.com:secret123",
            scriptText = script,
            timeoutSec = 5,
            proxyItem = null
        )
        assertEquals("HIT", result.outcomeType)
        assertEquals("YWxpY2VAZXhhbXBsZS5jb206c2VjcmV0MTIz", result.variables["B64"])
        assertEquals("alice@example.com:secret123", result.variables["ROUNDTRIP"])
        assertEquals("ALICE@EXAMPLE.COM", result.variables["UPPER"])
        assertEquals(64, result.variables["HASH"]?.length)
        assertEquals(32, result.variables["MD5HASH"]?.length)
        assertEquals("200", result.captures["Code"])
        assertEquals("admin", result.captures["Role"])
        assertEquals("example.com", result.captures["Domain"])
    }

    @Test
    fun `httpExecutionEngine handles malformed request url gracefully without crashing`() {
        val script = """
            REQUEST GET "not_a_valid_url"
            KEYCHECK
              KEYCHAIN Success OR
                KEY "HTTP/200"
        """.trimIndent()

        val result = HttpExecutionEngine.executeDebug(
            rawLine = "alice@example.com:secret123",
            scriptText = script,
            timeoutSec = 3,
            proxyItem = null
        )
        assertEquals("BAD", result.outcomeType)
        assertTrue(result.traceLogs.any { it.contains("FAILED") })
    }

    @Test
    fun `runnerPacketHelper packWithByteBudget enforces strict byte limits and roundtrips`() {
        val items = (1..100).map { i ->
            "$i\u001Fuser$i@example.com:pass$i\u001F127.0.0.1:8080\u001FRUNNING\u001FRUNNING"
        }
        val packed = RunnerPacketHelper.packWithByteBudget(items, maxBytes = 600)
        assertTrue(packed.toByteArray(Charsets.UTF_8).size <= 600)
        val unpacked = RunnerPacketHelper.unpackWorkers(packed)
        assertTrue(unpacked.isNotEmpty())
        assertEquals(1, unpacked.first().id)
        assertEquals("user1@example.com:pass1", unpacked.first().data)
    }
}
