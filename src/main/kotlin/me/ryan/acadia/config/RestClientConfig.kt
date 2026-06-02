package me.ryan.acadia.config

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.HttpClientSettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class RestClientConfig {
    // REL-3a: bound connect/read timeouts so a slow/unresponsive backend cannot
    // stall the gateway startup (SwaggerConfig fetches swagger groups synchronously).
    @Bean
    fun restClient(): RestClient {
        val settings =
            HttpClientSettings
                .defaults()
                .withConnectTimeout(CONNECT_TIMEOUT)
                .withReadTimeout(READ_TIMEOUT)
        val factory = ClientHttpRequestFactoryBuilder.detect().build(settings)
        return RestClient.builder().requestFactory(factory).build()
    }

    companion object {
        private val CONNECT_TIMEOUT = Duration.ofSeconds(1)
        private val READ_TIMEOUT = Duration.ofSeconds(2)
    }
}
