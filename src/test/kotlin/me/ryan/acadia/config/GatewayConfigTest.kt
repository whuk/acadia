package me.ryan.acadia.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GatewayConfigTest {
    @Test
    fun `서비스가 구성되지 않아도 라우터를 예외 없이 생성한다`() {
        val config = GatewayConfig(GatewayProperties(services = emptyList()))

        val routes = config.gatewayRoutes()

        assertThat(routes).isNotNull
    }
}
