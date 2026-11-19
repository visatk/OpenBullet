package com.example.domain.usecase

import com.example.model.ProxyItem
import com.example.model.ProxyProtocol
import com.example.model.ProxyStatus
import java.util.UUID

class ParseProxiesUseCase {

    data class ProxyParseResult(
        val newProxies: List<ProxyItem>,
        val duplicateCount: Int,
        val invalidCount: Int
    )

    operator fun invoke(
        rawText: String,
        defaultProtocol: ProxyProtocol,
        existingProxies: List<ProxyItem> = emptyList()
    ): ProxyParseResult {
        val seenKeys = existingProxies
            .mapTo(HashSet(existingProxies.size + 32)) {
                keyFor(it.host, it.port, it.protocol)
            }

        val accepted = mutableListOf<ProxyItem>()
        var duplicates = 0
        var invalid = 0

        for (rawLine in rawText.lineSequence()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            val parsed = parseLine(trimmed, defaultProtocol)
            if (parsed == null) {
                invalid++
                continue
            }

            val key = keyFor(parsed.host, parsed.port, parsed.protocol)
            if (!seenKeys.add(key)) {
                duplicates++
                continue
            }
            accepted.add(parsed)
        }

        return ProxyParseResult(
            newProxies = accepted,
            duplicateCount = duplicates,
            invalidCount = invalid
        )
    }

    private fun parseLine(line: String, defaultProtocol: ProxyProtocol): ProxyItem? {
        var protocol = defaultProtocol
        var remainder = line

        val schemeMatch = SCHEME_REGEX.matchAt(remainder, 0)
        if (schemeMatch != null) {
            protocol = when (schemeMatch.groupValues[1].lowercase()) {
                "socks4" -> ProxyProtocol.SOCKS4
                "socks5" -> ProxyProtocol.SOCKS5
                else -> ProxyProtocol.HTTP
            }
            remainder = remainder.substring(schemeMatch.value.length)
        }

        // Strip optional auth prefix (user:pass@host:port)
        val atIdx = remainder.lastIndexOf('@')
        if (atIdx >= 0) {
            remainder = remainder.substring(atIdx + 1)
        }

        val host: String
        val port: Int?

        if (remainder.startsWith("[")) {
            val closeBracket = remainder.indexOf(']')
            if (closeBracket <= 1) return null
            host = remainder.substring(1, closeBracket).trim()
            val after = remainder.substring(closeBracket + 1).removePrefix(":")
            val portStr = after.substringBefore(':')
            port = portStr.trim().toIntOrNull()
        } else {
            val parts = remainder.split(":")
            if (parts.size < 2) return null
            host = parts[0].trim()
            port = parts[1].trim().toIntOrNull()
        }

        if (host.isEmpty() || host.any { it.isWhitespace() } || port == null || port !in 1..65535) {
            return null
        }

        return ProxyItem(
            id = UUID.randomUUID().toString(),
            host = host,
            port = port,
            protocol = protocol,
            pingMs = null,
            country = "Pending",
            status = ProxyStatus.UNTESTED
        )
    }

    private fun keyFor(host: String, port: Int, protocol: ProxyProtocol): String =
        "${host.lowercase()}:$port:${protocol.name}"

    companion object {
        private val SCHEME_REGEX = Regex("""^(https?|socks4|socks5)://""", RegexOption.IGNORE_CASE)
    }
}
