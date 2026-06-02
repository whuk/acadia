package me.ryan.acadia.common

import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RequestPathsTest {
    @Test
    fun `컨텍스트 패스를 제거한 게이트웨이 상대 경로를 반환한다`() {
        val request = mock<HttpServletRequest>()
        whenever(request.requestURI).thenReturn("/gateway/api/users/1")
        whenever(request.contextPath).thenReturn("/gateway")

        assertThat(RequestPaths.gatewayRelative(request)).isEqualTo("/api/users/1")
    }

    @Test
    fun `컨텍스트 패스가 없으면 requestURI를 그대로 반환한다`() {
        val request = mock<HttpServletRequest>()
        whenever(request.requestURI).thenReturn("/api/users/1")
        whenever(request.contextPath).thenReturn("")

        assertThat(RequestPaths.gatewayRelative(request)).isEqualTo("/api/users/1")
    }
}
