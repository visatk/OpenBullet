package com.example.network

import com.example.model.ProxyItem
import com.example.model.ProxyProtocol
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.nio.channels.SocketChannel
import javax.net.SocketFactory
import okio.ByteString.Companion.encodeUtf8

class ProxyTunnelException(
    message: String,
    val protocol: ProxyProtocol? = null,
    val proxyStatusCode: Int = 0,
    cause: Throwable? = null
) : IOException(message, cause)

/**
 * Low-level raw TCP connection proxying engine supporting:
 * - HTTP CONNECT raw TCP tunneling (with RFC 7617 Basic Proxy-Authorization)
 * - SOCKS4 & SOCKS4a raw TCP tunneling (with remote hostname resolution)
 * - SOCKS5 raw TCP tunneling (RFC 1928 + RFC 1929 Username/Password subnegotiation + remote DNS)
 * - Direct & proxied raw TCP payload exchange (`tcp://host:port`)
 */
object RawTcpProxyTunnel {

    private const val SOCKS4_VERSION: Int = 0x04
    private const val SOCKS5_VERSION: Int = 0x05
    private const val SOCKS_CMD_CONNECT: Int = 0x01

    private const val SOCKS4_REPLY_GRANTED: Int = 0x5A

    private const val SOCKS5_AUTH_NONE: Int = 0x00
    private const val SOCKS5_AUTH_USER_PASS: Int = 0x02
    private const val SOCKS5_AUTH_NO_ACCEPTABLE: Int = 0xFF
    private const val SOCKS5_USER_PASS_VERSION: Int = 0x01

    private const val SOCKS5_ATYP_IPV4: Int = 0x01
    private const val SOCKS5_ATYP_DOMAIN: Int = 0x03
    private const val SOCKS5_ATYP_IPV6: Int = 0x04

    private const val MAX_HTTP_CONNECT_HEADER_BYTES: Int = 16 * 1024
    private const val MAX_RAW_TCP_READ_BYTES: Int = 64 * 1024

    private val ipv4LiteralRegex = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    /**
     * Opens a connected raw TCP [Socket] to [targetHost]:[targetPort], either directly (if [proxy] is null)
     * or tunneled through [proxy] using HTTP CONNECT, SOCKS4/4a, or SOCKS5 protocol handshakes.
     */
    fun openConnection(
        targetHost: String,
        targetPort: Int,
        proxy: ProxyItem?,
        timeoutMs: Int
    ): Socket {
        val safeTimeoutMs = timeoutMs.coerceIn(1_000, 120_000)
        val socket = Socket()
        return try {
            socket.tcpNoDelay = true
            socket.soTimeout = safeTimeoutMs
            if (proxy == null || proxy.host.isBlank()) {
                socket.connect(InetSocketAddress(targetHost, targetPort), safeTimeoutMs)
            } else {
                establishTunnelOnSocket(
                    socket = socket,
                    proxy = proxy,
                    targetHost = targetHost,
                    targetPort = targetPort,
                    timeoutMs = safeTimeoutMs
                )
            }
            socket
        } catch (t: Throwable) {
            runCatching { socket.close() }
            throw t
        }
    }

    /**
     * Connects [socket] to [proxy] and completes the protocol-specific raw TCP tunnel handshake
     * to [targetHost]:[targetPort].
     */
    fun establishTunnelOnSocket(
        socket: Socket,
        proxy: ProxyItem,
        targetHost: String,
        targetPort: Int,
        timeoutMs: Int
    ) {
        require(targetHost.isNotBlank()) { "Target host must not be blank" }
        require(targetPort in 1..65535) { "Target port out of range: $targetPort" }
        val safeTimeoutMs = timeoutMs.coerceIn(1_000, 120_000)

        socket.tcpNoDelay = true
        socket.soTimeout = safeTimeoutMs
        if (!socket.isConnected) {
            socket.connect(InetSocketAddress(proxy.host.trim(), proxy.port), safeTimeoutMs)
        }

        when (proxy.protocol) {
            ProxyProtocol.HTTP -> performHttpConnectHandshake(
                socket = socket,
                proxy = proxy,
                targetHost = targetHost,
                targetPort = targetPort
            )
            ProxyProtocol.SOCKS4 -> performSocks4Handshake(
                socket = socket,
                proxy = proxy,
                targetHost = targetHost,
                targetPort = targetPort
            )
            ProxyProtocol.SOCKS5 -> performSocks5Handshake(
                socket = socket,
                proxy = proxy,
                targetHost = targetHost,
                targetPort = targetPort
            )
        }
    }

    /**
     * Executes a raw TCP payload exchange over a direct or proxied TCP tunnel (`tcp://host:port`).
     */
    fun executeRawTcpExchange(
        targetHost: String,
        targetPort: Int,
        payload: String,
        proxy: ProxyItem?,
        timeoutMs: Int
    ): RawTcpExchangeResult {
        val startMs = System.currentTimeMillis()
        openConnection(
            targetHost = targetHost,
            targetPort = targetPort,
            proxy = proxy,
            timeoutMs = timeoutMs
        ).use { socket ->
            val out = socket.getOutputStream()
            val input = socket.getInputStream()

            if (payload.isNotEmpty()) {
                val decodedPayload = unescapeTcpPayload(payload)
                out.write(decodedPayload.toByteArray(Charsets.UTF_8))
                out.flush()
            }

            val buffer = ByteArray(4096)
            val bos = ByteArrayOutputStream()
            while (bos.size() < MAX_RAW_TCP_READ_BYTES) {
                val toRead = minOf(buffer.size, MAX_RAW_TCP_READ_BYTES - bos.size())
                val read = try {
                    input.read(buffer, 0, toRead)
                } catch (_: java.net.SocketTimeoutException) {
                    // Break cleanly if some banner/response bytes were already received
                    if (bos.size() > 0) break else throw ProxyTunnelException(
                        message = "Raw TCP read timed out from $targetHost:$targetPort",
                        protocol = proxy?.protocol
                    )
                }
                if (read <= 0) break
                bos.write(buffer, 0, read)
                if (input.available() == 0) break
            }

            val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val responseText = bos.toByteArray().toString(Charsets.UTF_8)
            return RawTcpExchangeResult(
                connected = true,
                latencyMs = elapsedMs,
                bytesReceived = bos.size(),
                responseText = responseText
            )
        }
    }

    internal fun performHttpConnectHandshake(
        socket: Socket,
        proxy: ProxyItem,
        targetHost: String,
        targetPort: Int
    ) {
        val out = socket.getOutputStream()
        val input = socket.getInputStream()
        val authority = if (targetHost.contains(':') && !targetHost.startsWith("[")) {
            "[$targetHost]:$targetPort"
        } else {
            "$targetHost:$targetPort"
        }

        val request = buildString {
            append("CONNECT $authority HTTP/1.1\r\n")
            append("Host: $authority\r\n")
            append("Proxy-Connection: Keep-Alive\r\n")
            if (proxy.username.isNotBlank() || proxy.password.isNotBlank()) {
                val credentials = "${proxy.username}:${proxy.password}".encodeUtf8().base64()
                append("Proxy-Authorization: Basic $credentials\r\n")
            }
            append("\r\n")
        }

        out.write(request.toByteArray(Charsets.ISO_8859_1))
        out.flush()

        val headerBytes = readUntilDoubleCrlf(input, MAX_HTTP_CONNECT_HEADER_BYTES)
        val headerText = headerBytes.toString(Charsets.ISO_8859_1)
        val statusLine = headerText.lineSequence().firstOrNull()?.trim().orEmpty()
        if (!statusLine.startsWith("HTTP/1.", ignoreCase = true)) {
            throw ProxyTunnelException(
                message = "Invalid HTTP CONNECT response from proxy: '${statusLine.take(64)}'",
                protocol = ProxyProtocol.HTTP
            )
        }

        val parts = statusLine.split(' ', limit = 3)
        val statusCode = parts.getOrNull(1)?.toIntOrNull() ?: 0
        if (statusCode != 200) {
            throw ProxyTunnelException(
                message = "HTTP CONNECT tunnel failed with status $statusCode ($statusLine)",
                protocol = ProxyProtocol.HTTP,
                proxyStatusCode = statusCode
            )
        }
    }

    internal fun performSocks4Handshake(
        socket: Socket,
        proxy: ProxyItem,
        targetHost: String,
        targetPort: Int
    ) {
        val out = socket.getOutputStream()
        val input = socket.getInputStream()
        val trimmedHost = targetHost.trim().removeSurrounding("[", "]")
        val userIdBytes = proxy.username.toByteArray(Charsets.UTF_8)

        val isIpv4Literal = ipv4LiteralRegex.matches(trimmedHost)
        val ipv4Bytes: ByteArray
        val domainBytes: ByteArray?

        if (isIpv4Literal) {
            val resolved = InetAddress.getByName(trimmedHost)
            ipv4Bytes = resolved.address
            domainBytes = null
        } else {
            // SOCKS4a extension: 0.0.0.1 indicates domain name follows the null-terminated USERID
            ipv4Bytes = byteArrayOf(0x00, 0x00, 0x00, 0x01)
            domainBytes = trimmedHost.toByteArray(Charsets.UTF_8)
        }

        val packet = ByteArrayOutputStream(16 + userIdBytes.size + (domainBytes?.size ?: 0))
        packet.write(SOCKS4_VERSION)
        packet.write(SOCKS_CMD_CONNECT)
        packet.write((targetPort ushr 8) and 0xFF)
        packet.write(targetPort and 0xFF)
        packet.write(ipv4Bytes)
        packet.write(userIdBytes)
        packet.write(0x00)
        if (domainBytes != null) {
            packet.write(domainBytes)
            packet.write(0x00)
        }

        out.write(packet.toByteArray())
        out.flush()

        val reply = readExactBytes(input, 8, ProxyProtocol.SOCKS4)
        val replyVersion = reply[0].toInt() and 0xFF
        val replyCode = reply[1].toInt() and 0xFF
        if (replyVersion != 0x00 && replyVersion != SOCKS4_VERSION) {
            throw ProxyTunnelException(
                message = "Invalid SOCKS4 reply version: 0x${replyVersion.toString(16)}",
                protocol = ProxyProtocol.SOCKS4
            )
        }
        if (replyCode != SOCKS4_REPLY_GRANTED) {
            val reason = when (replyCode) {
                0x5B -> "Request rejected or failed"
                0x5C -> "Client identd unreachable"
                0x5D -> "UserID authentication mismatch"
                else -> "Unknown code 0x${replyCode.toString(16)}"
            }
            throw ProxyTunnelException(
                message = "SOCKS4 CONNECT failed: $reason",
                protocol = ProxyProtocol.SOCKS4,
                proxyStatusCode = replyCode
            )
        }
    }

    internal fun performSocks5Handshake(
        socket: Socket,
        proxy: ProxyItem,
        targetHost: String,
        targetPort: Int
    ) {
        val out = socket.getOutputStream()
        val input = socket.getInputStream()
        val hasAuth = proxy.username.isNotBlank() || proxy.password.isNotBlank()

        // 1. Greeting / Authentication method negotiation
        val greeting = if (hasAuth) {
            byteArrayOf(
                SOCKS5_VERSION.toByte(),
                0x02,
                SOCKS5_AUTH_NONE.toByte(),
                SOCKS5_AUTH_USER_PASS.toByte()
            )
        } else {
            byteArrayOf(
                SOCKS5_VERSION.toByte(),
                0x01,
                SOCKS5_AUTH_NONE.toByte()
            )
        }
        out.write(greeting)
        out.flush()

        val methodReply = readExactBytes(input, 2, ProxyProtocol.SOCKS5)
        val serverVersion = methodReply[0].toInt() and 0xFF
        val selectedMethod = methodReply[1].toInt() and 0xFF
        if (serverVersion != SOCKS5_VERSION) {
            throw ProxyTunnelException(
                message = "Invalid SOCKS5 server version: 0x${serverVersion.toString(16)}",
                protocol = ProxyProtocol.SOCKS5
            )
        }
        if (selectedMethod == SOCKS5_AUTH_NO_ACCEPTABLE) {
            throw ProxyTunnelException(
                message = "SOCKS5 server rejected all offered authentication methods",
                protocol = ProxyProtocol.SOCKS5,
                proxyStatusCode = 407
            )
        }

        // 2. RFC 1929 Username/Password Subnegotiation
        if (selectedMethod == SOCKS5_AUTH_USER_PASS) {
            val userBytes = proxy.username.toByteArray(Charsets.UTF_8)
            val passBytes = proxy.password.toByteArray(Charsets.UTF_8)
            if (userBytes.size > 255 || passBytes.size > 255) {
                throw ProxyTunnelException(
                    message = "SOCKS5 username/password exceeds 255 bytes",
                    protocol = ProxyProtocol.SOCKS5
                )
            }
            val authPacket = ByteArrayOutputStream(3 + userBytes.size + passBytes.size)
            authPacket.write(SOCKS5_USER_PASS_VERSION)
            authPacket.write(userBytes.size)
            authPacket.write(userBytes)
            authPacket.write(passBytes.size)
            authPacket.write(passBytes)
            out.write(authPacket.toByteArray())
            out.flush()

            val authReply = readExactBytes(input, 2, ProxyProtocol.SOCKS5)
            val authStatus = authReply[1].toInt() and 0xFF
            if (authStatus != 0x00) {
                throw ProxyTunnelException(
                    message = "SOCKS5 authentication failed (status=0x${authStatus.toString(16)})",
                    protocol = ProxyProtocol.SOCKS5,
                    proxyStatusCode = 407
                )
            }
        } else if (selectedMethod != SOCKS5_AUTH_NONE) {
            throw ProxyTunnelException(
                message = "Unsupported SOCKS5 auth method: 0x${selectedMethod.toString(16)}",
                protocol = ProxyProtocol.SOCKS5
            )
        }

        // 3. SOCKS5 CONNECT Request
        val trimmedHost = targetHost.trim().removeSurrounding("[", "]")
        val connectPacket = ByteArrayOutputStream(32)
        connectPacket.write(SOCKS5_VERSION)
        connectPacket.write(SOCKS_CMD_CONNECT)
        connectPacket.write(0x00) // RSV

        when {
            ipv4LiteralRegex.matches(trimmedHost) -> {
                val ipv4 = InetAddress.getByName(trimmedHost).address
                connectPacket.write(SOCKS5_ATYP_IPV4)
                connectPacket.write(ipv4)
            }
            trimmedHost.contains(':') -> {
                val ipv6 = (InetAddress.getByName(trimmedHost) as? Inet6Address)?.address
                    ?: throw ProxyTunnelException("Invalid IPv6 target host: $trimmedHost", ProxyProtocol.SOCKS5)
                connectPacket.write(SOCKS5_ATYP_IPV6)
                connectPacket.write(ipv6)
            }
            else -> {
                val domainBytes = trimmedHost.toByteArray(Charsets.UTF_8)
                if (domainBytes.isEmpty() || domainBytes.size > 255) {
                    throw ProxyTunnelException("Invalid SOCKS5 domain length: ${domainBytes.size}", ProxyProtocol.SOCKS5)
                }
                connectPacket.write(SOCKS5_ATYP_DOMAIN)
                connectPacket.write(domainBytes.size)
                connectPacket.write(domainBytes)
            }
        }
        connectPacket.write((targetPort ushr 8) and 0xFF)
        connectPacket.write(targetPort and 0xFF)

        out.write(connectPacket.toByteArray())
        out.flush()

        // 4. Read SOCKS5 Reply Header
        val replyHeader = readExactBytes(input, 4, ProxyProtocol.SOCKS5)
        val repVersion = replyHeader[0].toInt() and 0xFF
        val repCode = replyHeader[1].toInt() and 0xFF
        val atyp = replyHeader[3].toInt() and 0xFF

        if (repVersion != SOCKS5_VERSION) {
            throw ProxyTunnelException(
                message = "Invalid SOCKS5 CONNECT reply version: 0x${repVersion.toString(16)}",
                protocol = ProxyProtocol.SOCKS5
            )
        }

        // Consume BND.ADDR + BND.PORT before checking repCode so stream stays aligned
        val addrBytesToSkip = when (atyp) {
            SOCKS5_ATYP_IPV4 -> 4
            SOCKS5_ATYP_IPV6 -> 16
            SOCKS5_ATYP_DOMAIN -> {
                val lenByte = readExactBytes(input, 1, ProxyProtocol.SOCKS5)
                lenByte[0].toInt() and 0xFF
            }
            else -> throw ProxyTunnelException(
                message = "Unknown SOCKS5 address type: 0x${atyp.toString(16)}",
                protocol = ProxyProtocol.SOCKS5
            )
        }
        readExactBytes(input, addrBytesToSkip + 2, ProxyProtocol.SOCKS5)

        if (repCode != 0x00) {
            val reason = when (repCode) {
                0x01 -> "General SOCKS server failure"
                0x02 -> "Connection not allowed by ruleset"
                0x03 -> "Network unreachable"
                0x04 -> "Host unreachable"
                0x05 -> "Connection refused by destination"
                0x06 -> "TTL expired"
                0x07 -> "Command not supported"
                0x08 -> "Address type not supported"
                else -> "Error code 0x${repCode.toString(16)}"
            }
            throw ProxyTunnelException(
                message = "SOCKS5 CONNECT failed: $reason",
                protocol = ProxyProtocol.SOCKS5,
                proxyStatusCode = repCode
            )
        }
    }

    private fun readUntilDoubleCrlf(input: InputStream, maxBytes: Int): ByteArray {
        val bos = ByteArrayOutputStream(256)
        var state = 0
        while (bos.size() < maxBytes) {
            val b = input.read()
            if (b == -1) {
                throw EOFException("Unexpected EOF while reading HTTP CONNECT proxy headers")
            }
            bos.write(b)
            state = when {
                state == 0 && b == '\r'.code -> 1
                state == 1 && b == '\n'.code -> 2
                state == 2 && b == '\r'.code -> 3
                state == 3 && b == '\n'.code -> 4
                b == '\r'.code -> 1
                else -> 0
            }
            if (state == 4) {
                return bos.toByteArray()
            }
        }
        throw ProxyTunnelException("HTTP CONNECT response header exceeded $maxBytes bytes", ProxyProtocol.HTTP)
    }

    private fun readExactBytes(
        input: InputStream,
        length: Int,
        protocol: ProxyProtocol
    ): ByteArray {
        val out = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(out, offset, length - offset)
            if (read < 0) {
                throw EOFException("${protocol.label} proxy closed connection during handshake ($offset/$length bytes)")
            }
            offset += read
        }
        return out
    }

    private fun unescapeTcpPayload(raw: String): String {
        return raw
            .replace("\\r\\n", "\r\n")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }
}

data class RawTcpExchangeResult(
    val connected: Boolean,
    val latencyMs: Long,
    val bytesReceived: Int,
    val responseText: String
)

/**
 * Custom [Socket] that intercepts [connect] and establishes a raw TCP tunnel through [proxy]
 * (using SOCKS4/4a, SOCKS5 with RFC 1929 auth, or HTTP CONNECT) before handing the socket back
 * to OkHttp or TLS (`SSLSocketFactory`).
 */
class ProxiedTcpSocket(
    private val proxy: ProxyItem,
    private val defaultTimeoutMs: Int
) : Socket() {

    override fun connect(endpoint: SocketAddress?) {
        connect(endpoint, defaultTimeoutMs)
    }

    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        val inetEndpoint = endpoint as? InetSocketAddress
            ?: throw IllegalArgumentException("Unsupported SocketAddress type: $endpoint")
        val targetHost = inetEndpoint.hostString
            ?.takeIf { it.isNotBlank() }
            ?: inetEndpoint.address?.hostAddress
            ?: throw SocketException("Unable to determine target host from $inetEndpoint")
        val targetPort = inetEndpoint.port
        val effectiveTimeout = if (timeout > 0) timeout else defaultTimeoutMs

        val proxyEndpoint = InetSocketAddress(proxy.host.trim(), proxy.port)
        super.connect(proxyEndpoint, effectiveTimeout)

        RawTcpProxyTunnel.establishTunnelOnSocket(
            socket = this,
            proxy = proxy,
            targetHost = targetHost,
            targetPort = targetPort,
            timeoutMs = effectiveTimeout
        )
    }

    override fun getChannel(): SocketChannel? = null
}

/**
 * Custom [SocketFactory] routing all socket connections through [RawTcpProxyTunnel] for
 * SOCKS4/4a, SOCKS5 (with per-proxy credentials), or raw HTTP CONNECT tunneling.
 */
class ProxiedTcpSocketFactory(
    private val proxy: ProxyItem,
    private val connectTimeoutMs: Int = 10_000
) : SocketFactory() {

    override fun createSocket(): Socket =
        ProxiedTcpSocket(proxy = proxy, defaultTimeoutMs = connectTimeoutMs)

    override fun createSocket(host: String, port: Int): Socket =
        ProxiedTcpSocket(proxy = proxy, defaultTimeoutMs = connectTimeoutMs).apply {
            connect(InetSocketAddress.createUnresolved(host, port), connectTimeoutMs)
        }

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress?,
        localPort: Int
    ): Socket =
        ProxiedTcpSocket(proxy = proxy, defaultTimeoutMs = connectTimeoutMs).apply {
            if (localHost != null || localPort > 0) {
                bind(InetSocketAddress(localHost, localPort))
            }
            connect(InetSocketAddress.createUnresolved(host, port), connectTimeoutMs)
        }

    override fun createSocket(host: InetAddress, port: Int): Socket =
        ProxiedTcpSocket(proxy = proxy, defaultTimeoutMs = connectTimeoutMs).apply {
            connect(InetSocketAddress(host, port), connectTimeoutMs)
        }

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int
    ): Socket =
        ProxiedTcpSocket(proxy = proxy, defaultTimeoutMs = connectTimeoutMs).apply {
            if (localAddress != null || localPort > 0) {
                bind(InetSocketAddress(localAddress, localPort))
            }
            connect(InetSocketAddress(address, port), connectTimeoutMs)
        }
}
