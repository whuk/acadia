package me.ryan.acadia.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.ryan.acadia.common.GatewayHeaders
import me.ryan.acadia.common.HeaderInjectingRequestWrapper
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(FilterOrders.TRACE_ID)
class TraceIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val traceId = generateTraceId()
        val wrapped = HeaderInjectingRequestWrapper(request, mapOf(GatewayHeaders.X_B3_TRACE_ID to traceId))
        filterChain.doFilter(wrapped, response)
    }

    private fun generateTraceId(): String =
        UUID
            .randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 16)
}
