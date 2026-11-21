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
            """.trimIndent(),
            version = "2.4",
            description = "Multi-part GitHub validator with custom DoP",
            suggestedBots = 16,
            maxRetries = 5,
            timeoutSec = 15,
            customUserAgent = "OpenBullet-Workstation/2.4",
            maxProxyUses = 12
        )

        val loliText = ConfigFileCodec.serializeToLoli(sample)
        assertTrue(loliText.contains("[SETTINGS]"))
        assertTrue(loliText.contains("[SCRIPT]"))
        assertEquals("OpenBullet .loli", ConfigFileCodec.detectFormatLabel(loliText))

        val parsedFromLoli = ConfigFileCodec.parseImportPayload(loliText)
        assertEquals(1, parsedFromLoli.size)
        val loliCfg = parsedFromLoli.first()
        assertEquals("GitHub API Check", loliCfg.name)
        assertEquals("DevTester", loliCfg.author)
        assertEquals("API", loliCfg.category)
        assertEquals("Mail:Pass", loliCfg.wordlistType)
        assertEquals("2.4", loliCfg.version)
        assertEquals("Multi-part GitHub validator with custom DoP", loliCfg.description)
        assertEquals(16, loliCfg.suggestedBots)
        assertEquals(5, loliCfg.maxRetries)
        assertEquals(15, loliCfg.timeoutSec)
        assertEquals("OpenBullet-Workstation/2.4", loliCfg.customUserAgent)
        assertEquals(12, loliCfg.maxProxyUses)
        assertEquals("2.4", loliCfg.metadata.version)
        assertEquals(16, loliCfg.executionSettings.suggestedBots)
        assertTrue(loliCfg.scriptPreview.contains("https://httpbin.org/get?user=<USER>"))

        val jsonBundle = ConfigFileCodec.serializeListToJson(listOf(sample))
        val parsedFromJson = ConfigFileCodec.parseImportPayload(jsonBundle)
        assertEquals(1, parsedFromJson.size)
        val jsonCfg = parsedFromJson.first()
        assertEquals("GitHub API Check", jsonCfg.name)
        assertEquals("2.4", jsonCfg.version)
        assertEquals("Multi-part GitHub validator with custom DoP", jsonCfg.description)
        assertEquals(16, jsonCfg.suggestedBots)
        assertEquals(5, jsonCfg.maxRetries)
        assertEquals(15, jsonCfg.timeoutSec)
        assertEquals("OpenBullet-Workstation/2.4", jsonCfg.customUserAgent)
        assertEquals(12, jsonCfg.maxProxyUses)
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

    @Test
    fun `dynamicParallelTaskEngine scales DoP dynamically and applies AIMD backpressure`() = kotlinx.coroutines.runBlocking {
        val items = (1..48).toList()
        val processedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val engine = com.example.network.DynamicParallelTaskEngine<Int>(
            initialDoP = 2,
            maxDoP = 12,
            adaptiveScalingEnabled = true
        )

        engine.executeAll(items = items) { _, _, item ->
            val current = processedCount.incrementAndGet()
            if (current == 6) {
                engine.setTargetDoP(8)
            } else if (current == 24) {
                engine.setTargetDoP(3)
            }
            engine.recordTaskOutcome(latencyMs = 12L, isCongestedOrRateLimited = (item % 15 == 0))
            kotlinx.coroutines.delay(4L)
        }

        assertEquals(48, processedCount.get())
        assertEquals(3, engine.currentTargetDoP)
        assertTrue(engine.currentEffectiveDoP in 1..3)
    }

    @Test
    fun `rawTcpProxyTunnel tunnels HTTP CONNECT SOCKS4a and SOCKS5 raw TCP streams`() {
        // 1. Test HTTP CONNECT tunnel with Basic Auth
        java.net.ServerSocket(0).use { server ->
            val port = server.localPort
            val serverThread = Thread {
                server.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                    val requestLines = mutableListOf<String>()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                        requestLines.add(line)
                    }
                    val hasConnect = requestLines.firstOrNull()?.startsWith("CONNECT target.local:443 HTTP/1.1") == true
                    val hasAuth = requestLines.any { it.startsWith("Proxy-Authorization: Basic ") }
                    val out = client.getOutputStream()
                    if (hasConnect && hasAuth) {
                        out.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                        out.flush()
                        val payload = ByteArray(4)
                        client.getInputStream().read(payload)
                        out.write("PONG".toByteArray(Charsets.UTF_8))
                        out.flush()
                    } else {
                        out.write("HTTP/1.1 407 Proxy Authentication Required\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                        out.flush()
                    }
                }
            }
            serverThread.start()

            val proxy = com.example.model.ProxyItem(
                id = "p-http",
                host = "127.0.0.1",
                port = port,
                protocol = com.example.model.ProxyProtocol.HTTP,
                pingMs = null,
                country = "Local",
                status = com.example.model.ProxyStatus.AVAILABLE,
                username = "alice",
                password = "secret"
            )
            com.example.network.RawTcpProxyTunnel.openConnection(
                targetHost = "target.local",
                targetPort = 443,
                proxy = proxy,
                timeoutMs = 3000
            ).use { sock ->
                sock.getOutputStream().write("PING".toByteArray(Charsets.UTF_8))
                sock.getOutputStream().flush()
                val reply = sock.getInputStream().readBytes().toString(Charsets.UTF_8)
                assertEquals("PONG", reply)
            }
            serverThread.join(2000)
        }

        // 2. Test SOCKS4a tunnel
        java.net.ServerSocket(0).use { server ->
            val port = server.localPort
            val serverThread = Thread {
                server.accept().use { client ->
                    val input = client.getInputStream()
                    val header = ByteArray(8)
                    input.read(header)
                    while (input.read() > 0) { /* userId */ }
                    while (input.read() > 0) { /* host */ }
                    val out = client.getOutputStream()
                    out.write(byteArrayOf(0x00, 0x5A, 0x00, 0x50, 127, 0, 0, 1))
                    out.flush()
                    out.write("SOCKS4A_OK".toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            }
            serverThread.start()

            val proxy = com.example.model.ProxyItem(
                id = "p-s4",
                host = "127.0.0.1",
                port = port,
                protocol = com.example.model.ProxyProtocol.SOCKS4,
                pingMs = null,
                country = "Local",
                status = com.example.model.ProxyStatus.AVAILABLE,
                username = "op"
            )
            com.example.network.RawTcpProxyTunnel.openConnection(
                targetHost = "internal.service.local",
                targetPort = 80,
                proxy = proxy,
                timeoutMs = 3000
            ).use { sock ->
                val reply = sock.getInputStream().readBytes().toString(Charsets.UTF_8)
                assertEquals("SOCKS4A_OK", reply)
            }
            serverThread.join(2000)
        }

        // 3. Test SOCKS5 tunnel with RFC 1929 Username/Password auth
        java.net.ServerSocket(0).use { server ->
            val port = server.localPort
            val serverThread = Thread {
                server.accept().use { client ->
                    val input = client.getInputStream()
                    val out = client.getOutputStream()
                    val greeting = ByteArray(4)
                    input.read(greeting)
                    out.write(byteArrayOf(0x05, 0x02))
                    out.flush()

                    val authVer = input.read()
                    val uLen = input.read()
                    val uBytes = ByteArray(uLen)
                    input.read(uBytes)
                    val pLen = input.read()
                    val pBytes = ByteArray(pLen)
                    input.read(pBytes)
                    val authOk = authVer == 1 &&
                        String(uBytes, Charsets.UTF_8) == "socksUser" &&
                        String(pBytes, Charsets.UTF_8) == "socksPass"
                    out.write(byteArrayOf(0x01, if (authOk) 0x00 else 0x01))
                    out.flush()

                    val reqHead = ByteArray(4)
                    input.read(reqHead)
                    val domainLen = input.read()
                    val domainBytes = ByteArray(domainLen + 2)
                    input.read(domainBytes)

                    out.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, 0x00, 0x50))
                    out.flush()
                    out.write("SOCKS5_OK".toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            }
            serverThread.start()

            val proxy = com.example.model.ProxyItem(
                id = "p-s5",
                host = "127.0.0.1",
                port = port,
                protocol = com.example.model.ProxyProtocol.SOCKS5,
                pingMs = null,
                country = "Local",
                status = com.example.model.ProxyStatus.AVAILABLE,
                username = "socksUser",
                password = "socksPass"
            )
            com.example.network.RawTcpProxyTunnel.openConnection(
                targetHost = "api.target.local",
                targetPort = 8080,
                proxy = proxy,
                timeoutMs = 3000
            ).use { sock ->
                val reply = sock.getInputStream().readBytes().toString(Charsets.UTF_8)
                assertEquals("SOCKS5_OK", reply)
            }
            serverThread.join(2000)
        }
    }

    @Test
    fun `httpExecutionEngine executes raw TCP request protocol over local socket`() {
        java.net.ServerSocket(0).use { server ->
            val port = server.localPort
            val serverThread = Thread {
                server.accept().use { client ->
                    val buf = ByteArray(128)
                    val n = client.getInputStream().read(buf)
                    val received = if (n > 0) String(buf, 0, n, Charsets.UTF_8) else ""
                    val out = client.getOutputStream()
                    out.write("220-TCP-BANNER [user=$received]".toByteArray(Charsets.UTF_8))
                    out.flush()
                }
            }
            serverThread.start()

            val script = """
                REQUEST TCP "tcp://127.0.0.1:$port"
                CONTENT "HELO <USER>"
                PARSE "<SOURCE>" LR "[user=HELO " "]" -> CAP "EchoUser"
                KEYCHECK
                  KEYCHAIN Success OR
                    KEY "220-TCP-BANNER"
            """.trimIndent()

            val debugRes = HttpExecutionEngine.executeDebug(
                rawLine = "tcp_operator:pass99",
                scriptText = script,
                timeoutSec = 3,
                proxyItem = null
            )
            assertEquals("Trace: ${debugRes.traceLogs}", "HIT", debugRes.outcomeType)
            assertEquals("Body: ${debugRes.responseBodyPreview} | Trace: ${debugRes.traceLogs}", "tcp_operator", debugRes.captures["EchoUser"])
            serverThread.join(2000)
        }
    }

    @Test
    fun `jobOrchestrator coordinates wordlist loop proxy rotation dynamic DoP and block pipeline`() = kotlinx.coroutines.runBlocking {
        val config = com.example.model.ConfigItem(
            id = "cfg-orch",
            name = "Orchestrator Test Config",
            author = "Arch",
            category = "Test",
            wordlistType = "Mail:Pass",
            needsProxies = true,
            lastModified = "2026-11-21",
            scriptPreview = """
                FUNCTION UPPERCASE "<USER>" -> VAR "UPPER_USER"
                PARSE "<UPPER_USER>" LR "" "" -> CAP "UpperUser"
                KEYCHECK
                  KEYCHAIN Success OR
                    KEY "@hit.com"
                  KEYCHAIN Custom OR
                    KEY "@custom.com"
                  KEYCHAIN Failure OR
                    KEY "@bad.com"
            """.trimIndent(),
            version = "1.2",
            description = "Orchestrator unit test",
            suggestedBots = 4,
            maxRetries = 2,
            timeoutSec = 5,
            maxProxyUses = 3
        )

        val proxies = listOf(
            com.example.model.ProxyItem("p1", "10.0.0.1", 8080, com.example.model.ProxyProtocol.HTTP, 20, "US", com.example.model.ProxyStatus.AVAILABLE),
            com.example.model.ProxyItem("p2", "10.0.0.2", 1080, com.example.model.ProxyProtocol.SOCKS5, 18, "DE", com.example.model.ProxyStatus.AVAILABLE)
        )
        val combos = listOf(
            "u1@hit.com:p1",
            "u2@bad.com:p2",
            "u3@custom.com:p3",
            "u4@hit.com:p4",
            "u5@hit.com:p5",
            "u6@bad.com:p6"
        )

        val orchestrator = com.example.network.JobOrchestrator(
            config = config,
            wordlistId = "wl-1",
            wordlistName = "test_combos.txt",
            lines = combos,
            startLine = 1,
            initialBotsCount = 3,
            initialProxyMode = com.example.model.ProxyMode.ON,
            allProxies = proxies
        )

        orchestrator.execute(shouldContinue = { true })
        val finalBatch = orchestrator.drainTelemetry(
            markRunning = false,
            fallbackStatusLog = "Completed"
        )

        assertEquals(6, finalBatch.currentDone)
        assertEquals(6, finalBatch.totalCount)
        assertEquals(3, finalBatch.hits)
        assertEquals(1, finalBatch.custom)
        assertEquals(2, finalBatch.bad)
        assertEquals(4, finalBatch.newHitsToPersist.size)
        assertTrue(finalBatch.newHitsToPersist.any { it.data == "u1@hit.com:p1" && it.capturedData.contains("Key=@hit.com") })
    }
}

