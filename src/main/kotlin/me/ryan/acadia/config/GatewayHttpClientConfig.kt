package me.ryan.acadia.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.JdkClientHttpRequestFactory
import java.net.http.HttpClient
import java.time.Duration

/**
 * Forces the gateway proxy HTTP client to plain HTTP/1.1.
 *
 * The JDK HttpClient defaults to HTTP/2, which on cleartext connections attempts an h2c upgrade
 * (Upgrade: h2c, HTTP2-Settings, Connection: Upgrade). Strict HTTP/1.1 backends (uvicorn/h11)
 * reject such requests when they carry a body, breaking POST/PUT/PATCH proxying. Pinning the
 * client to HTTP/1.1 removes the upgrade attempt.
 *
 * Spring Cloud Gateway Server WebMVC's `gatewayRestClientCustomizer` applies any
 * [ClientHttpRequestFactory] bean to the proxy RestClient, so providing this bean is sufficient.
 * Connect/read timeouts mirror `spring.cloud.gateway.server.webmvc.httpclient.*`.
 *
 * Note: the JDK client normalizes response header names to lowercase (as HTTP/2 mandates); this is
 * case-insensitive per the HTTP spec and safe for clients.
 */
@Configuration
class GatewayHttpClientConfig {
    @Bean
    fun proxyClientHttpRequestFactory(
        @Value("\${spring.cloud.gateway.server.webmvc.httpclient.connect-timeout:1s}") connectTimeout: Duration,
        @Value("\${spring.cloud.gateway.server.webmvc.httpclient.read-timeout:3s}") readTimeout: Duration,
    ): ClientHttpRequestFactory {
        val httpClient =
            HttpClient
                .newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build()
        return JdkClientHttpRequestFactory(httpClient).apply { setReadTimeout(readTimeout) }
    }
}
