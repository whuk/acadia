package me.ryan.acadia.swagger

import me.ryan.acadia.config.GatewayProperties.ServiceConfig
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class SwaggerGroupFetcher(
    private val restClient: RestClient,
) {
    fun fetchGroups(service: ServiceConfig): List<String> =
        try {
            val response =
                restClient
                    .get()
                    .uri("${service.url}/v3/api-docs/swagger-config")
                    .retrieve()
                    .body(SwaggerConfigResponse::class.java)
            response?.urls?.map { it.name } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

    private data class SwaggerConfigResponse(
        val urls: List<SwaggerUrl> = emptyList(),
    )

    private data class SwaggerUrl(
        val name: String = "",
        val url: String = "",
    )
}
