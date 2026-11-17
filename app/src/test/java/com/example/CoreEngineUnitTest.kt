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
}
