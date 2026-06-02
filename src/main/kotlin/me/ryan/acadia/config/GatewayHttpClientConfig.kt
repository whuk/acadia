package me.ryan.acadia.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.HttpClientSettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ClientHttpRequestFactory
import java.time.Duration

/**
 * Proxy downstream HTTP client (Apache HttpComponents).
 *
 * Two reasons over the default JDK HttpClient:
 *  1. HTTP/1.1: the JDK client negotiates HTTP/2 and on cleartext attempts an h2c upgrade
 *     (Upgrade: h2c, HTTP2-Settings), which strict HTTP/1.1 backends (uvicorn/h11) reject for
 *     bodied requests. HttpComponents speaks plain HTTP/1.1 and preserves response header casing.
 *  2. Streaming (SSE): the JDK read-timeout bounds the whole response, so a long-lived
 *     text/event-stream is cancelled at the timeout. HttpComponents applies the read-timeout as a
 *     per-read idle socket timeout, so a stream survives as long as data keeps flowing while a
 *     genuinely stalled backend still times out.
 *
 * Spring Cloud Gateway Server WebMVC's `gatewayRestClientCustomizer` applies any
 * [ClientHttpRequestFactory] bean to the proxy RestClient. Timeouts mirror
 * `spring.cloud.gateway.server.webmvc.httpclient.*`.
 */
@Configuration
class GatewayHttpClientConfig {
    @Bean
    fun proxyClientHttpRequestFactory(
        @Value("\${spring.cloud.gateway.server.webmvc.httpclient.connect-timeout:1s}") connectTimeout: Duration,
        @Value("\${spring.cloud.gateway.server.webmvc.httpclient.read-timeout:3s}") readTimeout: Duration,
    ): ClientHttpRequestFactory {
        val settings =
            HttpClientSettings
                .defaults()
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(readTimeout)
        return ClientHttpRequestFactoryBuilder.httpComponents().build(settings)
    }
}
