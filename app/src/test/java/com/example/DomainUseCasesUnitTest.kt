package com.example

import com.example.di.DispatcherProvider
import com.example.domain.usecase.CleanWordlistUseCase
import com.example.domain.usecase.CryptoAndRegexUseCase
import com.example.domain.usecase.DebugConfigScriptUseCase
import com.example.domain.usecase.ExportFormatterUseCase
import com.example.domain.usecase.FormatCombosUseCase
import com.example.domain.usecase.ParseProxiesUseCase
import com.example.domain.usecase.RunNativeDiagnosticUseCase
import com.example.model.HitDbItem
import com.example.model.ProxyItem
import com.example.model.ProxyProtocol
import com.example.model.ProxyStatus
import com.example.model.ResultTab
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DomainUseCasesUnitTest {

    private class TestDispatcherProvider(
        private val dispatcher: CoroutineDispatcher
    ) : DispatcherProvider {
        override val io: CoroutineDispatcher get() = dispatcher
        override val default: CoroutineDispatcher get() = dispatcher
        override val main: CoroutineDispatcher get() = dispatcher
    }

    @Test
    fun `cleanWordlistUseCase trims removes empty and deduplicates`() {
        val useCase = CleanWordlistUseCase()
        val res = useCase(
            rawInput = "  a@b.com:1  \n\n  a@b.com:1\n  c@d.com:2 ",
            trimWhitespace = true,
            removeEmpty = true,
            removeDuplicates = true
        )
        assertEquals("a@b.com:1\nc@d.com:2", res.output)
        assertEquals(4, res.inputCount)
        assertEquals(2, res.outputCount)
        assertEquals(2, res.removedCount)
    }

    @Test
    fun `formatCombosUseCase reformats separator and swaps sides`() {
        val useCase = FormatCombosUseCase()
        val res = useCase(
            rawInput = "user1;pass1\nuser2|pass2\nmalformed_line",
            targetSeparator = ":",
            swapOrder = true
        )
        assertEquals("pass1:user1\npass2:user2\nmalformed_line", res.output)
        assertEquals(2, res.formattedCount)
        assertEquals(1, res.skippedCount)
    }

    @Test
    fun `parseProxiesUseCase handles schemes auth ipv6 and deduplication`() {
        val useCase = ParseProxiesUseCase()
        val existing = listOf(
            ProxyItem("1", "1.2.3.4", 8080, ProxyProtocol.HTTP, null, "Pending", ProxyStatus.UNTESTED)
        )
        val raw = """
            # comment line
            1.2.3.4:8080
            socks5://user:pass@10.0.0.1:1080
            [2001:db8::1]:9050
            192.168.1.5:3128:authUser:authPass
            bad_host:99999
            missing_port
        """.trimIndent()

        val result = useCase(rawText = raw, defaultProtocol = ProxyProtocol.HTTP, existingProxies = existing)
        assertEquals(3, result.newProxies.size)
        assertEquals(1, result.duplicateCount)
        assertEquals(2, result.invalidCount)
        assertEquals(ProxyProtocol.SOCKS5, result.newProxies[0].protocol)
        assertEquals("10.0.0.1", result.newProxies[0].host)
        assertEquals(1080, result.newProxies[0].port)
        assertEquals("2001:db8::1", result.newProxies[1].host)
        assertEquals(9050, result.newProxies[1].port)
        assertEquals("192.168.1.5", result.newProxies[2].host)
        assertEquals(3128, result.newProxies[2].port)
    }

    @Test
    fun `cryptoAndRegexUseCase computes hashes and handles invalid regex safely`() {
        val useCase = CryptoAndRegexUseCase()
        val crypto = useCase.computeCrypto("hello world")
        assertEquals("aGVsbG8gd29ybGQ=", crypto.base64)
        assertEquals("5eb63bbbe01eeed093cb22bb8f5acdc3", crypto.md5)
        assertEquals(64, crypto.sha256.length)
        assertEquals("hello+world", crypto.urlEncoded)

        val validRegex = useCase.extractRegex("""[a-z]+@\w+\.\w+""", "contact a@b.com and c@d.org")
        assertNull(validRegex.error)
        assertEquals(listOf("a@b.com", "c@d.org"), validRegex.matches)

        val invalidRegex = useCase.extractRegex("[unclosed", "test")
        assertNotNull(invalidRegex.error)
        assertTrue(invalidRegex.matches.isEmpty())
    }

    @Test
    fun `exportFormatterUseCase filters hits across data config wordlist and captures`() {
        val useCase = ExportFormatterUseCase()
        val hits = listOf(
            HitDbItem("1", "12:00:00", "alice@x.com:123", ResultTab.HITS, "GitHub", "listA.txt", "Plan=Pro"),
            HitDbItem("2", "12:01:00", "bob@y.com:456", ResultTab.CUSTOM, "Spotify", "listB.txt", "Plan=Free")
        )
        val filtered = useCase.filterHits(hits, ResultTab.HITS, "Pro")
        assertEquals(1, filtered.size)
        assertEquals("1", filtered.first().id)

        val exportText = useCase.formatFilteredHits(hits, null, "listB")
        assertTrue(exportText.contains("bob@y.com:456"))
        assertTrue(!exportText.contains("alice@x.com"))
    }

    @Test
    fun `debugConfigScriptUseCase and runNativeDiagnosticUseCase execute offline deterministically`() = runTest {
        val dispatcherProvider = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
        val debugUseCase = DebugConfigScriptUseCase(dispatcherProvider)
        val nativeUseCase = RunNativeDiagnosticUseCase(dispatcherProvider)

        val debugResult = debugUseCase(
            rawTestInput = "alice@example.com:pass123",
            scriptText = """
                FUNCTION UPPERCASE "<USER>" -> VAR "UPPER"
                KEYCHECK
                  KEYCHAIN Success OR
                    KEY "@example.com"
            """.trimIndent(),
            timeoutSec = 5,
            useProxy = false,
            availableProxies = emptyList()
        )
        assertEquals("HIT", debugResult.outcomeType)
        assertEquals("ALICE@EXAMPLE.COM", debugResult.variables["UPPER"])

        val nativeSummary = nativeUseCase(
            sampleBody = "alice@example.com:pass123\nHTTP/200 {\"ok\":true}",
            successKey = "HTTP/200",
            failureKey = "Unauthorized"
        )
        assertTrue(nativeSummary.contains("Outcome=HIT"))
        assertTrue(nativeSummary.contains("Key=HTTP/200"))
    }
}
