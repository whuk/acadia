package me.ryan.acadia.config

import me.ryan.acadia.common.GatewayPaths
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties
import org.springdoc.core.properties.SwaggerUiConfigProperties
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig(
    swaggerUiConfigProperties: SwaggerUiConfigProperties,
    gatewayProperties: GatewayProperties,
) {
    init {
        val urls =
            gatewayProperties.services
                .filter { it.swaggerEnabled }
                .map { service ->
                    AbstractSwaggerUiConfigProperties.SwaggerUrl().apply {
                        name = service.name
                        url = "${GatewayPaths.SWAGGER_DOCS}/${service.name}"
                    }
                }.toMutableSet()
        swaggerUiConfigProperties.urls = urls
    }
}
