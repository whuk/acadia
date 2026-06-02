package me.ryan.acadia.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * SEC/REL-1c: backend 5xx is normalized to 502, while gateway-originated 502/503/504
 * (circuit open -> 503, timeout -> 504) pass through unchanged.
 */
class BackendErrorFilterTest {
    private fun statusAfterBackendSets(backendStatus: Int): Int {
        val filter = BackendErrorFilter()
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, res -> (res as HttpServletResponse).status = backendStatus }
        filter.doFilter(MockHttpServletRequest(), response, chain)
        return response.status
    }

    @Test
    fun `백엔드 500은 502로 변환된다`() {
        assertThat(statusAfterBackendSets(500)).isEqualTo(502)
    }

    @Test
    fun `게이트웨이 발생 503은 보존된다`() {
        assertThat(statusAfterBackendSets(503)).isEqualTo(503)
    }

    @Test
    fun `게이트웨이 발생 504는 보존된다`() {
        assertThat(statusAfterBackendSets(504)).isEqualTo(504)
    }
}
