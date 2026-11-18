package com.example

import com.example.nativeengine.KeyScanOutcome
import com.example.nativeengine.NativeScannerBridge
import com.example.network.LoliScriptParser
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
    }

    @Test
    fun `nativeScannerBridge analyzes combo pairs and hashes deterministically`() {
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

        val loliText = com.example.network.ConfigFileCodec.serializeToLoli(sample)
        assertTrue(loliText.contains("[SETTINGS]"))
        assertTrue(loliText.contains("[SCRIPT]"))
        assertEquals("OpenBullet .loli", com.example.network.ConfigFileCodec.detectFormatLabel(loliText))

        val parsedFromLoli = com.example.network.ConfigFileCodec.parseImportPayload(loliText)
        assertEquals(1, parsedFromLoli.size)
        assertEquals("GitHub API Check", parsedFromLoli.first().name)
        assertEquals("DevTester", parsedFromLoli.first().author)
        assertEquals("API", parsedFromLoli.first().category)
        assertEquals("Mail:Pass", parsedFromLoli.first().wordlistType)
        assertTrue(parsedFromLoli.first().scriptPreview.contains("https://httpbin.org/get?user=<USER>"))

        val jsonBundle = com.example.network.ConfigFileCodec.serializeListToJson(listOf(sample))
        val parsedFromJson = com.example.network.ConfigFileCodec.parseImportPayload(jsonBundle)
        assertEquals(1, parsedFromJson.size)
        assertEquals("GitHub API Check", parsedFromJson.first().name)
    }
}
