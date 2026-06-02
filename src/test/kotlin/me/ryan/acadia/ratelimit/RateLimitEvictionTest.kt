package me.ryan.acadia.ratelimit

import me.ryan.acadia.config.RateLimitProperties
import me.ryan.acadia.filter.RateLimitFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.concurrent.atomic.AtomicLong

class RateLimitEvictionTest {
    private val properties = RateLimitProperties(enabled = true, burst = 20, windowMs = 1000)

    private fun record(
        filter: RateLimitFilter,
        ip: String,
    ): Int {
        val request = MockHttpServletRequest().apply { remoteAddr = ip }
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, MockFilterChain())
        return response.status
    }

    @Test
    fun `sweep는 만료된 엔트리만 제거하고 활성 엔트리는 유지한다`() {
        val time = AtomicLong(0)
        val filter = RateLimitFilter(properties).apply { clock = { time.get() } }

        record(filter, "10.0.0.1") // windowStart = 0
        time.set(2000) // advance past the 1000ms window
        record(filter, "10.0.0.2") // windowStart = 2000

        filter.sweep()

        // Only the still-active entry survives; the stale one is evicted.
        assertThat(filter.trackedIpCount()).isEqualTo(1)
    }

    @Test
    fun `요청 처리 경로는 다른 IP의 엔트리를 스캔하거나 제거하지 않는다`() {
        val time = AtomicLong(0)
        val filter = RateLimitFilter(properties).apply { clock = { time.get() } }

        record(filter, "10.0.0.1") // windowStart = 0
        time.set(2000) // 10.0.0.1 is now stale, but no sweep is invoked
        record(filter, "10.0.0.2") // per-request path must not evict 10.0.0.1

        // Without an explicit sweep, the hot path leaves other entries untouched.
        assertThat(filter.trackedIpCount()).isEqualTo(2)
    }

    @Test
    fun `eviction 변경 후에도 버스트 제한과 윈도우 리셋이 유지된다`() {
        val time = AtomicLong(0)
        val filter =
            RateLimitFilter(RateLimitProperties(enabled = true, burst = 2, windowMs = 1000))
                .apply { clock = { time.get() } }

        assertThat(record(filter, "10.0.0.1")).isEqualTo(200) // count 1
        assertThat(record(filter, "10.0.0.1")).isEqualTo(200) // count 2
        assertThat(record(filter, "10.0.0.1")).isEqualTo(429) // exceeds burst

        time.set(2000) // window elapsed -> counter resets
        assertThat(record(filter, "10.0.0.1")).isEqualTo(200)
    }
}
