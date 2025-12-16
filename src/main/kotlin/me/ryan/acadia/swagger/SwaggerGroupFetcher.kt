package me.ryan.acadia.swagger

import me.ryan.acadia.config.GatewayProperties.ServiceConfig
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Component
class SwaggerGroupFetcher(
    private val webClient: WebClient,
) {
    fun fetchGroups(service: ServiceConfig): Mono<List<String>> =
        webClient
            .get()
            .uri("${service.url}/v3/api-docs/swagger-config")
            .retrieve()
            .bodyToMono(SwaggerConfigResponse::class.java)
            .map { response ->
                response.urls.map { it.name }
            }.onErrorReturn(emptyList())

    private data class SwaggerConfigResponse(
        val urls: List<SwaggerUrl> = emptyList(),
    )

    private data class SwaggerUrl(
        val name: String = "",
        val url: String = "",
    )
}
