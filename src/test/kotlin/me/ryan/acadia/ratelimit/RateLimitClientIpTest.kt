package me.ryan.acadia.ratelimit

import me.ryan.acadia.config.RateLimitProperties
import me.ryan.acadia.filter.RateLimitFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class RateLimitClientIpTest {
    private fun record(
        filter: RateLimitFilter,
        remoteAddr: String,
        forwardedFor: String? = null,
    ) {
        val request =
            MockHttpServletRequest().apply {
                this.remoteAddr = remoteAddr
                if (forwardedFor != null) addHeader("X-Forwarded-For", forwardedFor)
            }
        filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())
    }

    @Test
    fun `신뢰 프록시 설정 시 X-Forwarded-For의 클라이언트 IP로 버킷을 식별한다`() {
        val filter = RateLimitFilter(RateLimitProperties(enabled = true, trustForwardedFor = true))

        // Same forwarded client IP behind two different proxy connections -> one bucket.
        record(filter, remoteAddr = "10.0.0.1", forwardedFor = "203.0.113.9")
        record(filter, remoteAddr = "10.0.0.2", forwardedFor = "203.0.113.9")

        assertThat(filter.trackedIpCount()).isEqualTo(1)
    }

    @Test
    fun `X-Forwarded-For가 없으면 remoteAddr로 폴백한다`() {
        val filter = RateLimitFilter(RateLimitProperties(enabled = true, trustForwardedFor = true))

        record(filter, remoteAddr = "10.0.0.1")
        record(filter, remoteAddr = "10.0.0.2")

        // No forwarded header -> each remote address is its own bucket.
        assertThat(filter.trackedIpCount()).isEqualTo(2)
    }

    @Test
    fun `프록시를 신뢰하지 않으면 X-Forwarded-For를 무시한다`() {
        val filter = RateLimitFilter(RateLimitProperties(enabled = true, trustForwardedFor = false))

        // Same spoofed forwarded client IP, but trust is off -> remoteAddr decides the bucket.
        record(filter, remoteAddr = "10.0.0.1", forwardedFor = "203.0.113.9")
        record(filter, remoteAddr = "10.0.0.2", forwardedFor = "203.0.113.9")

        assertThat(filter.trackedIpCount()).isEqualTo(2)
    }
}
