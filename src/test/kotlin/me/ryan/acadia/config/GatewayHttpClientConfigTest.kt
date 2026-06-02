package me.ryan.acadia.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.JdkClientHttpRequestFactory
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URI
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * The gateway proxy must forward plain HTTP/1.1 to downstream backends. JDK HttpClient's default
 * HTTP/2 attempt adds h2c upgrade headers (Upgrade: h2c, HTTP2-Settings) that strict HTTP/1.1
 * servers (uvicorn/h11) reject for requests with a body. A raw TCP server captures exactly what
 * the factory sends.
 */
class GatewayHttpClientConfigTest {
    private lateinit var serverSocket: ServerSocket
    private val captured = AtomicReference("")

    @BeforeEach
    fun startRawServer() {
        serverSocket = ServerSocket(0)
        thread(isDaemon = true) {
            while (!serverSocket.isClosed) {
                val conn =
                    try {
                        serverSocket.accept()
                    } catch (e: Exception) {
                        break
                    }
                conn.soTimeout = 400
                val sb = StringBuilder()
                try {
                    val input = conn.getInputStream()
                    val buf = ByteArray(4096)
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        sb.append(String(buf, 0, n, Charsets.ISO_8859_1))
                    }
                } catch (e: SocketTimeoutException) {
                    // collected the request within the window
                } catch (e: Exception) {
                    // ignore
                }
                if (sb.isNotEmpty()) captured.set(sb.toString())
                runCatching {
                    conn.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    conn.close()
                }
            }
        }
    }

    @AfterEach
    fun stop() {
        serverSocket.close()
    }

    private fun postJson(factory: ClientHttpRequestFactory) {
        val request = factory.createRequest(URI("http://localhost:${serverSocket.localPort}/x"), HttpMethod.POST)
        request.headers.add("Content-Type", "application/json")
        request.body.write("""{"k":"v"}""".toByteArray())
        request.execute().use { it.statusCode }
    }

    @Test
    fun `기본 JDK HttpClient는 h2c 업그레이드 헤더를 보낸다 - 버그 메커니즘 재현`() {
        postJson(JdkClientHttpRequestFactory()) // default = HTTP/2

        val req = captured.get().lowercase()
        assertThat(req).contains("post /x")
        // The very behavior that breaks strict HTTP/1.1 backends.
        assertThat(req).contains("http2-settings")
    }

    @Test
    fun `게이트웨이 프록시 팩토리는 h2c 업그레이드 헤더를 보내지 않는다`() {
        val factory = GatewayHttpClientConfig().proxyClientHttpRequestFactory(Duration.ofSeconds(1), Duration.ofSeconds(3))

        postJson(factory)

        val req = captured.get().lowercase()
        assertThat(req).contains("post /x")
        assertThat(req).doesNotContain("upgrade:")
        assertThat(req).doesNotContain("http2-settings")
    }
}
