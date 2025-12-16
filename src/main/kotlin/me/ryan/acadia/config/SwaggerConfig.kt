package me.ryan.acadia.config

import me.ryan.acadia.common.GatewayPaths
import me.ryan.acadia.swagger.SwaggerGroupFetcher
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties
import org.springdoc.core.properties.SwaggerUiConfigProperties
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig(
    swaggerUiConfigProperties: SwaggerUiConfigProperties,
    gatewayProperties: GatewayProperties,
    swaggerGroupFetcher: SwaggerGroupFetcher,
) {
    init {
        val urls = mutableSetOf<AbstractSwaggerUiConfigProperties.SwaggerUrl>()

        gatewayProperties.services
            .filter { it.swaggerEnabled }
            .forEach { service ->
                val dynamicGroups = swaggerGroupFetcher.fetchGroups(service).block() ?: emptyList()
                val groups = dynamicGroups.ifEmpty { service.swaggerGroups }
                if (groups.isNotEmpty()) {
                    groups.forEach { group ->
                        urls.add(
                            AbstractSwaggerUiConfigProperties.SwaggerUrl().apply {
                                name = "${service.name}/$group"
                                url = "${GatewayPaths.SWAGGER_DOCS}/${service.name}/$group"
                            },
                        )
                    }
                } else {
                    urls.add(
                        AbstractSwaggerUiConfigProperties.SwaggerUrl().apply {
                            name = service.name
                            url = "${GatewayPaths.SWAGGER_DOCS}/${service.name}"
                        },
                    )
                }
            }
        swaggerUiConfigProperties.urls = urls
    }
}
